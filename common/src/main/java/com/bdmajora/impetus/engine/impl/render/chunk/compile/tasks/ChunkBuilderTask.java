package com.bdmajora.impetus.engine.impl.render.chunk.compile.tasks;

import com.bdmajora.impetus.engine.impl.render.chunk.compile.ChunkBuildContext;
import com.bdmajora.impetus.engine.impl.util.task.CancellationToken;

// Work item that meshes or sorts a chunk off the main thread.
public abstract class ChunkBuilderTask<OUTPUT> {
    // Runs the task and returns its output or null when cancelled.
    public abstract OUTPUT execute(ChunkBuildContext context, CancellationToken cancellationToken);
}
