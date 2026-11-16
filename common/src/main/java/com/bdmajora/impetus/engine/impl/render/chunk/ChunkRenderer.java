package com.bdmajora.impetus.engine.impl.render.chunk;

import com.bdmajora.impetus.engine.impl.gl.device.CommandList;
import com.bdmajora.impetus.engine.impl.render.chunk.lists.ChunkRenderListIterable;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass;
import com.bdmajora.impetus.engine.impl.render.viewport.CameraTransform;

// Backend that uploads and draws chunk geometry.
public interface ChunkRenderer {
    // Draws the render lists of a pass to the active framebuffer.
    void render(ChunkRenderMatrices matrices, CommandList commandList, ChunkRenderListIterable renderLists,
                TerrainRenderPass pass, CameraTransform occlusionCamera, CameraTransform camera);

    // Deletes the backend and its resources.
    void delete(CommandList commandList);

    // Returns the render pass configuration.
    RenderPassConfiguration<?> getRenderPassConfiguration();
}
