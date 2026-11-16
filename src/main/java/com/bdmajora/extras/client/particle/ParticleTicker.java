package com.bdmajora.extras.client.particle;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.ExtrasConfig;
import net.minecraft.client.particle.Particle;
import net.minecraft.util.ReportedException;

import java.util.ArrayList;
import java.util.List;
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

// Ticks particles on worker threads.
public final class ParticleTicker {
    // Smallest layer that is ticked in parallel.
    private static final int MIN_PARALLEL = 256;
    // Largest number of workers.
    private static final int MAX_THREADS = 8;

    // Whether particles tick in parallel.
    public static volatile boolean enabled;
    // Whether modded particles tick in parallel.
    public static volatile boolean moddedParticles;
    // Whether particles cache their light.
    public static volatile boolean lightCache;
    // Whether particles outside the view are skipped.
    public static volatile boolean cullOffscreen;
    // Whether particles cache collisions.
    public static volatile boolean collisionCache;

    // Number of ticks started.
    public static volatile int tickCounter;

    // Whether each particle class comes from a mod.
    private static final Map<Class<?>, Boolean> MODDED_BY_CLASS = new ConcurrentHashMap<>();
    // Counter for worker names.
    private static final AtomicInteger THREAD_ID = new AtomicInteger();
    // Worker pool or null.
    private static volatile ThreadPoolExecutor pool;

    // Prevents instantiation
    private ParticleTicker() {
    }

    // Applies the settings.
    public static void apply(ExtrasConfig.ParticleSettings settings) {
        enabled = settings.parallelTick;
        moddedParticles = settings.parallelModded;
        lightCache = settings.lightCache;
        cullOffscreen = settings.cullOffscreen;
        collisionCache = settings.collisionCache;
    }

    // Counts a new tick.
    public static void beginTick() {
        tickCounter++;
    }

    // Returns whether the layer is large enough to tick in parallel.
    public static boolean shouldParallelize(Queue<Particle> layer) {
        return enabled && layer.size() >= MIN_PARALLEL;
    }

    // Returns the worker count.
    private static int desiredThreads() {
        int cores = Runtime.getRuntime().availableProcessors();
        return Math.max(1, Math.min(MAX_THREADS, cores / 2));
    }

    // Returns the pool and starts it when needed.
    private static ThreadPoolExecutor ensurePool() {
        ThreadPoolExecutor current = pool;
        if (current != null) {
            return current;
        }
        synchronized (ParticleTicker.class) {
            current = pool;
            if (current != null) {
                return current;
            }
            int threads = desiredThreads();
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            current = new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), task -> {
                Thread thread = new Thread(task, "Impetus-Particle-Tick-" + THREAD_ID.getAndIncrement());
                thread.setDaemon(true);
                thread.setPriority(Thread.NORM_PRIORITY - 1);
                thread.setContextClassLoader(loader);
                return thread;
            });
            current.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
            current.prestartAllCoreThreads();
            pool = current;
            Extras.LOGGER.info("Parallel particle pool started with {} threads", threads);
            return current;
        }
    }

    // Ticks every particle of the layer and removes the dead ones.
    public static void tickLayer(Queue<Particle> layer, Consumer<Particle> tickOne) {
        Particle[] all = layer.toArray(new Particle[0]);
        List<Particle> sync = new ArrayList<>();
        List<Particle> async = new ArrayList<>(all.length);
        boolean modded = moddedParticles;
        for (Particle particle : all) {
            if (!modded && isModded(particle)) {
                sync.add(particle);
            } else {
                async.add(particle);
            }
        }

        ThreadPoolExecutor executor = ensurePool();
        int workers = executor.getCorePoolSize();
        int slice = Math.max(64, (async.size() + workers) / (workers + 1));
        Queue<Runnable> queue = new ConcurrentLinkedQueue<>();
        int tasks = 0;
        AtomicReference<Throwable> failure = new AtomicReference<>();
        for (int i = 0; i < async.size(); i += slice) {
            List<Particle> part = async.subList(i, Math.min(i + slice, async.size()));
            queue.add(() -> {
                for (Particle particle : part) {
                    try {
                        tickOne.accept(particle);
                    } catch (Throwable throwable) {
                        failure.compareAndSet(null, throwable);
                    }
                }
            });
            tasks++;
        }
        CountDownLatch done = new CountDownLatch(tasks);
        for (int i = 0; i < Math.min(workers, tasks); i++) {
            executor.execute(() -> drain(queue, done));
        }

        for (Particle particle : sync) {
            tickOne.accept(particle);
        }
        drain(queue, done);
        boolean interrupted = false;
        while (done.getCount() > 0L) {
            try {
                if (done.await(200L, TimeUnit.MICROSECONDS)) {
                    break;
                }
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }

        layer.removeIf(particle -> !particle.isAlive());

        Throwable throwable = failure.get();
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

    // Runs queued tasks until the queue is empty.
    private static void drain(Queue<Runnable> queue, CountDownLatch done) {
        Runnable task;
        while ((task = queue.poll()) != null) {
            try {
                task.run();
            } finally {
                done.countDown();
            }
        }
    }

    // Returns whether the particle comes from a mod.
    public static boolean isModded(Particle particle) {
        Class<?> type = particle.getClass();
        Boolean cached = MODDED_BY_CLASS.get(type);
        if (cached != null) {
            return cached;
        }
        boolean modded = !type.getName().startsWith("net.minecraft.");
        MODDED_BY_CLASS.put(type, modded);
        return modded;
    }
}
