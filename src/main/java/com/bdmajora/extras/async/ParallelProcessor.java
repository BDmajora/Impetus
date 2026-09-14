package com.bdmajora.extras.async;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.ExtrasConfig;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.IProjectile;
import net.minecraft.entity.MultiPartEntityPart;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.effect.EntityWeatherEffect;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.entity.item.EntityEnderCrystal;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.entity.monster.EntityShulker;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.entity.projectile.EntityFishHook;
import net.minecraft.entity.projectile.EntityShulkerBullet;
import net.minecraft.util.ReportedException;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeModContainer;
import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.common.thread.SidedThreadGroups;
import net.minecraftforge.fml.relauncher.FMLLaunchHandler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

// Server-side entity ticking spread over a worker pool, after AxalotL's Async (itself from MCMT): entities are grouped by 4x4-chunk section so neighbours share a task, sized by a learned cost model, and the main thread ticks the entities that must stay serial while it waits
public final class ParallelProcessor {
    // Entities within the same 4x4 chunk square land in one task, keeping most entity-to-entity interaction on one thread
    private static final int SECTION_SHIFT = 2;

    // Read once at startup: the collections that make parallel ticking safe are swapped in when a world is constructed, so this cannot flip live
    public static volatile boolean INSTALLED;

    // Held around chunk generation in every world: the vanilla generator keeps per-instance noise buffers, but the biome layers' scratch arrays are one static pool
    public static final Object WORLDGEN_LOCK = new Object();

    // Live switches, applied whenever the Extras config loads or saves
    public static volatile boolean entities;
    public static volatile boolean randomTicks;
    public static volatile boolean spawning;
    public static volatile boolean moddedEntities;
    private static volatile int configuredThreads;

    public static final CostModel ENTITY_COST = new CostModel(25_000D);
    public static final CostModel RANDOM_TICK_COST = new CostModel(20_000D);
    public static final CostModel SPAWN_COST = new CostModel(100_000D);

    // Vanilla types that touch shared state their tick cannot own: falling blocks and shulkers write blocks, boats carry passengers across chunks, the dragon and its parts are one multi-body entity, projectiles hit whatever they cross
    private static final Class<?>[] BLOCKED_CLASSES = {
            EntityPlayer.class, IProjectile.class, EntityFireball.class, EntityShulkerBullet.class, EntityFishHook.class,
            EntityFallingBlock.class, EntityShulker.class, EntityBoat.class, EntityDragon.class, MultiPartEntityPart.class,
            EntityEnderCrystal.class, EntityWeatherEffect.class
    };

    private static final Map<Class<?>, Boolean> SYNC_BY_CLASS = new ConcurrentHashMap<>();
    private static final AtomicInteger THREAD_ID = new AtomicInteger();
    private static final Object POOL_LOCK = new Object();
    private static volatile ThreadPoolExecutor pool;
    private static volatile boolean shuttingDown;

    private ParallelProcessor() {
    }

    // Startup half of the config: sets the install flag the world-construction hooks read
    public static void install(ExtrasConfig.AsyncSettings settings) {
        INSTALLED = settings.enabled;
    }

    // Live half: sub-switches and thread count; the class cache is dropped since the sync rules may have changed
    public static void apply(ExtrasConfig.AsyncSettings settings) {
        boolean on = INSTALLED && settings.enabled;
        entities = on && settings.entities;
        randomTicks = on && settings.randomTicks;
        spawning = on && settings.spawning;
        moddedEntities = settings.moddedEntities;
        configuredThreads = settings.threads;
        SyncEntityRules.load(settings.synchronizedEntities);
        SYNC_BY_CLASS.clear();
        ThreadPoolExecutor current = pool;
        if (current != null) {
            resize(current, desiredThreads());
        }
    }

    // Whether this world's entity loop should collect into batches this tick
    public static boolean isEntityTickingActive(World world) {
        return entities && !shuttingDown && !world.isRemote && ((ParallelWorld) world).impetus$isParallel();
    }

