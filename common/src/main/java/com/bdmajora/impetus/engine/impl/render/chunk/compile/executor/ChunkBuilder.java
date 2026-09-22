package com.bdmajora.impetus.engine.impl.render.chunk.compile.executor;

import com.bdmajora.impetus.engine.impl.common.util.MathUtil;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.ChunkBuildContext;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.tasks.ChunkBuilderTask;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.GlobalChunkBuildContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ChunkBuilder {
    static final Logger LOGGER = LogManager.getLogger("ChunkBuilder");

    // Priority given to each worker at creation; two below normal keeps them from competing with the render thread, and the Extras thread-scheduling page can move it
    public static volatile int WORKER_PRIORITY = Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 2);
    // Megabytes of heap required per builder thread, used to cap the worker count on a small heap
    private static final int MBS_PER_CHUNK_BUILDER = 64;

    // Tasks allowed in the queue per worker: small enough to stay responsive to camera movement, large enough to keep threads busy (2 is what Sodium 0.2 used); the adaptive floor
    private static final int TASK_QUEUE_LIMIT_PER_WORKER = 2;

    // Initial in-flight estimate at builder start; the adaptive controller adjusts from here based on actual throughput
    private static final int WARM_START_PER_WORKER = 32;

    // Damping for the target's decay when workers cannot keep up: it closes 1/N of the gap to the floor per frame, settling over ~1-3 frames instead of snapping
    private static final int TARGET_DECAY_DAMPING = 2;

    // Enables the adaptive scheduling controller; when off the in-flight target stays pinned at the floor (legacy fixed TASK_QUEUE_LIMIT_PER_WORKER budget)
    private static final boolean ENABLE_ADAPTIVE_SCHEDULING = true;

    private final ChunkJobQueue queue = new ChunkJobQueue();

    private final List<WorkerThread> threads = new ArrayList<>();

    private final AtomicInteger busyThreadCount = new AtomicInteger();

    // Target in-flight task count, adapted each frame by tickSchedulingBudget() to keep workers saturated regardless of frame rate; bounded below by the floor, self-limiting above
    private int targetInFlight;

    // Whether the previous frame stopped submitting because it hit the budget while work remained; the target only grows when true
    private boolean lastDispatchBudgetLimited;

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

        this.localContext = contextSupplier.get();

        this.managedBlocker = managedBlocker;

        if (ENABLE_ADAPTIVE_SCHEDULING && !this.threads.isEmpty()) {
            // A fresh builder always starts with a full initial-build backlog (world join, dimension/render-distance change, reload), so start from an estimate instead of ramping up
            this.targetInFlight = Math.max(this.getSchedulingFloor(), WARM_START_PER_WORKER * this.threads.size());
        } else {
            // When threading or adaptive scheduling are disabled, the target should always be the smallest queue size.
            this.targetInFlight = this.getSchedulingFloor();
        }
    }

    // Steady-state floor for the in-flight target: the small queue depth that keeps workers fed at high frame rates while preserving the freshest camera ordering
    private int getSchedulingFloor() {
        return Math.max(1, this.threads.size()) * TASK_QUEUE_LIMIT_PER_WORKER;
    }

    // Advances the controller one frame (call exactly once, before dispatch reads getSchedulingBudget()): double the target if a worker starved while dispatch was budget-limited, decay toward the floor on comfortable slack, else leave it
    public void tickSchedulingBudget() {
        if (!ENABLE_ADAPTIVE_SCHEDULING) {
            // Legacy behaviour: the target stays pinned at the floor, so getSchedulingBudget() yields the fixed per-worker budget
            return;
        }

        int floor = this.getSchedulingFloor();
        int queued = this.queue.size();
        boolean starved = this.queue.checkAndClearWorkerBlocked();

        if (starved && this.lastDispatchBudgetLimited) {
            // Workers ran dry while we were sitting on dispatchable work: grow aggressively to escape starvation.
            this.targetInFlight = (int) Math.min(Integer.MAX_VALUE, (long) this.targetInFlight * 2);
        } else if (queued > floor) {
            // Over-provisioned: close only 1/TARGET_DECAY_DAMPING (at least 1) of the gap per frame so corrections settle over a few frames and track fluctuating consumption smoothly
            int gap = queued - floor;
            int decayStep = Math.max(1, gap / TARGET_DECAY_DAMPING);
            this.targetInFlight = this.targetInFlight - decayStep;
        }

        // Keep the target at or above the floor (the decay may have stepped it below).
        this.targetInFlight = Math.max(floor, this.targetInFlight);
    }

    // the current ideal number of tasks the chunk builder would like in the queue
    public int getTargetQueueSize() {
        return this.targetInFlight;
    }

    // Build tasks still to schedule this frame: the in-flight target minus tasks already queued; a pure read, safe to call repeatedly
    public int getSchedulingBudget() {
        return Math.max(0, this.targetInFlight - this.queue.size());
    }

    // Records whether the last dispatch was limited by budget rather than by lack of work; consumed by the next tickSchedulingBudget()
    public void setDispatchBudgetLimited(boolean budgetLimited) {
        this.lastDispatchBudgetLimited = budgetLimited;
    }

    // Stops all workers and blocks until they terminate, then cancels every task and clears the queues; no-op if already stopped, and jobs that finished meanwhile still have their results processed for cleanup
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

    // Interrupts and joins every worker
    private void shutdownThreads() {
        // Wait for every remaining thread to terminate
        for (WorkerThread thread : this.threads) {
            this.managedBlocker.managedBlock(() -> !thread.isAlive());
        }

        this.threads.clear();
    }

    public <TASK extends ChunkBuilderTask<OUTPUT>, OUTPUT> ChunkJobTyped<TASK, OUTPUT> scheduleTask(TASK task, boolean important,
                                                                                                    Consumer<ChunkJobResult<OUTPUT>> consumer)
    {
        Objects.requireNonNull(task, "Task must be non-null");

        if (!this.queue.isRunning()) {
            throw new IllegalStateException("Executor is stopped");
        }

        var job = new ChunkJobTyped<>(task, consumer);

        this.queue.add(job, important);

        return job;
    }

    // the "optimal" number of threads to use for chunk build tasks; always at least one
    private static int getOptimalThreadCount() {
        int maxThreads = getMaxThreadCount();
        return MathUtil.clamp(Math.max(maxThreads / 3, maxThreads - 6), 1, 10);
    }

    // Requested count, or a heuristic from the core count when zero
    private static int getThreadCount(int requested) {
        return requested == 0 ? getOptimalThreadCount() : Math.min(requested, getMaxThreadCount());
    }

    // Upper bound the options screen offers
    public static int getMaxThreadCount() {
        int totalCores = Runtime.getRuntime().availableProcessors();
        long memoryMb = Runtime.getRuntime().maxMemory() / (1024L * 1024L);
        // always allow at least one builder regardless of heap size
        int maxBuilders = Math.max(1, (int)(memoryMb / MBS_PER_CHUNK_BUILDER));
        // choose the total CPU cores or the number of builders the heap permits, whichever is smaller
        return Math.min(totalCores, maxBuilders);
    }

    // Runs a queued job on the calling thread, for important rebuilds the main thread wants now
    public void tryStealTask(ChunkJob job) {
        if (!this.queue.stealJob(job)) {
            return;
        }

        executeJobWithLocalContext(job);
    }

    // Runs a job on the main thread's own build context
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

    // Per-frame bookkeeping
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

    // Nothing waiting
    public boolean isBuildQueueEmpty() {
        return this.queue.isEmpty();
    }

    // Waiting jobs
    public int getScheduledJobCount() {
        return this.queue.size();
    }

    // Workers currently running a job
    public int getBusyThreadCount() {
        return this.busyThreadCount.get();
    }

    // Worker count
    public int getTotalThreadCount() {
        return this.threads.size();
    }

    // Waits for a condition by stealing jobs rather than sleeping, so the wait is productive
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
        // Thread-local for a small performance win: avoids synchronizing caches between CPU cores
        private final ChunkBuildContext context;

        public WorkerRunnable(ChunkBuildContext context) {
            this.context = context;
        }

        // Worker loop: wait for a job, run it, report, repeat until shutdown
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
