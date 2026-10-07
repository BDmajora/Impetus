package com.bdmajora.impetus.engine.impl.render.chunk.compile;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceMap;
import com.bdmajora.impetus.engine.impl.render.chunk.RenderSection;
import com.bdmajora.impetus.engine.impl.render.chunk.data.BuiltRenderSectionData;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass;
import com.bdmajora.impetus.engine.impl.render.chunk.data.BuiltSectionMeshParts;
import com.bdmajora.impetus.engine.impl.render.mesh.region.SectionGeometry;
import org.jetbrains.annotations.Nullable;

// Everything a finished rebuild produced that the main thread must process or upload; a result cancelled before processing is DISCARDED so a section the player left is not re-added to the draw lists
public class ChunkBuildOutput extends ChunkTaskOutput {
    public final BuiltRenderSectionData info;
    public final Reference2ReferenceMap<TerrainRenderPass, BuiltSectionMeshParts> meshes;
    // Every pass merged for the mesh-shader backend, packed on the build worker; null when that backend is off or the section built to nothing
    public final @Nullable SectionGeometry meshGeometry;

    public ChunkBuildOutput(RenderSection render, BuiltRenderSectionData info, Reference2ReferenceMap<TerrainRenderPass, BuiltSectionMeshParts> meshes, int buildTime) {
        this(render, info, meshes, null, buildTime);
    }

    public ChunkBuildOutput(RenderSection render, BuiltRenderSectionData info, Reference2ReferenceMap<TerrainRenderPass, BuiltSectionMeshParts> meshes,
                            @Nullable SectionGeometry meshGeometry, int buildTime) {
        super(render, buildTime);
        this.info = info;
        this.meshes = meshes;
        this.meshGeometry = meshGeometry;

        if (this.info != null) {
            this.info.bake();
        }
    }

    // Frees the mesh buffers if the result was never consumed
    @Override
    public void delete() {
        for (BuiltSectionMeshParts data : this.meshes.values()) {
            data.free();
        }

        if (this.meshGeometry != null) {
            this.meshGeometry.delete();
        }
    }
}
