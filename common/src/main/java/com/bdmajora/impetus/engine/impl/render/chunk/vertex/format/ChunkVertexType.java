package com.bdmajora.impetus.engine.impl.render.chunk.vertex.format;

import com.bdmajora.impetus.engine.impl.gl.attribute.GlVertexFormat;
import org.jetbrains.annotations.MustBeInvokedByOverriders;

import java.util.HashMap;
import java.util.Map;

// Vertex layout of the terrain.
public interface ChunkVertexType {
    // Returns the scale of the positions.
    float getPositionScale();

    // Returns the offset of the positions.
    float getPositionOffset();

    // Returns the scale of the texture coordinates.
    float getTextureScale();

    // Returns the GL format.
    GlVertexFormat getVertexFormat();

    // Creates an encoder for the format.
    ChunkVertexEncoder createEncoder();

    // Returns the shader definitions of the format.
    @MustBeInvokedByOverriders
    default Map<String, String> getDefines() {
        var defines = new HashMap<String, String>();
        defines.put("VERT_POS_SCALE", String.valueOf(this.getPositionScale()));
        defines.put("VERT_POS_OFFSET", String.valueOf(this.getPositionOffset()));
        defines.put("VERT_TEX_SCALE", String.valueOf(this.getTextureScale()));

        return defines;
    }
}
