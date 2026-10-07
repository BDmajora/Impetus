package com.bdmajora.impetus.engine.impl.render.chunk.data;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import com.bdmajora.impetus.engine.impl.gl.util.VertexRange;
import com.bdmajora.impetus.engine.impl.common.util.NativeBuffer;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.ChunkBuildBuffers;
import com.bdmajora.impetus.engine.impl.render.chunk.sorting.SortState;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public record BuiltSectionMeshParts(NativeBuffer vertexBuffer, @Nullable NativeBuffer indexBuffer, @Nullable SortState sortState, Map<ModelQuadFacing, VertexRange> ranges) {
    public void free() {
        vertexBuffer.free();
        if (indexBuffer != null) {
            indexBuffer.free();
        }
    }

    // Packs each pass's facing buffers into one mesh with per-facing ranges
    public static Reference2ReferenceMap<TerrainRenderPass, BuiltSectionMeshParts> groupFromBuildBuffers(ChunkBuildBuffers buffers, float relativeCameraX, float relativeCameraY, float relativeCameraZ) {
        Reference2ReferenceMap<TerrainRenderPass, BuiltSectionMeshParts> meshes = new Reference2ReferenceOpenHashMap<>();

        for (TerrainRenderPass pass : buffers.getBuilderPasses()) {
            BuiltSectionMeshParts mesh = buffers.createMesh(pass, relativeCameraX, relativeCameraY, relativeCameraZ);

            if (mesh != null) {
                meshes.put(pass, mesh);
            }
        }

        return meshes;
    }
}
