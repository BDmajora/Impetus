package com.bdmajora.impetus.umbra.shaderpack.texture;

// Blur and clamp flags of a texture.
public final class TextureFilteringData {
    // Flag of linear filtering.
    private final boolean blur;
    // Flag of edge clamping.
    private final boolean clamp;

    // Creates a texture filtering data from its blur and clamp.
    public TextureFilteringData(boolean blur, boolean clamp) {
        this.blur = blur;
        this.clamp = clamp;
    }

    // Returns whether blur.
    public boolean shouldBlur() {
        return this.blur;
    }

    // Returns whether clamp.
    public boolean shouldClamp() {
        return this.clamp;
    }

    // Returns the text of the flags.
    @Override
    public String toString() {
        return "TextureFilteringData{blur=" + this.blur + ", clamp=" + this.clamp + "}";
    }
}
