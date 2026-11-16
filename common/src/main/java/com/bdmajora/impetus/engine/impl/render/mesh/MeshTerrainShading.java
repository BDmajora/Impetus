package com.bdmajora.impetus.engine.impl.render.mesh;

import com.bdmajora.impetus.engine.impl.render.chunk.ChunkRenderMatrices;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass;
import org.jetbrains.annotations.Nullable;

// Programs that shade the quads found visible by the mesh pipeline.
public interface MeshTerrainShading {
    // Returns the bytes of one vertex in the quad arena.
    int vertexStride();

    // Binds the opaque program for a phase and returns false when there is none.
    boolean bindOpaque(MeshDrawPhase phase, @Nullable TerrainRenderPass pass, ChunkRenderMatrices matrices);

    // Binds the translucent program for a phase and returns false when there is none.
    boolean bindTranslucent(MeshDrawPhase phase, @Nullable TerrainRenderPass pass, ChunkRenderMatrices matrices);

    // Undoes the state of the last bind.
    void finish();

    // Returns whether the programs can draw the shadow map.
    boolean hasShadowPrograms();

    // Deletes every program.
    void delete();
}
