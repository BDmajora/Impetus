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

public class ChunkBuilderSortTask extends ChunkBuilderTask<ChunkSortOutput> {
    private final RenderSection render;
    private final double cameraX, cameraY, cameraZ;
    private final int frame;
    private final Map<TerrainRenderPass, SortState.Resortable> translucentMeshes;
    private final RenderPassConfiguration<?> renderPassConfiguration;

    public ChunkBuilderSortTask(RenderSection render, double cameraX, double cameraY, double cameraZ, int frame, Map<TerrainRenderPass, SortState.Resortable> translucentMeshes, RenderPassConfiguration<?> renderPassConfiguration) {
        this.render = render;
        this.cameraX = cameraX;
        this.cameraY = cameraY;
        this.cameraZ = cameraZ;
        this.frame = frame;
        this.translucentMeshes = translucentMeshes;
        this.renderPassConfiguration = renderPassConfiguration;
    }

    @Override
    public ChunkSortOutput execute(ChunkBuildContext context, CancellationToken cancellationSource) {
        var meshes = new Reference2ReferenceOpenHashMap<TerrainRenderPass, ChunkSortOutput.SortedMesh>();
        for(Map.Entry<TerrainRenderPass, SortState.Resortable> entry : translucentMeshes.entrySet()) {
            var sortInfo = entry.getValue();
            var primitiveType = entry.getKey().primitiveType();
            var newIndexBuffer = new NativeBuffer(primitiveType.getIndexBufferSize(sortInfo.quadCount()));
            primitiveType.generateSortedIndexBuffer(newIndexBuffer.getDirectBuffer(), sortInfo.quadCount(), sortInfo, (float) (cameraX - this.render.getOriginX()), (float) (cameraY - this.render.getOriginY()), (float) (cameraZ - this.render.getOriginZ()));
            meshes.put(entry.getKey(), new ChunkSortOutput.SortedMesh(
                    newIndexBuffer
            ));
        }
        return new ChunkSortOutput(render, this.frame, meshes);
    }
}
