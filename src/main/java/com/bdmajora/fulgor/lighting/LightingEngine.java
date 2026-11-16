package com.bdmajora.fulgor.lighting;

import com.bdmajora.extras.async.ParallelWorkerThread;
import com.bdmajora.fulgor.Fulgor;
import com.bdmajora.fulgor.FulgorConfig;
import com.bdmajora.fulgor.collections.DeduplicatedLongQueue;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.concurrent.locks.ReentrantLock;

// Light update engine of a world that batches positions and runs them inline or in parallel.
public final class LightingEngine {
    // Starting capacity of each queue.
    private static final int QUEUE_CAPACITY = 512;

    // World the light is written to.
    private final World world;

    // Thread that created the engine.
    private final Thread ownerThread = Thread.currentThread();

    // Lock of the queues.
    private final ReentrantLock lock = new ReentrantLock();

    // Scheduled positions by light type.
    private final DeduplicatedLongQueue[] scheduledUpdates = new DeduplicatedLongQueue[EnumSkyBlock.values().length];

    // Propagator of the calling thread.
    private final LightPropagator inline;

    // Parallel scheduler or null.
    private final ParallelLightScheduler scheduler;
    // Fewest positions that try the parallel path.
    private final int parallelMinPositions;
    // Fewest chunks that use the parallel path.
    private final int parallelMinChunks;

    // Queue size that forces a flush.
    private final int maxScheduledUpdates;
    // Warning state of access from foreign threads.
    private final boolean warnOnIllegalThreadAccess;

    // Processing state of the engine.
    private boolean updating;

    // Creates an engine with the configured queues and scheduler.
    public LightingEngine(World world) {
        this.world = world;

        FulgorConfig config = FulgorConfig.get();

        this.maxScheduledUpdates = config.maxScheduledUpdates;
        this.warnOnIllegalThreadAccess = config.warnOnIllegalThreadAccess;

        boolean deduplicate = config.deduplicateUpdates;
        DeduplicatedLongQueue.Pool pool = new DeduplicatedLongQueue.Pool();

        for (int i = 0; i < this.scheduledUpdates.length; i++) {
            this.scheduledUpdates[i] = new DeduplicatedLongQueue(pool, QUEUE_CAPACITY, deduplicate);
        }

        this.inline = new LightPropagator(world, deduplicate, QUEUE_CAPACITY);

        int threads = config.parallelLightThreads();
        this.scheduler = config.parallelLightUpdates && threads > 0
                ? new ParallelLightScheduler(world, threads, deduplicate, QUEUE_CAPACITY)
                : null;
        this.parallelMinPositions = config.parallelMinPositions;
        this.parallelMinChunks = config.parallelMinChunks;
    }

    // Queues a position and flushes its type when the queue is full.
    public void scheduleLightUpdate(EnumSkyBlock lightType, BlockPos pos) {
        lock();

        try {
            DeduplicatedLongQueue queue = this.scheduledUpdates[lightType.ordinal()];

            Fulgor.recordScheduled();

            if (!queue.enqueue(LightKey.encode(pos))) {
                Fulgor.recordDeduplicated();
                return;
            }

            if (!this.updating && queue.size() >= this.maxScheduledUpdates) {
                processLightUpdatesForType(lightType);
            }
        } finally {
            this.lock.unlock();
        }
    }

    // Processes the sky and block light queues.
    public void processLightUpdates() {
        processLightUpdatesForType(EnumSkyBlock.SKY);
        processLightUpdatesForType(EnumSkyBlock.BLOCK);
    }

    // Processes the queue of one light type from the main thread.
    public void processLightUpdatesForType(EnumSkyBlock lightType) {
        if (this.world.isRemote && !isCallingFromMainThread()) {
            return;
        }

        DeduplicatedLongQueue queue = this.scheduledUpdates[lightType.ordinal()];

        if (queue.isEmpty()) {
            return;
        }

        lock();

        try {
            processLightUpdatesForTypeInner(lightType, queue);
        } finally {
            this.lock.unlock();
        }
    }

    // Returns whether the client thread is calling.
    @SideOnly(Side.CLIENT)
    private boolean isCallingFromMainThread() {
        return Minecraft.getMinecraft().isCallingFromMinecraftThread();
    }

    // Takes the lock and warns about a foreign thread that has to wait.
    private void lock() {
        if (this.lock.tryLock()) {
            return;
        }

        if (this.warnOnIllegalThreadAccess) {
            Thread current = Thread.currentThread();

            if (current != this.ownerThread && !ParallelWorkerThread.isCurrent()) {
                IllegalAccessException trace = new IllegalAccessException(String.format(
                        "World is owned by '%s' (ID: %s), but was accessed from thread '%s' (ID: %s)",
                        this.ownerThread.getName(), this.ownerThread.threadId(), current.getName(), current.threadId()));

                Fulgor.LOGGER.warn("Something (likely another mod) has attempted to modify the world's state from the "
                        + "wrong thread!\nThis is *bad practice* and can cause severe issues in your game. Fulgor has "
                        + "mitigated the violation, but it may introduce stalls.\nPlease report this to the offending "
                        + "mod with the stacktrace below. You can silence this warning by setting "
                        + "`warnOnIllegalThreadAccess` to `false` in config/impetus-fulgor.cfg.", trace);
            }
        }

        this.lock.lock();
    }

    // Runs a queue in parallel when it spans enough chunks and inline otherwise and rejects reentry.
    private void processLightUpdatesForTypeInner(EnumSkyBlock lightType, DeduplicatedLongQueue queue) {
        if (this.updating) {
            throw new IllegalStateException("Already processing light updates");
        }

        this.updating = true;

        try {
            if (this.scheduler != null && queue.size() >= this.parallelMinPositions) {
                Long2ObjectOpenHashMap<LongArrayList> buckets = this.scheduler.partition(queue);
                if (buckets.size() >= this.parallelMinChunks) {
                    this.scheduler.run(lightType, buckets, this.inline);
                    return;
                }
                for (LongArrayList bucket : buckets.values()) {
                    this.inline.fill(bucket);
                }
                this.inline.run(lightType, this.inline.scratchQueue(), false);
                return;
            }
            this.inline.run(lightType, queue, false);
        } finally {
            this.updating = false;
        }
    }
}
