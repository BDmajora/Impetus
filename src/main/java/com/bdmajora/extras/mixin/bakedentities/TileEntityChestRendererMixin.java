package com.bdmajora.extras.mixin.bakedentities;

import com.bdmajora.extras.client.bakedentities.AnimatedBlockEntity;
import com.bdmajora.extras.client.bakedentities.BakedEntities;
import net.minecraft.client.renderer.tileentity.TileEntityChestRenderer;
import net.minecraft.tileentity.TileEntityChest;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes TileEntityChestRenderer.
@Mixin(TileEntityChestRenderer.class)
public abstract class TileEntityChestRendererMixin {
    // Runs at the start of render and can cancel it.
    @Inject(method = "render(Lnet/minecraft/tileentity/TileEntityChest;DDDFIF)V", at = @At("HEAD"), cancellable = true)
    private void impetus$skipStaticChest(TileEntityChest te, double x, double y, double z, float partialTicks, int destroyStage, float alpha, CallbackInfo ci) {
        if (destroyStage >= 0 || !BakedEntities.chests || !te.hasWorld()) {
            return;
        }
        if (impetus$needsRenderer(te) || impetus$needsRenderer(te.adjacentChestXPos) || impetus$needsRenderer(te.adjacentChestZPos)
                || impetus$needsRenderer(te.adjacentChestXNeg) || impetus$needsRenderer(te.adjacentChestZNeg)) {
            return;
        }
        ci.cancel();
    }

    // Returns whether a chest needs drawing by its renderer rather than as baked terrain.
    private static boolean impetus$needsRenderer(TileEntityChest te) {
        return te != null && ((AnimatedBlockEntity) te).impetus$needsRenderer();
    }
}
