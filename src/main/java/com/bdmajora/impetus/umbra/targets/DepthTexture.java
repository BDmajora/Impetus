package com.bdmajora.impetus.umbra.targets;

import com.bdmajora.impetus.umbra.gl.GlResource;
import com.bdmajora.impetus.lwjgl.GL11;
import com.bdmajora.impetus.lwjgl.GL12;

import java.nio.ByteBuffer;

import com.bdmajora.impetus.umbra.gl.texture.TextureParameters;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// A depth texture programs sample (depthtex0/1/2, shadowtex0/1); several because OptiFine snapshots depth at different points (0 everything, 1 excludes translucents, 2 also excludes the hand) and packs subtract them to find water and hand pixels
public class DepthTexture extends GlResource {
    private static final ByteBuffer NULL_BUFFER = null;

    private final int internalFormat;
    private final int pixelFormat;
    private final int pixelType;
    private int width;
    private int height;

    public DepthTexture(int width, int height, int internalFormat, int pixelFormat, int pixelType) {
        this.internalFormat = internalFormat;
        this.pixelFormat = pixelFormat;
        this.pixelType = pixelType;
        this.width = width;
        this.height = height;

        setHandle(LWJGL.glGenTextures());
        allocate();
    }

    // Creates the storage at the current size
    private void allocate() {
        LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, getGlId());
        TextureParameters.set2D(GL11.GL_NEAREST, GL12.GL_CLAMP_TO_EDGE);
        LWJGL.glTexImage2D(GL11.GL_TEXTURE_2D, 0, this.internalFormat, this.width, this.height, 0,
                this.pixelFormat, this.pixelType, NULL_BUFFER);
        LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    // Reallocates; contents are lost
    public void resize(int newWidth, int newHeight) {
        this.width = newWidth;
        this.height = newHeight;
        allocate();
    }

    // For binding as a sampler
    public int getTextureId() {
        return getGlId();
    }

    // Frees the texture
    @Override
    protected void destroyInternal() {
        LWJGL.glDeleteTextures(getGlId());
    }
}
