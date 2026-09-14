package com.bdmajora.extras.mixin.bakedentities;

import com.bdmajora.extras.client.bakedentities.ModelBoxAccess;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.TexturedQuad;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

// Exposes the six quads a box holds so the baker can read geometry instead of guessing it from the box bounds
@Mixin(ModelBox.class)
public abstract class ModelBoxAccessorMixin implements ModelBoxAccess {
    @Shadow
    @Final
    private TexturedQuad[] quadList;

    @Override
    public TexturedQuad[] impetus$getQuads() {
        return this.quadList;
    }
}
