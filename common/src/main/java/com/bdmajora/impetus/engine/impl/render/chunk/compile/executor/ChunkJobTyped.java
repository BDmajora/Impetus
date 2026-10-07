package com.bdmajora.impetus.engine.impl.render.chunk.compile.executor;

import com.bdmajora.impetus.engine.impl.render.chunk.compile.ChunkBuildContext;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.tasks.ChunkBuilderTask;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public class ChunkJobTyped<TASK extends ChunkBuilderTask<OUTPUT>, OUTPUT>
        implements ChunkJob
{
    private final TASK task;
    private final Consumer<@Nullable ChunkJobResult<OUTPUT>> consumer;

    private volatile boolean cancelled;
    private volatile boolean started;

    // Ordering keys assigned by ChunkJobQueue when the job is enqueued. Only accessed under the queue's lock.
    long priority;
    long sequence;

    ChunkJobTyped(TASK task, Consumer<@Nullable ChunkJobResult<OUTPUT>> consumer) {
        this.task = task;
        this.consumer = consumer;
    }

    // Whether cancel was requested
    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    // Requests cancellation; a running task checks the token
    @Override
    public void setCancelled() {
        this.cancelled = true;
    }

    // Runs the task, catching everything into a failure result so a worker never dies; a cancelled job still reports (with null), or a collector waiting on it never wakes
    @Override
    public void execute(ChunkBuildContext context) {
        ChunkJobResult<OUTPUT> result = null;

        if (!this.cancelled) {
            this.started = true;

            long startTime = System.nanoTime();

            try {
                var output = this.task.execute(context, this);

                // A null output means the task was cancelled while executing
                if (output != null) {
                    result = new ChunkJobResult.Success<>(output, System.nanoTime() - startTime);
                }
            } catch (Throwable throwable) {
                result = new ChunkJobResult.Failure<>(throwable);
                ChunkBuilder.LOGGER.error("Chunk build failed", throwable);
            }
        }

        try {
            this.consumer.accept(result);
        } catch (Throwable throwable) {
            throw new RuntimeException("Exception while consuming result", throwable);
        }
    }

    // Whether a worker picked it up
    @Override
    public boolean isStarted() {
        return this.started;
    }
}
