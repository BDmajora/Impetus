package com.bdmajora.extras.mixin.bakedentities;

import com.bdmajora.extras.client.bakedentities.AnimatedBlockEntity;
import com.bdmajora.extras.client.bakedentities.BakedEntities;
import net.minecraft.client.renderer.tileentity.TileEntityEnderChestRenderer;
import net.minecraft.tileentity.TileEntityEnderChest;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Steps aside while the ender chest is at rest in the mesh
@Mixin(TileEntityEnderChestRenderer.class)
public abstract class TileEntityEnderChestRendererMixin {
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
