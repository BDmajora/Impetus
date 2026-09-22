package com.bdmajora.impetus.umbra.targets;

import com.bdmajora.impetus.umbra.gl.texture.InternalTextureFormat;
import com.bdmajora.impetus.lwjgl.GL11;
import com.bdmajora.impetus.lwjgl.GL12;

import java.nio.ByteBuffer;

import com.bdmajora.impetus.umbra.gl.texture.TextureParameters;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// One pack colour buffer (colortexN, gcolor in older packs) owning TWO GL textures like OptiFine's dfbColorTexturesA/B, since a pass must read the previous output while writing its own; pass N samples main and renders into alt, then BufferFlipper swaps
public class UmbraRenderTarget {
    private static final ByteBuffer NULL_BUFFER = null;
    private static final int GL_LINEAR_MIPMAP_LINEAR = 0x2703;

    private final InternalTextureFormat internalFormat;
    private final int mainTexture;
    private final int altTexture;
    private final boolean linear;
    private int width;
    private int height;
    private boolean valid = true;
    private boolean mainMipmapped;
    private boolean altMipmapped;

    public UmbraRenderTarget(InternalTextureFormat internalFormat, int width, int height) {
        this.internalFormat = internalFormat;
        this.width = width;
        this.height = height;

        this.mainTexture = LWJGL.glGenTextures();
        this.altTexture = LWJGL.glGenTextures();

        this.linear = !internalFormat.isInteger();
        setupTexture(this.mainTexture, width, height, this.linear);
        setupTexture(this.altTexture, width, height, this.linear);

        LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    // Allocates storage and sets the sampler state for one of the two textures
    private void setupTexture(int texture, int width, int height, boolean linear) {
        int filter = linear ? GL11.GL_LINEAR : GL11.GL_NEAREST;
        LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        TextureParameters.set2D(filter, GL12.GL_CLAMP_TO_EDGE);
        LWJGL.glTexImage2D(GL11.GL_TEXTURE_2D, 0, this.internalFormat.getInternalFormat(), width, height, 0,
                this.internalFormat.getPixelFormat(), this.internalFormat.getPixelType(), NULL_BUFFER);
    }

    // Reallocates both textures; contents are lost
    public void resize(int newWidth, int newHeight) {
        requireValid();
        this.width = newWidth;
        this.height = newHeight;
        boolean linear = !this.internalFormat.isInteger();
        setupTexture(this.mainTexture, newWidth, newHeight, linear);
        setupTexture(this.altTexture, newWidth, newHeight, linear);
        this.mainMipmapped = false;
        this.altMipmapped = false;
        LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    // Builds the mip chain for whichever texture was just rendered to
    public void generateMipmaps(boolean alt) {
        requireValid();
        if (!this.linear) {
            return;
        }
        int texture = alt ? this.altTexture : this.mainTexture;
        LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        LWJGL.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        LWJGL.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        LWJGL.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        turnOnMips(alt);
    }

    // Switches the sampler to a mipmapped filter
    public void turnOnMips(boolean alt) {
        if (alt) {
            this.altMipmapped = true;
        } else {
            this.mainMipmapped = true;
        }
    }

    // Returns both textures to the non-mipmapped filter
    public void resetMipmaps() {
        requireValid();
        if (this.mainMipmapped) {
            resetMipmapState(this.mainTexture);
            turnOffMips(false);
        }
        if (this.altMipmapped) {
            resetMipmapState(this.altTexture);
            turnOffMips(true);
        }
    }

    // Switches one texture back to the non-mipmapped filter
    public void turnOffMips(boolean alt) {
        if (alt) {
            this.altMipmapped = false;
        } else {
            this.mainMipmapped = false;
        }
    }

    // The filter reset itself
    private void resetMipmapState(int texture) {
        int filter = this.linear ? GL11.GL_LINEAR : GL11.GL_NEAREST;
        LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        TextureParameters.setFilter2D(filter);
    }

    // The format the pack requested for this buffer
    public InternalTextureFormat getInternalFormat() {
        return this.internalFormat;
    }

    // The texture currently being read
    public int getMainTexture() {
        requireValid();
        return this.mainTexture;
    }

    // The texture currently being written
    public int getAltTexture() {
        requireValid();
        return this.altTexture;
    }

    // Current width
    public int getWidth() {
        return this.width;
    }

    // Current height
    public int getHeight() {
        return this.height;
    }

    // Frees both textures
    public void destroy() {
        requireValid();
        this.valid = false;
        LWJGL.glDeleteTextures(this.mainTexture);
        LWJGL.glDeleteTextures(this.altTexture);
    }

    // Throws if used after destroy
    private void requireValid() {
        if (!this.valid) {
            throw new IllegalStateException("Tried to use a destroyed UmbraRenderTarget");
        }
    }
}
