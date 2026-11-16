package com.bdmajora.impetus.umbra.gl.image;

import org.lwjgl.opengl.GL15;

import java.util.function.IntSupplier;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Binding of a texture to an image unit.
public class ImageBinding {
    // Image unit of the binding.
    private final int imageUnit;
    // Format of the image.
    private final int internalFormat;
    // Supplier of the texture name.
    private final IntSupplier textureID;

    // Creates a binding and rejects a null texture supplier.
    public ImageBinding(int imageUnit, int internalFormat, IntSupplier textureID) {
        this.imageUnit = imageUnit;
        this.internalFormat = internalFormat;
        this.textureID = java.util.Objects.requireNonNull(textureID, "textureID");
    }

    // Binds the current texture to the image unit.
    public void update() {
        LWJGL.glBindImageTexture(this.imageUnit, this.textureID.getAsInt(), 0, true, 0,
                GL15.GL_READ_WRITE, this.internalFormat);
    }
}
