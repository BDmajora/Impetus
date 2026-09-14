package com.bdmajora.fulgor.lighting;

import com.bdmajora.extras.async.ParallelWorkerThread;
import com.bdmajora.fulgor.Fulgor;
import com.bdmajora.fulgor.collections.DeduplicatedLongQueue;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.util.math.BlockPos.MutableBlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

// ScalableLux's idea applied to Fulgor: a batch of scheduled positions is split by chunk and the chunks are passed out to a pool, with any two whose 15-block reach could meet kept apart in time; each pass settles its own region to the same fixpoint the single pass would, so the result is unchanged and only the wall time shrinks
final class ParallelLightScheduler {
    // A pass touches at most 15 blocks from a scheduled position and reads one further, so chunks this far apart (Chebyshev) never see each other's writes; one chunk of margin on top of the proven three
    private static final int EXCLUSION_DISTANCE = 4;

    private static final AtomicInteger THREAD_ID = new AtomicInteger();
    private static final Object POOL_LOCK = new Object();
    private static volatile ThreadPoolExecutor pool;

    private final World world;
    private final int threads;
    private final boolean deduplicate;
    private final int queueCapacity;

    // Propagators handed to workers; grown on demand up to the pool width, since each holds 34 queues
    private final ArrayDeque<LightPropagator> spare = new ArrayDeque<>();

    ParallelLightScheduler(World world, int threads, boolean deduplicate, int queueCapacity) {
        this.world = world;
        this.threads = threads;
        this.deduplicate = deduplicate;
        this.queueCapacity = queueCapacity;
    }

