package com.bdmajora.impetus.engine.impl.render.chunk.compile;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceMap;
import com.bdmajora.impetus.engine.impl.common.util.NativeBuffer;
import com.bdmajora.impetus.engine.impl.render.chunk.RenderSection;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass;

// Result of a finished sort of a section.
public class ChunkSortOutput extends ChunkTaskOutput {
    // Sorted index buffer of one translucent pass.
    public record SortedMesh(NativeBuffer indexData) {}

    // Sorted meshes by render pass.
    public final Reference2ReferenceMap<TerrainRenderPass, SortedMesh> meshes;

    // Creates an output and rejects null meshes.
    public ChunkSortOutput(RenderSection render, int buildTime, Reference2ReferenceMap<TerrainRenderPass, SortedMesh> meshes) {
        super(render, buildTime);
        this.meshes = java.util.Objects.requireNonNull(meshes, "meshes");
    }

    // Frees every index buffer and continues after a failure and rethrows the first one.
    @Override
    public void delete() {
        RuntimeException failure = null;

        for (SortedMesh data : this.meshes.values()) {
            try {
                data.indexData().free();
            } catch (RuntimeException e) {
                if (failure == null) failure = e; else failure.addSuppressed(e);
            }
        }

        if (failure != null) {
            throw failure;
        }
    }
}
