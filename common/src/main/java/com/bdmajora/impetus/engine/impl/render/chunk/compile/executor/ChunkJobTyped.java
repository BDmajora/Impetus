package com.bdmajora.impetus.engine.impl.render.chunk.compile.executor;

import com.bdmajora.impetus.engine.impl.render.chunk.compile.ChunkBuildContext;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.tasks.ChunkBuilderTask;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

// Job that runs one builder task and reports its result.
public class ChunkJobTyped<TASK extends ChunkBuilderTask<OUTPUT>, OUTPUT>
        implements ChunkJob
{
    // Task to run.
    private final TASK task;
    // Receiver of the result.
    private final Consumer<@Nullable ChunkJobResult<OUTPUT>> consumer;

    // Whether cancellation was requested.
    private volatile boolean cancelled;
    // Whether a worker started the job.
    private volatile boolean started;

    // Priority assigned by the queue.
    long priority;
    // Order of arrival assigned by the queue.
    long sequence;

    // Creates a job and rejects a null task or receiver.
    ChunkJobTyped(TASK task, Consumer<@Nullable ChunkJobResult<OUTPUT>> consumer) {
        this.task = java.util.Objects.requireNonNull(task, "task");
        this.consumer = java.util.Objects.requireNonNull(consumer, "consumer");
    }

    // Returns whether cancellation was requested.
    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    // Requests cancellation.
    @Override
    public void setCancelled() {
        this.cancelled = true;
    }

    // Runs the task and passes the result or the failure or null for a cancelled job to the receiver.
    @Override
    public void execute(ChunkBuildContext context) {
        ChunkJobResult<OUTPUT> result = null;

        if (!this.cancelled) {
            this.started = true;

            long startTime = System.nanoTime();

            try {
                var output = this.task.execute(context, this);

                // Treats a null output as cancelled
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

    // Returns whether a worker started the job.
    @Override
    public boolean isStarted() {
        return this.started;
    }
}
