package com.bdmajora.extras.mixin.bakedentities;

import com.bdmajora.extras.client.bakedentities.ModelBoxAccess;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.TexturedQuad;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

// Mixin that changes ModelBox.
@Mixin(ModelBox.class)
public abstract class ModelBoxAccessorMixin implements ModelBoxAccess {
    // Reference to the field quad list of the target.
    @Shadow
    @Final
    private TexturedQuad[] quadList;

    // Returns the quads.
    @Override
    public TexturedQuad[] impetus$getQuads() {
        return this.quadList;
    }
}
