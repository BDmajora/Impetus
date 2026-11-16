package com.bdmajora.impetus.engine.impl.render.chunk.compile;

import com.bdmajora.impetus.engine.impl.render.chunk.RenderSection;

// Result of a chunk task.
public abstract class ChunkTaskOutput {
    // Section the task ran for.
    public final RenderSection render;
    // Time the task took.
    public final int buildTime;

    // Creates an output.
    protected ChunkTaskOutput(RenderSection render, int buildTime) {
        this.render = render;
        this.buildTime = buildTime;
    }

    // Frees the resources of the output.
    public void delete() {

    }
}
