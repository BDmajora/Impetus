package com.bdmajora.impetus.umbra.targets;

import com.bdmajora.impetus.umbra.gl.GlResource;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;

import com.bdmajora.impetus.umbra.gl.texture.TextureParameters;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Random noise texture for packs without their own.
public class NoiseTexture extends GlResource {
    // Size used without a pack value.
    public static final int DEFAULT_RESOLUTION = 256;

    // Creates a noise texture from its resolution.
    public NoiseTexture(int resolution) {
        setHandle(LWJGL.glGenTextures());
        LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, getGlId());
        TextureParameters.set2D(GL11.GL_LINEAR, GL11.GL_REPEAT);

        ByteBuffer data = ByteBuffer.allocateDirect(resolution * resolution * 4).order(ByteOrder.nativeOrder());
        Random random = new Random(0);
        byte[] pixels = new byte[resolution * resolution * 4];
        random.nextBytes(pixels);
        data.put(pixels);
        data.flip();

        LWJGL.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, resolution, resolution, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, data);
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
