package com.bdmajora.extras.mixin.zoom;

import com.bdmajora.extras.client.zoom.LogicalZoom;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes EntityRenderer.
@Mixin(EntityRenderer.class)
public class EntityRendererZoomMixin {
    // Runs at the start of updateCameraAndRender.
    @Inject(method = "updateCameraAndRender", at = @At("HEAD"))
    private void impetus$updateZoom(float partialTicks, long nanoTime, CallbackInfo ci) {
        LogicalZoom.update();
    }

    // Changes the return value of getFOVModifier.
    @ModifyReturnValue(method = "getFOVModifier", at = @At("RETURN"))
    private float impetus$zoomFov(float fov, @Local(argsOnly = true) boolean useFOVSetting) {
        return useFOVSetting ? LogicalZoom.zoomFov(fov) : fov;
    }
}
