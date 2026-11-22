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

// Runs entity ticks and random ticks on worker threads.
public final class ParallelProcessor {
    // Shift from chunk coordinates to section coordinates.
    private static final int SECTION_SHIFT = 2;

    // Whether the feature was installed at startup.
    public static volatile boolean INSTALLED;

    // Lock that serialises world generation.
    public static final Object WORLDGEN_LOCK = new Object();

    // Whether entities tick in parallel.
    public static volatile boolean entities;
    // Whether random ticks run in parallel.
    public static volatile boolean randomTicks;
    // Whether entities of other mods tick in parallel.
    public static volatile boolean moddedEntities;
    // Configured worker count or zero for automatic.
    private static volatile int configuredThreads;

    // Cost model of entity ticks.
    public static final CostModel ENTITY_COST = new CostModel(25_000D);
    // Cost model of random ticks.
    public static final CostModel RANDOM_TICK_COST = new CostModel(20_000D);

    // Entity classes that always tick on the main thread.
    private static final Class<?>[] BLOCKED_CLASSES = {
            EntityPlayer.class, IProjectile.class, EntityFireball.class, EntityShulkerBullet.class, EntityFishHook.class,
            EntityFallingBlock.class, EntityShulker.class, EntityBoat.class, EntityDragon.class, MultiPartEntityPart.class,
            EntityEnderCrystal.class, EntityWeatherEffect.class
    };

    // Whether each entity class ticks on the main thread.
    private static final Map<Class<?>, Boolean> SYNC_BY_CLASS = new ConcurrentHashMap<>();
    // Counter for worker names.
    private static final AtomicInteger THREAD_ID = new AtomicInteger();
    // Lock that guards the pool.
    private static final Object POOL_LOCK = new Object();
    // Worker pool or null.
    private static volatile ThreadPoolExecutor pool;
    // Whether the pool is stopping.
    private static volatile boolean shuttingDown;

    // Prevents instantiation
    private ParallelProcessor() {
    }

    // Records whether the feature is enabled.
    public static void install(ExtrasConfig.AsyncSettings settings) {
        INSTALLED = settings.enabled;
    }

    // Applies the settings and resizes the pool.
    public static void apply(ExtrasConfig.AsyncSettings settings) {
        boolean on = INSTALLED && settings.enabled;
        entities = on && settings.entities;
        randomTicks = on && settings.randomTicks;
        moddedEntities = settings.moddedEntities;
        configuredThreads = settings.threads;
        SyncEntityRules.load(settings.synchronizedEntities);
        SYNC_BY_CLASS.clear();
        ThreadPoolExecutor current = pool;
        if (current != null) {
            resize(current, desiredThreads());
        }
    }

    // Returns whether the world ticks entities in parallel.
    public static boolean isEntityTickingActive(World world) {
        return entities && !shuttingDown && !world.isRemote && ((ParallelWorld) world).impetus$isParallel();
    }

    // Returns whether the world ticks random blocks in parallel.
    public static boolean isRandomTickingActive(World world) {
        return randomTicks && !shuttingDown && !world.isRemote && ((ParallelWorld) world).impetus$isParallel();
    }

    // Returns whether the current thread is a worker.
    public static boolean isWorkerThread() {
        return ParallelWorkerThread.isCurrent();
    }

    // Returns the worker count that the settings ask for.
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

    // Returns the number of processors.
    public static int maxThreads() {
        return Math.max(1, Runtime.getRuntime().availableProcessors());
    }

    // Returns the current worker count.
    public static int poolSize() {
        ThreadPoolExecutor current = pool;
        return current == null ? 0 : current.getCorePoolSize();
    }

    // Returns the pool and starts it when needed.
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

    // Changes the worker count of the pool.
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

    // Allows parallel work again.
    public static void onServerStart() {
        shuttingDown = false;
    }

    // Stops the pool and waits for its tasks.
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

    // Returns whether the entity must tick on the main thread.
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

    // Returns whether the class of the entity must tick on the main thread.
    private static boolean classify(Entity entity, Class<?> type) {
        for (Class<?> blocked : BLOCKED_CLASSES) {
            if (blocked.isAssignableFrom(type)) {
                return true;
            }
        }
        ResourceLocation key = EntityList.getKey(entity);
        if (key == null) {
            return true;
        }
        if (!moddedEntities && !"minecraft".equals(key.getNamespace())) {
            return true;
        }
        return SyncEntityRules.matches(key);
    }

    // Ticks the entities and spreads the parallel ones over the workers.
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

    // Ticks one entity and reports a crash.
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

    // Returns the packed section of the entity.
    private static long sectionKey(Entity entity) {
        int x = entity.chunkCoordX >> SECTION_SHIFT;
        int z = entity.chunkCoordZ >> SECTION_SHIFT;
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    // Returns tasks that group nearby entities.
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

    // Adds a task that runs the action on every item.
    private static <T> void emit(List<Runnable> work, List<T> batch, CostModel cost, Consumer<T> action) {
        work.add(cost.wrap(batch.size(), () -> {
            for (T item : batch) {
                action.accept(item);
            }
        }));
    }

    // Adds tasks that each run a slice of the items.
    private static <T> void addSlices(List<Runnable> work, List<T> items, int maxPerTask, CostModel cost, Consumer<T> action) {
        for (int i = 0; i < items.size(); i += maxPerTask) {
            emit(work, new ArrayList<>(items.subList(i, Math.min(i + maxPerTask, items.size()))), cost, action);
        }
    }

    // Runs the action on every item in parallel.
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

    // Runs the action and logs a failure.
    private static <T> void runLogged(T item, Consumer<T> action) {
        try {
            action.accept(item);
        } catch (Throwable throwable) {
            Extras.LOGGER.error("Error in parallel task for {}", item, throwable);
        }
    }

    // Queues the tasks and wakes the workers.
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

    // Helps with the queue and waits for every task.
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

    // Runs queued tasks until the queue is empty.
    private static void drain(Queue<Runnable> queue) {
        Runnable task;
        while ((task = queue.poll()) != null) {
            task.run();
        }
    }

    // Tasks of one parallel run.
    private static final class Batch {
        // Tasks that wait to run.
        final Queue<Runnable> queue = new ConcurrentLinkedQueue<>();
        // First failure of the run.
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        // Latch that counts finished tasks.
        volatile CountDownLatch done = new CountDownLatch(0);

        // Sets the number of tasks to wait for.
        void arm(int tasks) {
            this.done = new CountDownLatch(tasks);
        }

        // Stores the first failure.
        void fail(Throwable throwable) {
            this.failure.compareAndSet(null, throwable);
        }

        // Throws the stored failure.
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
