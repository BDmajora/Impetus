package com.bdmajora.extras.mixin.zoom;

import com.bdmajora.extras.client.zoom.LogicalZoom;
import net.minecraft.client.renderer.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Hides the hands and held items while zoomed; hooked on the item renderer rather than EntityRenderer.renderHand since Umbra's shader hand pass calls it directly, and both callers only reach it in first person
@Mixin(ItemRenderer.class)
public class ItemRendererZoomMixin {
    @Inject(method = "renderItemInFirstPerson(F)V", at = @At("HEAD"), cancellable = true)
    private void impetus$hideHandWhenZoomed(float partialTicks, CallbackInfo ci) {
        if (LogicalZoom.hidesHand()) {
            ci.cancel();
        }
    }
}
