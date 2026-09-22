package com.bdmajora.impetus.umbra.targets;

import com.bdmajora.impetus.umbra.gl.GlResource;
import com.bdmajora.impetus.lwjgl.GL11;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;

import com.bdmajora.impetus.umbra.gl.texture.TextureParameters;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// The `noisetex` sampler, OptiFine's generated tiling RGBA noise; packs lean on it for dithering, cloud shapes and wave offsets
public class NoiseTexture extends GlResource {
    // OptiFine's default; a pack overrides it with noiseTextureResolution
    public static final int DEFAULT_RESOLUTION = 256;

    // LINEAR since packs sample at non-integer coordinates and expect blending; REPEAT is what makes a small noise texture tile
    public NoiseTexture(int resolution) {
        setHandle(LWJGL.glGenTextures());
        LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, getGlId());
        TextureParameters.set2D(GL11.GL_LINEAR, GL11.GL_REPEAT);

        // Direct and native-ordered because it goes straight to glTexImage2D; a heap buffer would be copied
        ByteBuffer data = ByteBuffer.allocateDirect(resolution * resolution * 4).order(ByteOrder.nativeOrder());
        // Fixed seed, deliberately: the noise must be identical across pipeline rebuilds or switching packs makes every dither pattern jump
        Random random = new Random(0);
        byte[] pixels = new byte[resolution * resolution * 4];
        random.nextBytes(pixels);
        data.put(pixels);
        data.flip();

        LWJGL.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, resolution, resolution, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, data);
        LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    // For binding as the noisetex sampler
    public int getTextureId() {
        return getGlId();
    }

    // Frees the texture
    @Override
    protected void destroyInternal() {
        LWJGL.glDeleteTextures(getGlId());
    }
}
