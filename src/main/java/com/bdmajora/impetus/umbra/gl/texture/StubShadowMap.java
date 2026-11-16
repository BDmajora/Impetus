package com.bdmajora.impetus.umbra.gl.texture;

import com.bdmajora.impetus.umbra.gl.GlResource;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// One pixel depth texture bound when no shadow map exists.
public class StubShadowMap extends GlResource {
    // Creates a stub shadow map with default state.
    public StubShadowMap() {
        setHandle(LWJGL.glGenTextures());
        LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, getGlId());
        TextureParameters.set2D(GL11.GL_NEAREST, GL11.GL_REPEAT);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer depth = stack.malloc(Float.BYTES);
            depth.putFloat(1.0f);
            depth.flip();
            LWJGL.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL14.GL_DEPTH_COMPONENT24, 1, 1, 0,
                    GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, depth);
        }
        LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    // Returns the texture id.
    public int getTextureId() {
        return getGlId();
    }

    // Deletes the texture.
    @Override
    protected void destroyInternal() {
        LWJGL.glDeleteTextures(getGlId());
    }
}
