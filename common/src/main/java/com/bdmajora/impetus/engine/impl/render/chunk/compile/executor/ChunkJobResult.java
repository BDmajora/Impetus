package com.bdmajora.impetus.engine.impl.render.chunk.compile.executor;

import java.util.Objects;

// Outcome of a chunk job.
public sealed interface ChunkJobResult<OUTPUT> permits ChunkJobResult.Success, ChunkJobResult.Failure {
    // Successful outcome with its output and run time.
    record Success<OUTPUT>(OUTPUT output, long executionTimeNanos) implements ChunkJobResult<OUTPUT> {

    }

    // Failed outcome with its cause.
    record Failure<OUTPUT>(Throwable throwable) implements ChunkJobResult<OUTPUT> {
        // Rejects a null cause.
        public Failure {
            Objects.requireNonNull(throwable);
        }

        // Throws the cause as a runtime exception.
        public void abort() {
            if (this.throwable instanceof RuntimeException crashException) {
                // Rethrows runtime exceptions unchanged
                throw crashException;
            } else {
                throw new RuntimeException("Exception thrown while executing job", this.throwable);
            }
        }
    }
}
