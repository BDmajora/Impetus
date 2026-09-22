package com.bdmajora.impetus.umbra.gl.texture;

import com.bdmajora.impetus.umbra.gl.GlResource;
import com.bdmajora.impetus.lwjgl.GL11;
import com.bdmajora.impetus.lwjgl.MemoryStack;

import java.nio.ByteBuffer;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// A 1x1 solid-colour texture bound as the fallback `normals`/`specular` samplers when the resource pack ships no PBR maps (OptiFine's flat up-normal and black specular), so PBR math always reads a defined value
public class PlainTexture extends GlResource {
    // Nearest filtering and repeat wrapping: at 1x1 neither matters, but the driver defaults would make a mipmap-incomplete texture that samples black on some drivers
    public PlainTexture(int red, int green, int blue, int alpha) {
        setHandle(LWJGL.glGenTextures());
        LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, getGlId());
        TextureParameters.set2D(GL11.GL_NEAREST, GL11.GL_REPEAT);
        // One RGBA pixel on the thread-local stack; nothing here outlives the constructor
        try (MemoryStack stack = LWJGL.stackPush()) {
            ByteBuffer pixel = stack.malloc(4);
            pixel.put((byte) red).put((byte) green).put((byte) blue).put((byte) alpha);
            pixel.flip();
            LWJGL.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 1, 1, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
        }
        // Unbound before returning so the constructor leaves nothing bound on the active unit for the caller to trip over
        LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    // For binding as the fallback sampler
    public int getTextureId() {
        return getGlId();
    }

    // Frees the texture
    @Override
    protected void destroyInternal() {
        LWJGL.glDeleteTextures(getGlId());
    }
}