    public static boolean isRandomTickingActive(World world) {
        return randomTicks && !shuttingDown && !world.isRemote && ((ParallelWorld) world).impetus$isParallel();
    }

    public static boolean isSpawningActive(World world) {
        return spawning && !shuttingDown && !world.isRemote && ((ParallelWorld) world).impetus$isParallel();
    }

    // True on a pool thread
    public static boolean isWorkerThread() {
        return ParallelWorkerThread.isCurrent();
    }

    // Auto sizing follows Async: leave headroom for the OS and, on the integrated server, for the render thread
    public static int desiredThreads() {
        int cores = Runtime.getRuntime().availableProcessors();
        int configured = configuredThreads;
        if (configured > 0) {
            return Math.max(1, Math.min(configured, cores));
        }
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
        int threads = (int) (cores / (windows ? 1.6D : 1.3D));
        if (FMLLaunchHandler.side().isClient()) {
            threads--;
        }
        return Math.max(1, threads);
    }

    // Upper bound for the thread slider
    public static int maxThreads() {
        return Math.max(1, Runtime.getRuntime().availableProcessors());
    }

    // Current pool width, or zero before the first batch
    public static int poolSize() {
        ThreadPoolExecutor current = pool;
        return current == null ? 0 : current.getCorePoolSize();
    }

