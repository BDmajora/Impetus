package com.bdmajora.impetus.engine.impl.render.chunk.shader;

import com.bdmajora.impetus.engine.impl.gl.tessellation.GlPrimitiveType;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass;
import org.joml.Matrix4fc;

// Uniforms and state of a chunk shader.
public interface ChunkShaderInterface {
    // Prepares the state for a pass.
    void setupState(TerrainRenderPass pass);
    // Restores the state after a pass.
    default void restoreState() {}
    // Returns the primitive type of the shader.
    GlPrimitiveType getPrimitiveType();
    // Stores the projection matrix.
    void setProjectionMatrix(Matrix4fc matrix);
    // Stores the model view matrix.
    void setModelViewMatrix(Matrix4fc matrix);
    // Stores the offset of the region.
    void setRegionOffset(float x, float y, float z);
    // Stores the unit of a texture slot.
    void setTextureSlot(ChunkShaderTextureSlot slot, int val);

    // Stores the load times of the sections.
    default void setSectionAges(long timestamp, long[] loadTimes) {

    }
}
