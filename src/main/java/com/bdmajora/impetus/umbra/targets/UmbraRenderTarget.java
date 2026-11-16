package com.bdmajora.impetus.umbra.targets;

import com.bdmajora.impetus.umbra.gl.texture.InternalTextureFormat;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.nio.ByteBuffer;

import com.bdmajora.impetus.umbra.gl.texture.TextureParameters;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Color target of a shader pack with a main and an alternate texture.
public class UmbraRenderTarget {
    // Empty pixel source of an allocation.
    private static final ByteBuffer NULL_BUFFER = null;
    // GL enum of trilinear filtering.
    private static final int GL_LINEAR_MIPMAP_LINEAR = 0x2703;

    // Format of both textures.
    private final InternalTextureFormat internalFormat;
    // GL name of the main texture.
    private final int mainTexture;
    // GL name of the alternate texture.
    private final int altTexture;
    // Linear filtering state of the format.
    private final boolean linear;
    // Width in pixels.
    private int width;
    // Height in pixels.
    private int height;
    // Live state of the textures.
    private boolean valid = true;
    // Mipmapped state of the main texture.
    private boolean mainMipmapped;
    // Mipmapped state of the alternate texture.
    private boolean altMipmapped;

    // Creates both textures and deletes them when one fails.
    public UmbraRenderTarget(InternalTextureFormat internalFormat, int width, int height) {
        this.internalFormat = internalFormat;
        this.width = width;
        this.height = height;

        this.mainTexture = LWJGL.glGenTextures();
        this.altTexture = LWJGL.glGenTextures();

        this.linear = !internalFormat.isInteger();
        // Deletes both textures when their setup fails
        try {
            setupTexture(this.mainTexture, width, height, this.linear);
            setupTexture(this.altTexture, width, height, this.linear);
        } catch (RuntimeException | Error e) {
            LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            LWJGL.glDeleteTextures(this.mainTexture);
            LWJGL.glDeleteTextures(this.altTexture);
            throw e;
        }

        LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    // Sets the filter and wrap and allocates the storage of a texture.
    private void setupTexture(int texture, int width, int height, boolean linear) {
        int filter = linear ? GL11.GL_LINEAR : GL11.GL_NEAREST;
        LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        TextureParameters.set2D(filter, GL12.GL_CLAMP_TO_EDGE);
        LWJGL.glTexImage2D(GL11.GL_TEXTURE_2D, 0, this.internalFormat.getInternalFormat(), width, height, 0,
                this.internalFormat.getPixelFormat(), this.internalFormat.getPixelType(), NULL_BUFFER);
    }

    // Reallocates both textures at a new size and forgets their mipmaps.
    public void resize(int newWidth, int newHeight) {
        requireValid();
        this.width = newWidth;
        this.height = newHeight;
        setupTexture(this.mainTexture, newWidth, newHeight, this.linear);
        setupTexture(this.altTexture, newWidth, newHeight, this.linear);
        this.mainMipmapped = false;
        this.altMipmapped = false;
        LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    // Generates the mipmaps of a texture of a linear format.
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

    // Marks a texture as mipmapped.
    public void turnOnMips(boolean alt) {
        if (alt) {
            this.altMipmapped = true;
        } else {
            this.mainMipmapped = true;
        }
    }

    // Restores the plain filter of every mipmapped texture.
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

    // Marks a texture as not mipmapped.
    public void turnOffMips(boolean alt) {
        if (alt) {
            this.altMipmapped = false;
        } else {
            this.mainMipmapped = false;
        }
    }

    // Restores the plain filter of a texture.
    private void resetMipmapState(int texture) {
        int filter = this.linear ? GL11.GL_LINEAR : GL11.GL_NEAREST;
        LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        TextureParameters.setFilter2D(filter);
    }

    // Returns the format of the textures.
    public InternalTextureFormat getInternalFormat() {
        return this.internalFormat;
    }

    // Returns the GL name of the main texture.
    public int getMainTexture() {
        requireValid();
        return this.mainTexture;
    }

    // Returns the GL name of the alternate texture.
    public int getAltTexture() {
        requireValid();
        return this.altTexture;
    }

    // Returns the width in pixels.
    public int getWidth() {
        return this.width;
    }

    // Returns the height in pixels.
    public int getHeight() {
        return this.height;
    }

    // Deletes both textures once and rejects a second call.
    public void destroy() {
        requireValid();
        this.valid = false;
        try {
            LWJGL.glDeleteTextures(this.mainTexture);
        } finally {
            LWJGL.glDeleteTextures(this.altTexture);
        }
    }

    // Rejects a destroyed target.
    private void requireValid() {
        if (!this.valid) {
            throw new IllegalStateException("Tried to use a destroyed UmbraRenderTarget");
        }
    }
}
