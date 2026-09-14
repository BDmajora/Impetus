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

// Client particle ticking spread over a small pool, after Harvey_Husky's AsyncParticles: the client thread is blocked while the batch runs, so the client world is quiescent and the only shared state particles reach is the spawn queue, which is made concurrent; the light each particle will render with is sampled here so the render pass does no lookups
public final class ParticleTicker {
    // Hardly worth a hand-off below this many particles in one layer
    private static final int MIN_PARALLEL = 256;
    private static final int MAX_THREADS = 8;

    // Live switches, applied whenever the Extras config loads or saves
    public static volatile boolean enabled;
    public static volatile boolean moddedParticles;
    public static volatile boolean lightCache;
    public static volatile boolean cullOffscreen;
    public static volatile boolean collisionCache;

    // Bumped once per updateEffects; a particle's cached light is only trusted when it was sampled this tick
    public static volatile int tickCounter;

    private static final Map<Class<?>, Boolean> MODDED_BY_CLASS = new ConcurrentHashMap<>();
    private static final AtomicInteger THREAD_ID = new AtomicInteger();
    private static volatile ThreadPoolExecutor pool;

    private ParticleTicker() {
    }

    public static void apply(ExtrasConfig.ParticleSettings settings) {
        enabled = settings.parallelTick;
        moddedParticles = settings.parallelModded;
        lightCache = settings.lightCache;
        cullOffscreen = settings.cullOffscreen;
        collisionCache = settings.collisionCache;
    }

    // Marks the start of a tick so stale light samples from a previous one are not reused
    public static void beginTick() {
        tickCounter++;
    }

    // Whether this layer should go through the pool at all
    public static boolean shouldParallelize(Queue<Particle> layer) {
        return enabled && layer.size() >= MIN_PARALLEL;
    }

    private static int desiredThreads() {
        int cores = Runtime.getRuntime().availableProcessors();
        return Math.max(1, Math.min(MAX_THREADS, cores / 2));
    }

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

    // Ticks one layer: modded particles stay on the client thread unless opted in, vanilla ones are sliced across the pool, and the dead are dropped afterwards the way vanilla's iterator would have
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
            executor.execute(() -> {
                Runnable task;
                while ((task = queue.poll()) != null) {
                    try {
                        task.run();
                    } finally {
                        done.countDown();
                    }
                }
            });
        }

        for (Particle particle : sync) {
            tickOne.accept(particle);
        }
        Runnable task;
        while ((task = queue.poll()) != null) {
            try {
                task.run();
            } finally {
                done.countDown();
            }
        }
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

    // Vanilla's particles all live under net.minecraft; anything else was written without this pool in mind
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
