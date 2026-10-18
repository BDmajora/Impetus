package com.bdmajora.extras.mixin.zoom;

import com.bdmajora.extras.client.zoom.LogicalZoom;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Logical Zoom's two camera hooks: the zoom state latches at the start of the frame, and the world FOV narrows where vanilla computes it
@Mixin(EntityRenderer.class)
public class EntityRendererZoomMixin {
    @Inject(method = "updateCameraAndRender", at = @At("HEAD"))
    private void impetus$updateZoom(float partialTicks, long nanoTime, CallbackInfo ci) {
        LogicalZoom.update();
    }

    // useFOVSetting is false only for the first-person hand, which keeps its own fixed 70 degrees like the original
    @ModifyReturnValue(method = "getFOVModifier", at = @At("RETURN"))
    private float impetus$zoomFov(float fov, @Local(argsOnly = true) boolean useFOVSetting) {
        return useFOVSetting ? LogicalZoom.zoomFov(fov) : fov;
    }
}
