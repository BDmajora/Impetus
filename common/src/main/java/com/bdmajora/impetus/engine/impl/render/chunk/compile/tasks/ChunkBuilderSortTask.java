package com.bdmajora.impetus.engine.impl.render.chunk.compile.tasks;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import com.bdmajora.impetus.engine.impl.render.chunk.RenderPassConfiguration;
import com.bdmajora.impetus.engine.impl.render.chunk.RenderSection;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.ChunkBuildContext;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.ChunkSortOutput;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass;
import com.bdmajora.impetus.engine.impl.common.util.NativeBuffer;
import com.bdmajora.impetus.engine.impl.util.task.CancellationToken;
import com.bdmajora.impetus.engine.impl.render.chunk.sorting.SortState;

import java.util.Map;

// Task that sorts the translucent meshes of a section.
public class ChunkBuilderSortTask extends ChunkBuilderTask<ChunkSortOutput> {
    // Section to sort.
    private final RenderSection render;
    // Camera position.
    private final double cameraX, cameraY, cameraZ;
    // Frame the task was created in.
    private final int frame;
    // Meshes to sort by pass.
    private final Map<TerrainRenderPass, SortState.Resortable> translucentMeshes;
    // Configuration of the render passes.
    private final RenderPassConfiguration<?> renderPassConfiguration;

    // Creates a task and rejects a null section or null meshes.
    public ChunkBuilderSortTask(RenderSection render, double cameraX, double cameraY, double cameraZ, int frame, Map<TerrainRenderPass, SortState.Resortable> translucentMeshes, RenderPassConfiguration<?> renderPassConfiguration) {
        this.render = java.util.Objects.requireNonNull(render, "render");
        this.cameraX = cameraX;
        this.cameraY = cameraY;
        this.cameraZ = cameraZ;
        this.frame = frame;
        this.translucentMeshes = java.util.Objects.requireNonNull(translucentMeshes, "translucentMeshes");
        this.renderPassConfiguration = renderPassConfiguration;
    }

    // Sorts every mesh and returns the output or null when cancelled and frees the buffers on a failure.
    @Override
    public ChunkSortOutput execute(ChunkBuildContext context, CancellationToken cancellationSource) {
        var meshes = new Reference2ReferenceOpenHashMap<TerrainRenderPass, ChunkSortOutput.SortedMesh>();
        try {
            for (Map.Entry<TerrainRenderPass, SortState.Resortable> entry : translucentMeshes.entrySet()) {
                if (cancellationSource != null && cancellationSource.isCancelled()) {
                    freeAll(meshes);
                    return null;
                }

                var sortInfo = entry.getValue();
                var primitiveType = entry.getKey().primitiveType();
                var newIndexBuffer = new NativeBuffer(primitiveType.getIndexBufferSize(sortInfo.quadCount()));
                try {
                    primitiveType.generateSortedIndexBuffer(newIndexBuffer.getDirectBuffer(), sortInfo.quadCount(), sortInfo, (float) (cameraX - this.render.getOriginX()), (float) (cameraY - this.render.getOriginY()), (float) (cameraZ - this.render.getOriginZ()));
                } catch (Throwable failure) {
                    newIndexBuffer.free();
                    throw failure;
                }
                meshes.put(entry.getKey(), new ChunkSortOutput.SortedMesh(newIndexBuffer));
            }
        } catch (Throwable failure) {
            freeAll(meshes);
            throw failure;
        }
        return new ChunkSortOutput(render, this.frame, meshes);
    }

    // Frees the index buffers of the meshes.
    private static void freeAll(Reference2ReferenceOpenHashMap<TerrainRenderPass, ChunkSortOutput.SortedMesh> meshes) {
        for (var mesh : meshes.values()) {
            mesh.indexData().free();
        }
        meshes.clear();
    }
}
