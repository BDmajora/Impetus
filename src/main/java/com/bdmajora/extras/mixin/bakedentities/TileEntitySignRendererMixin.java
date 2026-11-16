package com.bdmajora.extras.mixin.bakedentities;

import com.bdmajora.extras.client.bakedentities.BakedEntities;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.model.ModelSign;
import net.minecraft.client.renderer.tileentity.TileEntitySignRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Mixin that changes TileEntitySignRenderer.
@Mixin(TileEntitySignRenderer.class)
public abstract class TileEntitySignRendererMixin {
    // Wraps the call of ModelSign.renderSign in render.
    @WrapOperation(method = "render(Lnet/minecraft/tileentity/TileEntitySign;DDDFIF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/ModelSign;renderSign()V"))
    private void impetus$skipStaticBoard(ModelSign model, Operation<Void> original, @Local(argsOnly = true, ordinal = 0) int destroyStage) {
        if (destroyStage < 0 && BakedEntities.signs) {
            return;
        }
        original.call(model);
    }
}
