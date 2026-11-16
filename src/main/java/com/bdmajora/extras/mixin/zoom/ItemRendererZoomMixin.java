package com.bdmajora.extras.mixin.zoom;

import com.bdmajora.extras.client.zoom.LogicalZoom;
import net.minecraft.client.renderer.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes ItemRenderer.
@Mixin(ItemRenderer.class)
public class ItemRendererZoomMixin {
    // Runs at the start of renderItemInFirstPerson and can cancel it.
    @Inject(method = "renderItemInFirstPerson(F)V", at = @At("HEAD"), cancellable = true)
    private void impetus$hideHandWhenZoomed(float partialTicks, CallbackInfo ci) {
        if (LogicalZoom.hidesHand()) {
            ci.cancel();
        }
    }
}
