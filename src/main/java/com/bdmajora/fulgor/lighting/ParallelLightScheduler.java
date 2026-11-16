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

// Runs light updates of distant chunks in parallel and keeps nearby chunks apart.
final class ParallelLightScheduler {
    // Chunk distance below which two tasks never run together.
    private static final int EXCLUSION_DISTANCE = 4;

    // Number of the next worker thread.
    private static final AtomicInteger THREAD_ID = new AtomicInteger();
    // Lock of the pool creation.
    private static final Object POOL_LOCK = new Object();
    // Shared worker pool or null before first use.
    private static volatile ThreadPoolExecutor pool;

    // World the light is written to.
    private final World world;
    // Number of worker threads.
    private final int threads;
    // Deduplication state of new propagators.
    private final boolean deduplicate;
    // Capacity of the queues of new propagators.
    private final int queueCapacity;

    // Idle propagators for reuse.
    private final ArrayDeque<LightPropagator> spare = new ArrayDeque<>();

    // Creates a scheduler for a world.
    ParallelLightScheduler(World world, int threads, boolean deduplicate, int queueCapacity) {
        this.world = world;
        this.threads = threads;
        this.deduplicate = deduplicate;
        this.queueCapacity = queueCapacity;
    }

    // Returns the shared worker pool and creates it on first use.
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
            current.prestartAllCoreThreads();
            pool = current;
            Fulgor.LOGGER.info("Parallel light pool started with {} threads", threads);
            return current;
        }
    }

    // Drains a queue into key lists by chunk.
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
        queue.resetDeduplication();
        return buckets;
    }

    // Runs the chunk tasks on the workers and the calling thread and reports the light changes and rethrows the first failure.
    void run(EnumSkyBlock lightType, Long2ObjectOpenHashMap<LongArrayList> buckets, LightPropagator inline) {
        List<Task> pending = new ArrayList<>(buckets.size());
        for (Long2ObjectMap.Entry<LongArrayList> entry : buckets.long2ObjectEntrySet()) {
            long chunk = entry.getLongKey();
            pending.add(new Task((int) (chunk >> 32), (int) chunk, entry.getValue()));
        }
        pending.sort((a, b) -> Integer.compare(b.keys.size(), a.keys.size()));

        ThreadPoolExecutor executor = pool(this.threads);
        int width = executor.getCorePoolSize();
        List<Task> running = new ArrayList<>();
        List<LongArrayList> notifications = new ArrayList<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Object monitor = this;
        // Interrupt state that stops new tasks and is restored at the end
        boolean interrupted = false;

        while (true) {
            Task inlineTask = null;
            synchronized (monitor) {
                boolean done = false;
                while (true) {
                    if ((pending.isEmpty() || interrupted) && running.isEmpty()) {
                        done = true;
                        break;
                    }
                    Task next = interrupted ? null : firstRunnable(pending, running);
                    if (next == null) {
                        if (running.isEmpty()) {
                            done = true;
                            break;
                        }
                        // Waits for the running tasks even after an interrupt
                        try {
                            monitor.wait();
                        } catch (InterruptedException e) {
                            interrupted = true;
                        }
                        continue;
                    }
                    pending.remove(next);
                    running.add(next);
                    if (running.size() > width) {
                        inlineTask = next;
                        break;
                    }
                    executor.execute(() -> {
                        LightPropagator propagator = acquire();
                        // Clean state of the propagator that allows its reuse
                        boolean clean = false;
                        try {
                            propagator.run(lightType, propagator.fill(next.keys), true);
                            LongArrayList deferred = propagator.takeDeferredNotifications();
                            if (deferred != null) {
                                synchronized (monitor) {
                                    notifications.add(deferred);
                                }
                            }
                            clean = true;
                        } catch (Throwable throwable) {
                            failure.compareAndSet(null, throwable);
                        } finally {
                            if (clean) {
                                release(propagator);
                            }
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

        if (interrupted) {
            Thread.currentThread().interrupt();
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

    // Returns the first pending task far enough from every running task or null.
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

    // Returns an idle propagator or a new one.
    private LightPropagator acquire() {
        synchronized (this.spare) {
            LightPropagator propagator = this.spare.poll();
            if (propagator != null) {
                return propagator;
            }
        }
        return new LightPropagator(this.world, this.deduplicate, this.queueCapacity);
    }

    // Keeps a propagator for reuse while fewer than the thread count are idle.
    private void release(LightPropagator propagator) {
        synchronized (this.spare) {
            if (this.spare.size() < this.threads) {
                this.spare.push(propagator);
            }
        }
    }

    // Keys of one chunk to update.
    private static final class Task {
        // Chunk x of the task.
        final int chunkX;
        // Chunk z of the task.
        final int chunkZ;
        // Light keys of the chunk.
        final LongArrayList keys;

        // Creates a task.
        Task(int chunkX, int chunkZ, LongArrayList keys) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.keys = keys;
        }
    }
}
