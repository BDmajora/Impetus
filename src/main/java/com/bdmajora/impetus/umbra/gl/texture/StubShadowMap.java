package com.bdmajora.impetus.umbra.gl.texture;

import com.bdmajora.impetus.umbra.gl.GlResource;
import com.bdmajora.impetus.lwjgl.GL11;
import com.bdmajora.impetus.lwjgl.GL14;
import com.bdmajora.impetus.lwjgl.MemoryStack;

import java.nio.ByteBuffer;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// A 1x1 depth texture holding 1.0, bound to shadowtex0/1 when there is no shadow pass; 1.0 is the clear value every pack reads as fully lit (what OptiFine shows without a shadow program), and raw or compare samplers layer over it per program
public class StubShadowMap extends GlResource {
    public StubShadowMap() {
        setHandle(LWJGL.glGenTextures());
        LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, getGlId());
        TextureParameters.set2D(GL11.GL_NEAREST, GL11.GL_REPEAT);
        try (MemoryStack stack = LWJGL.stackPush()) {
            ByteBuffer depth = stack.malloc(Float.BYTES);
            depth.putFloat(1.0f);
            depth.flip();
            LWJGL.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL14.GL_DEPTH_COMPONENT24, 1, 1, 0,
                    GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, depth);
        }
        LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    // For binding as shadowtex0/1
    public int getTextureId() {
        return getGlId();
    }

    // Frees the texture
    @Override
    protected void destroyInternal() {
        LWJGL.glDeleteTextures(getGlId());
    }
}
