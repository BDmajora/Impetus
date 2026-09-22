package com.bdmajora.impetus.engine.impl.render.chunk.compile.tasks;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import com.bdmajora.impetus.engine.impl.render.chunk.RenderSection;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.ChunkBuildContext;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.ChunkSortOutput;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass;
import com.bdmajora.impetus.engine.impl.common.util.NativeBuffer;
import com.bdmajora.impetus.engine.impl.util.task.CancellationToken;
import com.bdmajora.impetus.engine.impl.render.chunk.sorting.TranslucentQuadAnalyzer;

import java.util.Map;

public class ChunkBuilderSortTask extends ChunkBuilderTask<ChunkSortOutput> {
    private final RenderSection render;
    private final float cameraX, cameraY, cameraZ;
    private final int frame;
    private final Map<TerrainRenderPass, TranslucentQuadAnalyzer.SortState> translucentMeshes;

    public ChunkBuilderSortTask(RenderSection render, float cameraX, float cameraY, float cameraZ, int frame, Map<TerrainRenderPass, TranslucentQuadAnalyzer.SortState> translucentMeshes) {
        this.render = render;
        this.cameraX = cameraX;
        this.cameraY = cameraY;
        this.cameraZ = cameraZ;
        this.frame = frame;
        this.translucentMeshes = translucentMeshes;
    }

    // Re-sorts a section's translucent quads for a new camera position without rebuilding
    @Override
    public ChunkSortOutput execute(ChunkBuildContext context, CancellationToken cancellationSource) {
        var meshes = new Reference2ReferenceOpenHashMap<TerrainRenderPass, ChunkSortOutput.SortedMesh>();
        for(Map.Entry<TerrainRenderPass, TranslucentQuadAnalyzer.SortState> entry : translucentMeshes.entrySet()) {
            var sortInfo = entry.getValue();
            var primitiveType = entry.getKey().primitiveType();
            var newIndexBuffer = new NativeBuffer(primitiveType.getIndexBufferSize(sortInfo.centersLength() / 3));
            primitiveType.generateSortedIndexBuffer(newIndexBuffer.getDirectBuffer(), sortInfo.centersLength() / 3, sortInfo, cameraX - this.render.getOriginX(), cameraY - this.render.getOriginY(), cameraZ - this.render.getOriginZ());
            meshes.put(entry.getKey(), new ChunkSortOutput.SortedMesh(
                    newIndexBuffer
            ));
        }
        return new ChunkSortOutput(render, this.frame, meshes);
    }
}
