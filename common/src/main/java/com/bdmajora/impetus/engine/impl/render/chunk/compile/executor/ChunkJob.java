package com.bdmajora.impetus.engine.impl.render.chunk.compile.executor;

import com.bdmajora.impetus.engine.impl.render.chunk.compile.ChunkBuildContext;
import com.bdmajora.impetus.engine.impl.util.task.CancellationToken;

// Unit of work for the chunk builder threads.
public interface ChunkJob extends CancellationToken {
    // Runs the job with a build context.
    void execute(ChunkBuildContext context);

    // Returns whether a worker started the job.
    boolean isStarted();
}
