package com.bdmajora.extras.mixin.bakedentities;

import com.bdmajora.extras.client.bakedentities.AnimatedBlockEntity;
import com.bdmajora.extras.client.bakedentities.BakedEntities;
import net.minecraft.client.renderer.tileentity.TileEntityShulkerBoxRenderer;
import net.minecraft.tileentity.TileEntityShulkerBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes TileEntityShulkerBoxRenderer.
@Mixin(TileEntityShulkerBoxRenderer.class)
public abstract class TileEntityShulkerBoxRendererMixin {
    // Runs at the start of render and can cancel it.
    @Inject(method = "render(Lnet/minecraft/tileentity/TileEntityShulkerBox;DDDFIF)V", at = @At("HEAD"), cancellable = true)
    private void impetus$skipStaticBox(TileEntityShulkerBox te, double x, double y, double z, float partialTicks, int destroyStage, float alpha, CallbackInfo ci) {
        if (destroyStage >= 0 || !BakedEntities.shulkerBoxes || !te.hasWorld()) {
            return;
        }
        if (!((AnimatedBlockEntity) te).impetus$needsRenderer()) {
            ci.cancel();
        }
    }
}
