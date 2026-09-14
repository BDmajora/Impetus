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

// Batched light propagator replacing vanilla's recursive checkLightFor, one per World (from Phosphor); positions are recorded until something reads light, then the batch runs brightest-to-darkest, and a large batch is split by chunk across a pool (ScalableLux's idea) with the same result
public final class LightingEngine {
    // Starting capacity of each queue's dedup set, kept small since 34 queues x up to 4 engines means every doubling costs megabytes of empty table before a block is placed
    private static final int QUEUE_CAPACITY = 512;

    private final World world;

    // The thread that constructed the world, kept only to name the offender in lock()'s warning
    private final Thread ownerThread = Thread.currentThread();

    private final ReentrantLock lock = new ReentrantLock();

    // Positions handed to checkLightFor, one queue per light type
    private final DeduplicatedLongQueue[] scheduledUpdates = new DeduplicatedLongQueue[EnumSkyBlock.values().length];

    // The pass state for the calling thread; extra ones live in the scheduler for its workers
    private final LightPropagator inline;

    // Null when parallel passes are off for this world
    private final ParallelLightScheduler scheduler;
    private final int parallelMinPositions;
    private final int parallelMinChunks;

    private final int maxScheduledUpdates;
    private final boolean warnOnIllegalThreadAccess;

    private boolean updating;

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

    // Records that a position's light may be stale, resolved on next read; Fulgor's whole replacement for World.checkLightFor
    public void scheduleLightUpdate(EnumSkyBlock lightType, BlockPos pos) {
        lock();

        try {
            DeduplicatedLongQueue queue = this.scheduledUpdates[lightType.ordinal()];

            Fulgor.recordScheduled();

            if (!queue.enqueue(LightKey.encode(pos))) {
                Fulgor.recordDeduplicated();
                return;
            }

            // Deferral is a memory trade and a producer that never reads light (generator, world-edit) would grow this unbounded, so flush and give up batching for one pass; skipped while a pass is running since re-entry is invalid
            if (!this.updating && queue.size() >= this.maxScheduledUpdates) {
                processLightUpdatesForType(lightType);
            }
        } finally {
            this.lock.unlock();
        }
    }

    // Resolves everything pending, for both light types
    public void processLightUpdates() {
        processLightUpdatesForType(EnumSkyBlock.SKY);
        processLightUpdatesForType(EnumSkyBlock.BLOCK);
    }

    // Resolves everything pending for one light type
    public void processLightUpdatesForType(EnumSkyBlock lightType) {
        // The client reaches this from off-thread callers (chunk builders, sound engine, mod render hooks) that must not mutate the world; unlike the server there is a tick that will do the work shortly, so they are turned away
        if (this.world.isRemote && !isCallingFromMainThread()) {
            return;
        }

        DeduplicatedLongQueue queue = this.scheduledUpdates[lightType.ordinal()];

        // Cheap volatile read before the lock; every light read goes through here and almost none have anything to do
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

    // Guards the render-thread-only cache reads
    @SideOnly(Side.CLIENT)
    private boolean isCallingFromMainThread() {
        return Minecraft.getMinecraft().isCallingFromMinecraftThread();
    }

    // Contention means another mod is touching the world from the wrong thread; blocking stalls but beats the corruption of proceeding, so warn and block
    private void lock() {
        if (this.lock.tryLock()) {
            return;
        }

        if (this.warnOnIllegalThreadAccess) {
            Thread current = Thread.currentThread();

            // Impetus' own parallel workers read light legitimately and contend on purpose; only a foreign thread is worth a warning
            if (current != this.ownerThread && !ParallelWorkerThread.isCurrent()) {
                IllegalAccessException trace = new IllegalAccessException(String.format(
                        "World is owned by '%s' (ID: %s), but was accessed from thread '%s' (ID: %s)",
                        this.ownerThread.getName(), this.ownerThread.getId(), current.getName(), current.getId()));

                Fulgor.LOGGER.warn("Something (likely another mod) has attempted to modify the world's state from the "
                        + "wrong thread!\nThis is *bad practice* and can cause severe issues in your game. Fulgor has "
                        + "mitigated the violation, but it may introduce stalls.\nPlease report this to the offending "
                        + "mod with the stacktrace below. You can silence this warning by setting "
                        + "`warnOnIllegalThreadAccess` to `false` in config/impetus-fulgor.cfg.", trace);
            }
        }

        this.lock.lock();
    }

    // Runs one full pass; re-entry is a bug, so it throws rather than corrupting the queues. A batch big enough and spread over enough chunks goes to the scheduler, anything smaller runs on this thread
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
                // Too few chunks to spread; the queue was drained by the partition, so the positions go back through the scratch queue
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
