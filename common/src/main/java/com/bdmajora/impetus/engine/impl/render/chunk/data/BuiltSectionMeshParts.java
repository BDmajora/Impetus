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

// Vertex and index data of one pass of a section.
public record BuiltSectionMeshParts(NativeBuffer vertexBuffer, @Nullable NativeBuffer indexBuffer, @Nullable SortState sortState, Map<ModelQuadFacing, VertexRange> ranges) {
    // Rejects a null vertex buffer or ranges.
    public BuiltSectionMeshParts {
        java.util.Objects.requireNonNull(vertexBuffer, "vertexBuffer");
        java.util.Objects.requireNonNull(ranges, "ranges");
    }

    // Frees both buffers and still frees the index buffer when the vertex buffer fails.
    public void free() {
        try {
            vertexBuffer.free();
        } finally {
            if (indexBuffer != null) {
                indexBuffer.free();
            }
        }
    }

    // Creates the meshes of every pass of the buffers and frees them again when one pass fails.
    public static Reference2ReferenceMap<TerrainRenderPass, BuiltSectionMeshParts> groupFromBuildBuffers(ChunkBuildBuffers buffers, float relativeCameraX, float relativeCameraY, float relativeCameraZ) {
        Reference2ReferenceMap<TerrainRenderPass, BuiltSectionMeshParts> meshes = new Reference2ReferenceOpenHashMap<>();

        try {
            for (TerrainRenderPass pass : buffers.getBuilderPasses()) {
                BuiltSectionMeshParts mesh = buffers.createMesh(pass, relativeCameraX, relativeCameraY, relativeCameraZ);

                if (mesh != null) {
                    meshes.put(pass, mesh);
                }
            }
        } catch (Throwable failure) {
            for (BuiltSectionMeshParts mesh : meshes.values()) {
                mesh.free();
            }
            throw failure;
        }

        return meshes;
    }
}
