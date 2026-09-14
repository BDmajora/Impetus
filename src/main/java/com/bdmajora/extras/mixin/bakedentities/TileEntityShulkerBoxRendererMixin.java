package com.bdmajora.extras.mixin.bakedentities;

import com.bdmajora.extras.client.bakedentities.AnimatedBlockEntity;
import com.bdmajora.extras.client.bakedentities.BakedEntities;
import net.minecraft.client.renderer.tileentity.TileEntityShulkerBoxRenderer;
import net.minecraft.tileentity.TileEntityShulkerBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Steps aside while the box is closed in the mesh; the block damage overlay reaches shulker boxes through the model path, so the breaking pass is not special here
@Mixin(TileEntityShulkerBoxRenderer.class)
public abstract class TileEntityShulkerBoxRendererMixin {
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
