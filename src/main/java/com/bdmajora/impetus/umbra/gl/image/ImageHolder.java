package com.bdmajora.impetus.umbra.gl.image;

import java.util.function.IntSupplier;

// Contract of the image holder.
public interface ImageHolder {
    // Returns whether an image name is declared.
    boolean hasImage(String name);

    // Binds a texture to the image of a name.
    void addTextureImage(IntSupplier textureID, int internalFormat, String name);
}
