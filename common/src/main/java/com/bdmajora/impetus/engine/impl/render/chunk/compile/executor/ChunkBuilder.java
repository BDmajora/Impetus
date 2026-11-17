package com.bdmajora.impetus.engine.impl.render.chunk.compile.executor;

import com.bdmajora.impetus.engine.impl.common.util.MathUtil;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.ChunkBuildContext;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.ChunkBuildOutput;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.ChunkSortOutput;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.tasks.ChunkBuilderTask;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.GlobalChunkBuildContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ChunkBuilder {
    static final Logger LOGGER = LogManager.getLogger("ChunkBuilder");
    // Heap in megabytes each builder thread needs.
    private static final int MBS_PER_CHUNK_BUILDER = 64;

    // Queued tasks allowed per worker and the floor of the adaptive target.
    private static final int TASK_QUEUE_LIMIT_PER_WORKER = 2;

    // Whether the adaptive scheduler runs.
    private static final boolean ENABLE_ADAPTIVE_SCHEDULING = false;

    // Whether changes of the adaptive in flight target are logged.
    private static final boolean DEBUG_ADAPTIVE_SCHEDULING = false;

    // Cap on the in flight target per worker.
    private static final int MAX_TARGET_PER_WORKER = 64;

    // Headroom over the break even queue depth.
    private static final double SCHEDULING_HEADROOM = 1.5;

    // Smoothing factor of the frame time moving average.
    private static final double FRAME_TIME_EMA_ALPHA = 0.15;

    // Frame time assumed before any frame is seen.
    private static final long INITIAL_FRAME_NANOS = TimeUnit.MILLISECONDS.toNanos(50);

    // Longest frame interval fed to the average.
    private static final long MAX_FRAME_NANOS = TimeUnit.MILLISECONDS.toNanos(250);

    // Mesh task time assumed before any has completed.
    private static final double DEFAULT_MESH_TASK_NANOS = TimeUnit.MILLISECONDS.toNanos(2);

    // Sort task time assumed before any has completed.
    private static final double DEFAULT_SORT_TASK_NANOS = TimeUnit.MICROSECONDS.toNanos(500);

    // Bounds on how many sorts count as one mesh task when the leftover mesh budget becomes a sort budget.
    private static final double MIN_SORTS_PER_MESH = 1.0, MAX_SORTS_PER_MESH = 16.0;

    // Sort to mesh cost ratio used while adaptive scheduling is off.
    private static final double LEGACY_SORTS_PER_MESH = 4.0;

    // Sorts dispatched per frame regardless of the mesh budget.
    private static final int MIN_SORT_BUDGET = 4;

    // Priority given to each worker at creation.
    public static volatile int WORKER_PRIORITY = Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 2);

    private final ChunkJobQueue queue = new ChunkJobQueue();

    private final List<WorkerThread> threads = new ArrayList<>();

    private final AtomicInteger busyThreadCount = new AtomicInteger();

    // Target number of in flight mesh tasks.
    private int targetInFlight;

    // Sort tasks the workers finish in the time of one mesh task.
    private double sortsPerMesh;

    // Moving average of the interval between scheduling ticks.
    private double frameTimeEma = INITIAL_FRAME_NANOS;

    // Time of the previous scheduling tick or 0 before the first.
    private long lastTickNanos;

    private final ChunkBuildContext localContext;

    private final ManagedBlocker managedBlocker;

    public ChunkBuilder(ManagedBlocker managedBlocker, Supplier<ChunkBuildContext> contextSupplier, int requestedThreads) {
        GlobalChunkBuildContext.setMainThread();

        if (requestedThreads >= 0) {
            int count = getThreadCount(requestedThreads);

            for (int i = 0; i < count; i++) {
                ChunkBuildContext context = contextSupplier.get();
                WorkerRunnable worker = new WorkerRunnable(context);

                WorkerThread thread = new WorkerThread(worker, "Chunk Render Task Executor #" + i, context);
                thread.setPriority(MathUtil.clamp(WORKER_PRIORITY, Thread.MIN_PRIORITY, Thread.MAX_PRIORITY));
                thread.start();

                this.threads.add(thread);
            }
        }

        LOGGER.info("Started {} worker threads", this.threads.size());

        this.localContext = contextSupplier.get();

        this.managedBlocker = managedBlocker;

        // Seeds the target from the default task times.
        this.recomputeTargets(DEFAULT_MESH_TASK_NANOS, DEFAULT_SORT_TASK_NANOS);
    }

    // Returns the steady state floor of the in flight target.
    private int getSchedulingFloor() {
        return Math.max(1, this.threads.size()) * TASK_QUEUE_LIMIT_PER_WORKER;
    }

    // Advances the scheduler once per frame from the measured frame and task times.
    public void tickSchedulingBudget(ChunkJobMetricsTracker metrics) {
        long now = System.nanoTime();

        if (this.lastTickNanos != 0) {
            long frameNanos = Math.min(Math.max(0, now - this.lastTickNanos), MAX_FRAME_NANOS);
            this.frameTimeEma += FRAME_TIME_EMA_ALPHA * (frameNanos - this.frameTimeEma);
        }

        this.lastTickNanos = now;

        if (!ENABLE_ADAPTIVE_SCHEDULING) {
            // Keeps the target at the floor while adaptive scheduling is off.
            return;
        }

        int previousTarget = this.targetInFlight;

        this.recomputeTargets(
                metrics.getAverageExecutionNanos(ChunkBuildOutput.class, DEFAULT_MESH_TASK_NANOS),
                metrics.getAverageExecutionNanos(ChunkSortOutput.class, DEFAULT_SORT_TASK_NANOS));

        if (DEBUG_ADAPTIVE_SCHEDULING && this.targetInFlight != previousTarget) {
            LOGGER.info("Scheduling target {} -> {} (frame={}us, queued={}, sortsPerMesh={})",
                    previousTarget, this.targetInFlight, (long) (this.frameTimeEma / 1000), this.queue.size(),
                    String.format("%.1f", this.sortsPerMesh));
        }
    }

    private void recomputeTargets(double meshTaskNanos, double sortTaskNanos) {
        int floor = this.getSchedulingFloor();

        if (!ENABLE_ADAPTIVE_SCHEDULING || this.threads.isEmpty()) {
            // Keeps the target at the floor.
            this.targetInFlight = floor;
            this.sortsPerMesh = LEGACY_SORTS_PER_MESH;
            return;
        }

        // Guard against a degenerate average (a task that measured as instantaneous) blowing the target up to the cap.
        meshTaskNanos = Math.max(1.0, meshTaskNanos);
        sortTaskNanos = Math.max(1.0, sortTaskNanos);

        double perWorker = this.frameTimeEma / meshTaskNanos * SCHEDULING_HEADROOM;
        long target = (long) Math.ceil(perWorker * this.threads.size());
        long cap = (long) MAX_TARGET_PER_WORKER * this.threads.size();

        this.targetInFlight = (int) Math.max(floor, Math.min(target, cap));
        this.sortsPerMesh = Math.max(MIN_SORTS_PER_MESH, Math.min(meshTaskNanos / sortTaskNanos, MAX_SORTS_PER_MESH));
    }

    // Returns the number of tasks the builder would like queued.
    public int getTargetQueueSize() {
        return this.targetInFlight;
    }

    // Returns the in flight target minus the queued tasks.
    public int getSchedulingBudget() {
        return Math.max(0, this.targetInFlight - this.queue.size());
    }

    // Returns this frame's sort budget with a floor of MIN_SORT_BUDGET.
    public int getSortSchedulingBudget() {
        long budget = (long) Math.ceil(this.getSchedulingBudget() * this.sortsPerMesh);
        return (int) Math.max(MIN_SORT_BUDGET, Math.min(Integer.MAX_VALUE, budget));
    }

    // Stops and joins every worker and cancels all jobs and clears the queues.
    public void shutdown() {
        if (!this.queue.isRunning()) {
            throw new IllegalStateException("Worker threads are not running");
        }

        // Delete any queued tasks and resources attached to them
        var jobs = this.queue.shutdown();

        for (var job : jobs) {
            job.setCancelled();
        }

        this.shutdownThreads();
    }

    private void shutdownThreads() {
        LOGGER.info("Stopping worker threads");

        // Wait for every remaining thread to terminate
        for (WorkerThread thread : this.threads) {
            this.managedBlocker.managedBlock(() -> !thread.isAlive());
        }

        this.threads.clear();
    }

    public <TASK extends ChunkBuilderTask<OUTPUT>, OUTPUT> ChunkJobTyped<TASK, OUTPUT> scheduleTask(TASK task, boolean important, long priority,
                                                                                                    Consumer<@Nullable ChunkJobResult<OUTPUT>> consumer)
    {
        Objects.requireNonNull(task, "Task must be non-null");

        if (!this.queue.isRunning()) {
            throw new IllegalStateException("Executor is stopped");
        }

        var job = new ChunkJobTyped<>(task, consumer);

        this.queue.add(job, important ? ChunkJobQueue.IMPORTANT_PRIORITY : priority);

        return job;
    }

    // Returns the number of builder threads to use and at least one.
    private static int getOptimalThreadCount() {
        int desiredThreads = Math.max(getMaxThreadCount() / 3, getMaxThreadCount() - 6);
        if (desiredThreads < 1) {
            return 1;
        } else if (desiredThreads > 10) {
            return 10;
        } else {
            return desiredThreads;
        }
    }

    private static int getThreadCount(int requested) {
        return requested == 0 ? getOptimalThreadCount() : Math.min(requested, getMaxThreadCount());
    }

    public static int getMaxThreadCount() {
        int totalCores = Runtime.getRuntime().availableProcessors();
        long memoryMb = Runtime.getRuntime().maxMemory() / (1024L * 1024L);
        // always allow at least one builder regardless of heap size
        int maxBuilders = Math.max(1, (int)(memoryMb / MBS_PER_CHUNK_BUILDER));
        // Returns the smaller of the core count and the builders the heap permits.
        return Math.min(totalCores, maxBuilders);
    }

    public void tryStealTask(ChunkJob job) {
        if (!this.queue.stealJob(job)) {
            return;
        }

        executeJobWithLocalContext(job);
    }

    private void executeJobWithLocalContext(ChunkJob job) {
        var localContext = this.localContext;
        GlobalChunkBuildContext.bindMainThread(localContext);

        try {
            job.execute(localContext);
        } finally {
            GlobalChunkBuildContext.bindMainThread(null);
            localContext.cleanup();
        }
    }

    public void tick() {
        // Don't need to run jobs on the main thread if there are worker threads
        if (!this.threads.isEmpty()) {
            return;
        }

        while (!this.queue.isEmpty()) {
            var job = Objects.requireNonNull(this.queue.pollJob());
            executeJobWithLocalContext(job);
        }
    }

    public boolean isBuildQueueEmpty() {
        return this.queue.isEmpty();
    }

    public int getScheduledJobCount() {
        return this.queue.size();
    }

    public int getBusyThreadCount() {
        return this.busyThreadCount.get();
    }

    public int getTotalThreadCount() {
        return this.threads.size();
    }

    public void managedBlock(BooleanSupplier isDone) {
        this.managedBlocker.managedBlock(isDone);
    }

    public static final class WorkerThread extends Thread implements GlobalChunkBuildContext.Holder {
        private final ChunkBuildContext context;

        public WorkerThread(Runnable runnable, String name, ChunkBuildContext context) {
            super(runnable, name);
            this.context = context;
        }

        @Override
        public ChunkBuildContext impetus$getGlobalContext() {
            return context;
        }
    }

    private class WorkerRunnable implements Runnable {
        // One context per thread avoids keeping its caches coherent across cores.
        private final ChunkBuildContext context;

        public WorkerRunnable(ChunkBuildContext context) {
            this.context = context;
        }

        @Override
        public void run() {
            // Run until the chunk builder shuts down
            while (ChunkBuilder.this.queue.isRunning()) {
                ChunkJob job;

                try {
                    job = ChunkBuilder.this.queue.waitForNextJob();
                } catch (InterruptedException ignored) {
                    continue;
                }

                if (job == null) {
                    // might mean we are not running anymore... go around and check isRunning
                    continue;
                }

                ChunkBuilder.this.busyThreadCount.getAndIncrement();

                try {
                    job.execute(this.context);
                } finally {
                    this.context.cleanup();

                    ChunkBuilder.this.busyThreadCount.decrementAndGet();
                }
            }
        }
    }

    public interface ManagedBlocker {
        ManagedBlocker NONE = isDone -> {
            while (!isDone.getAsBoolean()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {
                }
            }
        };

        void managedBlock(BooleanSupplier isDone);
    }
}
