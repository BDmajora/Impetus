package com.bdmajora.impetus.engine.impl.render.chunk.compile;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceMap;
import com.bdmajora.impetus.engine.impl.render.chunk.RenderSection;
import com.bdmajora.impetus.engine.impl.render.chunk.data.BuiltRenderSectionData;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass;
import com.bdmajora.impetus.engine.impl.render.chunk.data.BuiltSectionMeshParts;
import com.bdmajora.impetus.engine.impl.render.mesh.region.SectionGeometry;
import org.jetbrains.annotations.Nullable;

// Result of a finished section rebuild.
public class ChunkBuildOutput extends ChunkTaskOutput {
    // Data of the built section.
    public final BuiltRenderSectionData info;
    // Mesh parts by render pass.
    public final Reference2ReferenceMap<TerrainRenderPass, BuiltSectionMeshParts> meshes;
    // Geometry merged for the mesh shader backend or null.
    public final @Nullable SectionGeometry meshGeometry;

    // Creates an output without mesh shader geometry.
    public ChunkBuildOutput(RenderSection render, BuiltRenderSectionData info, Reference2ReferenceMap<TerrainRenderPass, BuiltSectionMeshParts> meshes, int buildTime) {
        this(render, info, meshes, null, buildTime);
    }

    // Creates an output and bakes the section data and rejects null meshes.
    public ChunkBuildOutput(RenderSection render, BuiltRenderSectionData info, Reference2ReferenceMap<TerrainRenderPass, BuiltSectionMeshParts> meshes,
                            @Nullable SectionGeometry meshGeometry, int buildTime) {
        super(render, buildTime);
        this.info = info;
        this.meshes = java.util.Objects.requireNonNull(meshes, "meshes");
        this.meshGeometry = meshGeometry;

        if (this.info != null) {
            this.info.bake();
        }
    }

    // Frees the mesh buffers and continues after a failure and rethrows the first one.
    @Override
    public void delete() {
        RuntimeException failure = null;

        for (BuiltSectionMeshParts data : this.meshes.values()) {
            try {
                data.free();
            } catch (RuntimeException e) {
                if (failure == null) failure = e; else failure.addSuppressed(e);
            }
        }

        if (this.meshGeometry != null) {
            try {
                this.meshGeometry.delete();
            } catch (RuntimeException e) {
                if (failure == null) failure = e; else failure.addSuppressed(e);
            }
        }

        if (failure != null) {
            throw failure;
        }
    }
}
