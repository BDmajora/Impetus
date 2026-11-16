package com.bdmajora.impetus.umbra.shaderpack.texture;

// Sampler rename applied to one texture stage.
public final class CustomTexturePatch {
    // Sampler name to replace.
    private final String samplerName;
    // Stage the rename applies to.
    private final TextureStage stage;
    // Texture type of the replacement.
    private final String textureType;
    // Replacement sampler name.
    private final String newSamplerName;

    // Creates a custom texture patch from its sampler name and stage and texture type and new sampler name.
    public CustomTexturePatch(String samplerName, TextureStage stage, String textureType, String newSamplerName) {
        this.samplerName = samplerName;
        this.stage = stage;
        this.textureType = textureType;
        this.newSamplerName = newSamplerName;
    }

    // Returns the sampler name.
    public String getSamplerName() {
        return this.samplerName;
    }

    // Returns the stage.
    public TextureStage getStage() {
        return this.stage;
    }

    // Returns the texture type.
    public String getTextureType() {
        return this.textureType;
    }

    // Returns the new sampler name.
    public String getNewSamplerName() {
        return this.newSamplerName;
    }

    // Returns the text of the patch.
    @Override
    public String toString() {
        return this.samplerName + " (" + this.textureType + ", " + this.stage + ") -> " + this.newSamplerName;
    }
}
