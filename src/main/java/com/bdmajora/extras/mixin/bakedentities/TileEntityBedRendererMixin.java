package com.bdmajora.extras.mixin.bakedentities;

import com.bdmajora.extras.client.bakedentities.BakedEntities;
import net.minecraft.client.renderer.tileentity.TileEntityBedRenderer;
import net.minecraft.tileentity.TileEntityBed;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Beds never animate, so a bed in a world is always the mesh's; the world-less call (item rendering) keeps the renderer
@Mixin(TileEntityBedRenderer.class)
public abstract class TileEntityBedRendererMixin {
    @Inject(method = "render(Lnet/minecraft/tileentity/TileEntityBed;DDDFIF)V", at = @At("HEAD"), cancellable = true)
    private void impetus$skipStaticBed(TileEntityBed te, double x, double y, double z, float partialTicks, int destroyStage, float alpha, CallbackInfo ci) {
        if (destroyStage >= 0 || !BakedEntities.beds || te == null || te.getWorld() == null) {
            return;
        }
        ci.cancel();
    }
}