    // Builds the pool on first use; threads inherit the server thread's context loader so mod classes resolve on them
    private static ThreadPoolExecutor ensurePool() {
        ThreadPoolExecutor current = pool;
        if (current != null && !current.isShutdown()) {
            return current;
        }
        synchronized (POOL_LOCK) {
            current = pool;
            if (current != null && !current.isShutdown()) {
                return current;
            }
            int threads = desiredThreads();
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            current = new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), task -> {
                // The server group, whichever thread grows the pool: mods ask FML for the effective side and it answers from the group
                ParallelWorkerThread thread = new ParallelWorkerThread(SidedThreadGroups.SERVER, task, "Impetus-Parallel-Tick-" + THREAD_ID.getAndIncrement());
                thread.setDaemon(true);
                thread.setPriority(Thread.NORM_PRIORITY - 1);
                thread.setContextClassLoader(loader);
                return thread;
            });
            current.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
            current.prestartAllCoreThreads();
            pool = current;
            shuttingDown = false;
            Extras.LOGGER.info("Parallel ticking pool started with {} threads", threads);
            return current;
        }
    }

    // Grows or shrinks in place; order matters since core may not exceed max
    private static void resize(ThreadPoolExecutor current, int threads) {
        if (current.getCorePoolSize() == threads) {
            return;
        }
        if (threads > current.getMaximumPoolSize()) {
            current.setMaximumPoolSize(threads);
            current.setCorePoolSize(threads);
        } else {
            current.setCorePoolSize(threads);
            current.setMaximumPoolSize(threads);
        }
        current.prestartAllCoreThreads();
    }

    // Server start: clears the stop flag a previous integrated server left behind, so its worlds can batch again
    public static void onServerStart() {
        shuttingDown = false;
    }

    // Server stop: lets in-flight tasks finish, then drops the pool so the next world starts a fresh one
    public static void shutdown() {
        shuttingDown = true;
        ThreadPoolExecutor current = pool;
        pool = null;
        if (current == null) {
            return;
        }
        current.shutdown();
        try {
            if (!current.awaitTermination(30L, TimeUnit.SECONDS)) {
                current.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            current.shutdownNow();
        }
        SYNC_BY_CLASS.clear();
    }

    // Entity classification

    // Whether this entity's tick must stay on the main thread; per-class answers are cached, the per-entity ones (portal transit) are not
    public static boolean shouldTickSynchronously(Entity entity) {
        if (shuttingDown || entity.world.isRemote) {
            return true;
        }
        if (((PortalAccessor) entity).impetus$isInPortal()) {
            return true;
        }
        Class<?> type = entity.getClass();
        Boolean cached = SYNC_BY_CLASS.get(type);
        if (cached != null) {
            return cached;
        }
        boolean sync = classify(entity, type);
        SYNC_BY_CLASS.put(type, sync);
        return sync;
    }

    private static boolean classify(Entity entity, Class<?> type) {
        for (Class<?> blocked : BLOCKED_CLASSES) {
            if (blocked.isAssignableFrom(type)) {
                return true;
            }
        }
        ResourceLocation key = EntityList.getKey(entity);
        // Unregistered entities are unknown quantities, and modded ones only join the pool by choice
        if (key == null) {
            return true;
        }
        if (!moddedEntities && !"minecraft".equals(key.getNamespace())) {
            return true;
        }
        return SyncEntityRules.matches(key);
    }

    // Entity batches

    // Ticks the collected entities: workers take the async list by section, the main thread takes the sync list and then helps drain the queue; a crash in any task surfaces on the main thread once the batch has settled
    public static void tickEntities(World world, List<Entity> async, List<Entity> sync) {
        ThreadPoolExecutor executor = ensurePool();
        if (executor == null || ENTITY_COST.shouldRunSequentially(async.size())) {
            for (Entity entity : async) {
                tickEntity(world, entity, null);
            }
            for (Entity entity : sync) {
                tickEntity(world, entity, null);
            }
            return;
        }

        Batch batch = new Batch();
        List<Runnable> work = buildSpatialWork(async, executor.getCorePoolSize(), entity -> tickEntity(world, entity, batch));
        batch.arm(work.size());
        submit(executor, batch, work);
        for (Entity entity : sync) {
            tickEntity(world, entity, batch);
        }
        finish(batch);
        batch.rethrow();
    }

    // One entity's tick with vanilla's crash handling; a failure either removes the entity (Forge's removeErroringEntities) or is parked on the batch for the main thread
    private static void tickEntity(World world, Entity entity, Batch batch) {
        if (entity.isDead) {
            return;
        }
        try {
            world.updateEntity(entity);
        } catch (Throwable throwable) {
            CrashReport report = CrashReport.makeCrashReport(throwable, "Ticking entity");
            CrashReportCategory category = report.makeCategory("Entity being ticked");
            entity.addEntityCrashInfo(category);
            if (ForgeModContainer.removeErroringEntities) {
                FMLLog.log.fatal("{}", report.getCompleteReport());
                world.removeEntity(entity);
                return;
            }
            ReportedException reported = new ReportedException(report);
            if (batch == null) {
                throw reported;
            }
            batch.fail(reported);
        }
    }

    private static long sectionKey(Entity entity) {
        int x = entity.chunkCoordX >> SECTION_SHIFT;
        int z = entity.chunkCoordZ >> SECTION_SHIFT;
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    // Groups by section, emits full sections as their own tasks and packs the small remainder in key order so adjacent sections still tend to share a task
    private static List<Runnable> buildSpatialWork(List<Entity> entities, int workers, Consumer<Entity> action) {
        Long2ObjectOpenHashMap<List<Entity>> bySection = new Long2ObjectOpenHashMap<>();
        for (Entity entity : entities) {
            long key = sectionKey(entity);
            List<Entity> section = bySection.get(key);
            if (section == null) {
                section = new ArrayList<>();
                bySection.put(key, section);
            }
            section.add(entity);
        }
        int maxPerTask = ENTITY_COST.chunkSize(entities.size(), workers);
        long[] keys = bySection.keySet().toLongArray();
        Arrays.sort(keys);

        List<Runnable> work = new ArrayList<>();
        for (long key : keys) {
            List<Entity> section = bySection.get(key);
            if (section.size() >= maxPerTask) {
                addSlices(work, section, maxPerTask, ENTITY_COST, action);
            }
        }
        List<Entity> pending = new ArrayList<>();
        for (long key : keys) {
            List<Entity> section = bySection.get(key);
            if (section.size() >= maxPerTask) {
                continue;
            }
            if (!pending.isEmpty() && pending.size() + section.size() > maxPerTask) {
                emit(work, pending, ENTITY_COST, action);
                pending = new ArrayList<>();
            }
            pending.addAll(section);
        }
        if (!pending.isEmpty()) {
            emit(work, pending, ENTITY_COST, action);
        }
        return work;
    }

    private static <T> void emit(List<Runnable> work, List<T> batch, CostModel cost, Consumer<T> action) {
        work.add(cost.wrap(batch.size(), () -> {
            for (T item : batch) {
                action.accept(item);
            }
        }));
    }

    private static <T> void addSlices(List<Runnable> work, List<T> items, int maxPerTask, CostModel cost, Consumer<T> action) {
        for (int i = 0; i < items.size(); i += maxPerTask) {
            emit(work, new ArrayList<>(items.subList(i, Math.min(i + maxPerTask, items.size()))), cost, action);
        }
    }

    // Generic batches

    // Runs an action over a list in parallel when the cost model says it is worth it; exceptions from tasks are logged, not propagated, since callers (random ticks, spawning) have no entity to blame
    public static <T> void forEachParallel(List<T> items, CostModel cost, Consumer<T> action) {
        if (items.isEmpty()) {
            return;
        }
        ThreadPoolExecutor executor = ensurePool();
        if (executor == null || cost.shouldRunSequentially(items.size())) {
            for (T item : items) {
                runLogged(item, action);
            }
            return;
        }
        List<Runnable> work = new ArrayList<>();
        addSlices(work, items, cost.chunkSize(items.size(), executor.getCorePoolSize()), cost, item -> runLogged(item, action));
        Batch batch = new Batch();
        batch.arm(work.size());
        submit(executor, batch, work);
        finish(batch);
    }

    private static <T> void runLogged(T item, Consumer<T> action) {
        try {
            action.accept(item);
        } catch (Throwable throwable) {
            Extras.LOGGER.error("Error in parallel task for {}", item, throwable);
        }
    }

    // Batch plumbing

    // Queues the work and wakes as many workers as there are tasks; workers and the main thread all pull from the same queue so nobody idles while tasks remain
    private static void submit(ThreadPoolExecutor executor, Batch batch, List<Runnable> work) {
        for (Runnable task : work) {
            batch.queue.add(() -> {
                try {
                    task.run();
                } catch (Throwable throwable) {
                    Extras.LOGGER.error("Error in parallel tick task", throwable);
                } finally {
                    batch.done.countDown();
                }
            });
        }
        int workers = Math.max(1, Math.min(executor.getCorePoolSize(), work.size()));
        for (int i = 0; i < workers; i++) {
            executor.execute(() -> drain(batch.queue));
        }
    }

    // The main thread empties what it can, then blocks until the last worker finishes its task; an interrupt is remembered rather than acted on, since returning early would leave workers writing into a world the tick has moved on from
    private static void finish(Batch batch) {
        drain(batch.queue);
        boolean interrupted = false;
        while (true) {
            try {
                batch.done.await();
                break;
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static void drain(Queue<Runnable> queue) {
        Runnable task;
        while ((task = queue.poll()) != null) {
            task.run();
        }
    }

    // One tick's worth of queued tasks plus the first failure any of them raised
    private static final class Batch {
        final Queue<Runnable> queue = new ConcurrentLinkedQueue<>();
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        volatile CountDownLatch done = new CountDownLatch(0);

        // Sized once the work list is known and before anything is queued
        void arm(int tasks) {
            this.done = new CountDownLatch(tasks);
        }

        void fail(Throwable throwable) {
            this.failure.compareAndSet(null, throwable);
        }

        // Rethrows on the main thread so the crash report reads like vanilla's
        void rethrow() {
            Throwable throwable = this.failure.get();
            if (throwable instanceof ReportedException) {
                throw (ReportedException) throwable;
            }
            if (throwable instanceof RuntimeException) {
                throw (RuntimeException) throwable;
            }
            if (throwable != null) {
                throw new RuntimeException(throwable);
            }
        }
    }
}
