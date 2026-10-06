package com.bdmajora.impetus.umbra.gl.image;

import org.lwjgl.opengl.GL15;

import java.util.function.IntSupplier;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// One image uniform's binding for one program; the texture id is a supplier and update() runs at every use, since a render-target image's texture changes on flip and an unrelated draw may have rebound the unit (Iris does the same)
public class ImageBinding {
    private final int imageUnit;
    // Must match the image's declared format; a mismatch here is undefined behaviour rather than a GL error
    private final int internalFormat;
    private final IntSupplier textureID;

    public ImageBinding(int imageUnit, int internalFormat, IntSupplier textureID) {
        this.imageUnit = imageUnit;
        this.internalFormat = internalFormat;
        this.textureID = textureID;
    }

    // glBindImageTexture with the supplier's current texture
    public void update() {
        // Always layered: a layered binding exposes a 3D/array image whole, which every custom image with a depth needs, and is harmless for 2D (Umbra binds layered unconditionally)
        LWJGL.glBindImageTexture(this.imageUnit, this.textureID.getAsInt(), 0, true, 0,
                GL15.GL_READ_WRITE, this.internalFormat);
    }
}
