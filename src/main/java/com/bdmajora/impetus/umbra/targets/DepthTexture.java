package com.bdmajora.impetus.umbra.targets;

import com.bdmajora.impetus.umbra.gl.GlResource;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.nio.ByteBuffer;

import com.bdmajora.impetus.umbra.gl.texture.TextureParameters;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Depth texture of a render target set.
public class DepthTexture extends GlResource {
    // Null pixel data for allocation.
    private static final ByteBuffer NULL_BUFFER = null;

    // Internal format.
    private final int internalFormat;
    // Pixel format.
    private final int pixelFormat;
    // Pixel type.
    private final int pixelType;
    // Width in pixels.
    private int width;
    // Height in pixels.
    private int height;

    // Creates a depth texture from its width and height and internal format and pixel format and pixel type.
    public DepthTexture(int width, int height, int internalFormat, int pixelFormat, int pixelType) {
        this.internalFormat = internalFormat;
        this.pixelFormat = pixelFormat;
        this.pixelType = pixelType;
        this.width = width;
        this.height = height;

        setHandle(LWJGL.glGenTextures());
        allocate();
    }

    // Allocates the texture storage.
    private void allocate() {
        LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, getGlId());
        TextureParameters.set2D(GL11.GL_NEAREST, GL12.GL_CLAMP_TO_EDGE);
        LWJGL.glTexImage2D(GL11.GL_TEXTURE_2D, 0, this.internalFormat, this.width, this.height, 0,
                this.pixelFormat, this.pixelType, NULL_BUFFER);
        LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    // Reallocates the texture at a new size.
    public void resize(int newWidth, int newHeight) {
        this.width = newWidth;
        this.height = newHeight;
        allocate();
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
