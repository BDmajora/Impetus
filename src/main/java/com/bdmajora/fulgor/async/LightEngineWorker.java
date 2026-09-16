package com.bdmajora.fulgor.async;

import com.bdmajora.extras.async.ParallelWorkerThread;
import com.bdmajora.fulgor.Fulgor;
import com.bdmajora.fulgor.async.engine.BfsLightEngine;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

// Drains one light lane: the server gives each lane a daemon thread, the client calls processPending from its tick instead
final class LightEngineWorker {
    private static final int MAX_CACHED_ENGINES = 4;
    // Time slices so a flood of chunk loads cannot starve player edits, and edge checks cannot starve either
    private static final long EDGE_CHECK_BUDGET_NS = 10_000_000L;
    private static final long BLOCK_CHANGE_BUDGET_NS = 5_000_000L;

    private final LightQueue queue;
    private final ConcurrentLinkedDeque<BfsLightEngine> enginePool = new ConcurrentLinkedDeque<>();
    private final Supplier<BfsLightEngine> engineFactory;
    private final BiConsumer<ChunkTasks, BfsLightEngine> taskProcessor;
    private final String laneName;
    private final Thread thread;

    private volatile boolean running = true;

    LightEngineWorker(LightQueue queue, Supplier<BfsLightEngine> engineFactory,
                      BiConsumer<ChunkTasks, BfsLightEngine> taskProcessor, String laneName, boolean startThread) {
        this.queue = queue;
        this.engineFactory = engineFactory;
        this.taskProcessor = taskProcessor;
        this.laneName = laneName;

        if (startThread) {
            // A ParallelWorkerThread so Fulgor's own off-thread checks recognise the lane as a legitimate reader
            this.thread = new ParallelWorkerThread(this::run, "Impetus-Fulgor-" + laneName);
            this.thread.setDaemon(true);
            this.thread.setPriority(Thread.NORM_PRIORITY - 1);
            this.thread.start();
        } else {
            this.thread = null;
        }
    }

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

    // Block changes first, then initial lights (with block changes interleaved), then edge maintenance under a budget
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

    private void processTask(ChunkTasks task, BfsLightEngine engine) {
        try {
            this.taskProcessor.accept(task, engine);
        } finally {
            this.queue.completeTask(task);
        }
    }

    private BfsLightEngine acquireEngine() {
        BfsLightEngine cached = this.enginePool.pollFirst();
        return cached != null ? cached : this.engineFactory.get();
    }

    private void releaseEngine(BfsLightEngine engine) {
        if (this.enginePool.size() < MAX_CACHED_ENGINES) {
            this.enginePool.addFirst(engine);
        }
    }

    void requestStop() {
        this.running = false;
        this.queue.wakeUp();
    }

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
