package com.bdmajora.extras.mixin.bakedentities;

import com.bdmajora.extras.client.bakedentities.AnimatedBlockEntity;
import com.bdmajora.extras.client.bakedentities.BakedEntities;
import net.minecraft.client.renderer.tileentity.TileEntityEnderChestRenderer;
import net.minecraft.tileentity.TileEntityEnderChest;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes TileEntityEnderChestRenderer.
@Mixin(TileEntityEnderChestRenderer.class)
public abstract class TileEntityEnderChestRendererMixin {
    // Runs at the start of render and can cancel it.
    @Inject(method = "render(Lnet/minecraft/tileentity/TileEntityEnderChest;DDDFIF)V", at = @At("HEAD"), cancellable = true)
    private void impetus$skipStaticChest(TileEntityEnderChest te, double x, double y, double z, float partialTicks, int destroyStage, float alpha, CallbackInfo ci) {
        if (destroyStage >= 0 || !BakedEntities.enderChests || !te.hasWorld()) {
            return;
        }
        if (!((AnimatedBlockEntity) te).impetus$needsRenderer()) {
            ci.cancel();
        }
    }
}
