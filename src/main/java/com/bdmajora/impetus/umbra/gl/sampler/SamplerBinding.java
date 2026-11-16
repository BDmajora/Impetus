package com.bdmajora.impetus.umbra.gl.sampler;

import com.bdmajora.impetus.umbra.gl.GlTextureUnits;

import java.util.function.IntSupplier;

// Binding of a texture to a sampler unit.
public final class SamplerBinding {
    // Texture unit of the binding.
    private final int textureUnit;
    // Texture target of the binding.
    private final int textureTarget;
    // Supplier of the texture name.
    private final IntSupplier texture;

    // Creates a sampler binding from its texture unit and texture target and texture.
    public SamplerBinding(int textureUnit, int textureTarget, IntSupplier texture) {
        this.textureUnit = textureUnit;
        this.textureTarget = textureTarget;
        this.texture = texture;
    }

    // Binds the current texture to the unit.
    public void update() {
        GlTextureUnits.bindTextureInRun(this.textureUnit, this.textureTarget, this.texture.getAsInt());
    }

    // Returns the texture unit.
    public int getTextureUnit() {
        return this.textureUnit;
    }
}