    // One pool for every world of the session; sized once from the first engine that asks
    private static ThreadPoolExecutor pool(int threads) {
        ThreadPoolExecutor current = pool;
        if (current != null) {
            return current;
        }
        synchronized (POOL_LOCK) {
            current = pool;
            if (current != null) {
                return current;
            }
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            current = new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), task -> {
                ParallelWorkerThread thread = new ParallelWorkerThread(task, "Impetus-Fulgor-Light-" + THREAD_ID.getAndIncrement());
                thread.setDaemon(true);
                thread.setPriority(Thread.NORM_PRIORITY - 1);
                thread.setContextClassLoader(loader);
                return thread;
            });
            // The default abort policy stays: a rejected task must surface as an error rather than leave the caller waiting on a latch that never counts down
            current.prestartAllCoreThreads();
            pool = current;
            Fulgor.LOGGER.info("Parallel light pool started with {} threads", threads);
            return current;
        }
    }

    // Splits the queue by chunk; the queue is drained either way, and the caller runs the whole thing inline when too few chunks are involved for a hand-off to pay
    Long2ObjectOpenHashMap<LongArrayList> partition(DeduplicatedLongQueue queue) {
        Long2ObjectOpenHashMap<LongArrayList> buckets = new Long2ObjectOpenHashMap<>();
        while (!queue.isEmpty()) {
            long key = queue.dequeue();
            long chunk = (((long) LightKey.chunkX(key)) << 32) | (LightKey.chunkZ(key) & 0xFFFFFFFFL);
            LongArrayList bucket = buckets.get(chunk);
            if (bucket == null) {
                bucket = new LongArrayList();
                buckets.put(chunk, bucket);
            }
            bucket.add(key);
        }
        // The queue's dedup set still remembers everything just drained; cleared here as beginDraining would have, or nothing could be scheduled twice
        queue.resetDeduplication();
        return buckets;
    }

    // Runs the buckets across the pool and this thread, replaying the workers' render notifications on this thread once everything has settled
    void run(EnumSkyBlock lightType, Long2ObjectOpenHashMap<LongArrayList> buckets, LightPropagator inline) {
        List<Task> pending = new ArrayList<>(buckets.size());
        for (Long2ObjectMap.Entry<LongArrayList> entry : buckets.long2ObjectEntrySet()) {
            long chunk = entry.getLongKey();
            pending.add(new Task((int) (chunk >> 32), (int) chunk, entry.getValue()));
        }
        // Largest first, so the long passes start early and the small ones fill the gaps
        pending.sort((a, b) -> Integer.compare(b.keys.size(), a.keys.size()));

        ThreadPoolExecutor executor = pool(this.threads);
        int width = executor.getCorePoolSize();
        List<Task> running = new ArrayList<>();
        List<LongArrayList> notifications = new ArrayList<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Object monitor = this;

        while (true) {
            Task inlineTask = null;
            synchronized (monitor) {
                boolean done = false;
                while (true) {
                    if (pending.isEmpty() && running.isEmpty()) {
                        done = true;
                        break;
                    }
                    Task next = firstRunnable(pending, running);
                    if (next == null) {
                        if (running.isEmpty()) {
                            done = true;
                            break;
                        }
                        try {
                            monitor.wait();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            done = true;
                            break;
                        }
                        continue;
                    }
                    pending.remove(next);
                    running.add(next);
                    if (running.size() > width) {
                        // Every worker is busy but this chunk is free to go, so this thread takes it rather than idling
                        inlineTask = next;
                        break;
                    }
                    executor.execute(() -> {
                        LightPropagator propagator = acquire();
                        try {
                            propagator.run(lightType, propagator.fill(next.keys), true);
                            LongArrayList deferred = propagator.takeDeferredNotifications();
                            if (deferred != null) {
                                synchronized (monitor) {
                                    notifications.add(deferred);
                                }
                            }
                        } catch (Throwable throwable) {
                            failure.compareAndSet(null, throwable);
                        } finally {
                            release(propagator);
                            synchronized (monitor) {
                                running.remove(next);
                                monitor.notifyAll();
                            }
                        }
                    });
                }
                if (done) {
                    break;
                }
            }
            // Outside the monitor so finishing workers are not held up behind this pass
            try {
                inline.run(lightType, inline.fill(inlineTask.keys), false);
            } catch (Throwable throwable) {
                failure.compareAndSet(null, throwable);
            } finally {
                synchronized (monitor) {
                    running.remove(inlineTask);
                    monitor.notifyAll();
                }
            }
        }

        MutableBlockPos pos = new MutableBlockPos();
        for (LongArrayList batch : notifications) {
            for (int i = 0; i < batch.size(); i++) {
                this.world.notifyLightSet(LightKey.decode(pos, batch.getLong(i)));
            }
        }

        Throwable throwable = failure.get();
        if (throwable instanceof RuntimeException) {
            throw (RuntimeException) throwable;
        }
        if (throwable instanceof Error) {
            throw (Error) throwable;
        }
        if (throwable != null) {
            throw new RuntimeException(throwable);
        }
    }

    // The first pending chunk far enough from everything in flight
    private static Task firstRunnable(List<Task> pending, List<Task> running) {
        for (Task candidate : pending) {
            boolean clear = true;
            for (Task active : running) {
                if (Math.abs(candidate.chunkX - active.chunkX) < EXCLUSION_DISTANCE
                        && Math.abs(candidate.chunkZ - active.chunkZ) < EXCLUSION_DISTANCE) {
                    clear = false;
                    break;
                }
            }
            if (clear) {
                return candidate;
            }
        }
        return null;
    }

    private LightPropagator acquire() {
        synchronized (this.spare) {
            LightPropagator propagator = this.spare.poll();
            if (propagator != null) {
                return propagator;
            }
        }
        return new LightPropagator(this.world, this.deduplicate, this.queueCapacity);
    }

    private void release(LightPropagator propagator) {
        synchronized (this.spare) {
            if (this.spare.size() < this.threads) {
                this.spare.push(propagator);
            }
        }
    }

    // One chunk's scheduled positions
    private static final class Task {
        final int chunkX;
        final int chunkZ;
        final LongArrayList keys;

        Task(int chunkX, int chunkZ, LongArrayList keys) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.keys = keys;
        }
    }
}
