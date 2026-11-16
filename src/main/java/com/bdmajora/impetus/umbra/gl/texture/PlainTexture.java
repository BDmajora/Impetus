package com.bdmajora.impetus.umbra.gl.texture;

import com.bdmajora.impetus.umbra.gl.GlResource;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Texture made from raw pixel data.
public class PlainTexture extends GlResource {
    // Creates a plain texture from its red and green and blue and alpha.
    public PlainTexture(int red, int green, int blue, int alpha) {
        setHandle(LWJGL.glGenTextures());
        LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, getGlId());
        TextureParameters.set2D(GL11.GL_NEAREST, GL11.GL_REPEAT);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer pixel = stack.malloc(4);
            pixel.put((byte) red).put((byte) green).put((byte) blue).put((byte) alpha);
            pixel.flip();
            LWJGL.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 1, 1, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
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
