package com.bdmajora.fulgor.async;

import com.bdmajora.extras.async.ParallelWorkerThread;
import com.bdmajora.fulgor.Fulgor;
import com.bdmajora.fulgor.async.engine.BfsLightEngine;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

// Worker thread that runs queued light tasks.
final class LightEngineWorker {
    // Most engines kept for reuse.
    private static final int MAX_CACHED_ENGINES = 4;
    // Time budget of edge checks per round.
    private static final long EDGE_CHECK_BUDGET_NS = 10_000_000L;
    // Time budget of block changes per round.
    private static final long BLOCK_CHANGE_BUDGET_NS = 5_000_000L;

    // Queue of light tasks.
    private final LightQueue queue;
    // Engines kept for reuse.
    private final ConcurrentLinkedDeque<BfsLightEngine> enginePool = new ConcurrentLinkedDeque<>();
    // Factory of new engines.
    private final Supplier<BfsLightEngine> engineFactory;
    private final BiConsumer<ChunkTasks, BfsLightEngine> taskProcessor;
    // Name of the worker lane.
    private final String laneName;
    // Thread that runs the tasks.
    private final Thread thread;

    // Flag cleared to stop the thread.
    private volatile boolean running = true;

    // Creates a worker and starts its thread when asked.
    LightEngineWorker(LightQueue queue, Supplier<BfsLightEngine> engineFactory,
                      BiConsumer<ChunkTasks, BfsLightEngine> taskProcessor, String laneName, boolean startThread) {
        this.queue = queue;
        this.engineFactory = engineFactory;
        this.taskProcessor = taskProcessor;
        this.laneName = laneName;

        if (startThread) {
            this.thread = new ParallelWorkerThread(this::run, "Impetus-Fulgor-" + laneName);
            this.thread.setDaemon(true);
            this.thread.setPriority(Thread.NORM_PRIORITY - 1);
            this.thread.start();
        } else {
            this.thread = null;
        }
    }

    // Runs light tasks until stopped.
    private void run() {
        while (this.running) {
            if (this.queue.isEmpty()) {
                try {
                    this.queue.waitForWork();
                } catch (InterruptedException e) {
                    break;
                }
            }
            processPending();
        }
    }

    // Runs queued tasks within the round budgets.
    void processPending() {
        BfsLightEngine engine = acquireEngine();
        try {
            long changeDeadline = System.nanoTime() + BLOCK_CHANGE_BUDGET_NS;
            ChunkTasks task;
            while ((task = this.queue.removeFirstBlockChangeTask()) != null) {
                processTask(task, engine);
                if (System.nanoTime() > changeDeadline) {
                    break;
                }
            }

            boolean moreWork = true;
            while (moreWork) {
                moreWork = false;
                while ((task = this.queue.removeFirstInitialLightTask()) != null) {
                    processTask(task, engine);
                    ChunkTasks priorityTask;
                    while ((priorityTask = this.queue.removeFirstBlockChangeTask()) != null) {
                        processTask(priorityTask, engine);
                    }
                }

                long edgeDeadline = System.nanoTime() + EDGE_CHECK_BUDGET_NS;
                while ((task = this.queue.removeFirstTask()) != null) {
                    processTask(task, engine);
                    ChunkTasks priorityTask;
                    while ((priorityTask = this.queue.removeFirstBlockChangeTask()) != null) {
                        processTask(priorityTask, engine);
                    }
                    if (this.queue.hasInitialLightTask()) {
                        moreWork = true;
                        break;
                    }
                    if (System.nanoTime() > edgeDeadline) {
                        break;
                    }
                }
            }
        } catch (Throwable t) {
            Fulgor.LOGGER.error("Exception in the {} light lane", this.laneName, t);
        } finally {
            releaseEngine(engine);
        }
    }

    // Runs one task with an engine.
    private void processTask(ChunkTasks task, BfsLightEngine engine) {
        try {
            this.taskProcessor.accept(task, engine);
        } finally {
            this.queue.completeTask(task);
        }
    }

    // Takes a pooled engine or creates one.
    private BfsLightEngine acquireEngine() {
        BfsLightEngine cached = this.enginePool.pollFirst();
        return cached != null ? cached : this.engineFactory.get();
    }

    // Returns an engine to the pool.
    private void releaseEngine(BfsLightEngine engine) {
        if (this.enginePool.size() < MAX_CACHED_ENGINES) {
            this.enginePool.addFirst(engine);
        }
    }

    // Asks the thread to stop.
    void requestStop() {
        this.running = false;
        this.queue.wakeUp();
    }

    // Waits up to a second for the thread to end.
    void awaitStop() {
        if (this.thread == null) {
            return;
        }
        try {
            this.thread.join(1000L);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
