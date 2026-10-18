package com.bdmajora.impetus.engine.impl.render.mesh;

import com.bdmajora.impetus.engine.impl.render.chunk.ChunkRenderMatrices;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass;
import org.jetbrains.annotations.Nullable;

// The programs that shade the quads the mesh pipeline found visible: Nvidium's own, or a shader pack's translated to the same task and mesh stages
public interface MeshTerrainShading {
    // Bytes per vertex in the quad arena, a whole number of uvec4s
    int vertexStride();

    // Binds the opaque program for a phase, false when there is none to draw with; the first bind after finish() also applies the pass's GL state, which survives the occlusion programs bound in between
    boolean bindOpaque(MeshDrawPhase phase, @Nullable TerrainRenderPass pass, ChunkRenderMatrices matrices);

    // Binds the translucent program for a phase and applies the pass's state, false when there is none
    boolean bindTranslucent(MeshDrawPhase phase, @Nullable TerrainRenderPass pass, ChunkRenderMatrices matrices);

    // Undoes the state the last bind applied
    void finish();

    // Whether these programs can draw the shadow map
    boolean hasShadowPrograms();

    // Frees every program
    void delete();
}
