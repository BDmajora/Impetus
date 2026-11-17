package com.bdmajora.extras.mixin.bakedentities;

import com.bdmajora.extras.client.bakedentities.BakedEntities;
import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.model.ModelSign;
import net.minecraft.client.renderer.tileentity.TileEntitySignRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Mixin that changes TileEntitySignRenderer.
@Mixin(TileEntitySignRenderer.class)
public abstract class TileEntitySignRendererMixin {
    // Skips the board draw while the sign is baked and not breaking.
    @WrapWithCondition(method = "render(Lnet/minecraft/tileentity/TileEntitySign;DDDFIF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/ModelSign;renderSign()V"))
    private boolean impetus$skipStaticBoard(ModelSign model, @Local(argsOnly = true, ordinal = 0) int destroyStage) {
        return destroyStage >= 0 || !BakedEntities.signs;
    }
}
