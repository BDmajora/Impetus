package com.bdmajora.extras.mixin.panini;

import com.bdmajora.extras.client.PaniniProjection;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes EntityRenderer.
@Mixin(EntityRenderer.class)
public class EntityRendererPaniniMixin {
    // Wraps the call of Project.gluPerspective in setupCameraTransform.
    @WrapOperation(
            method = "setupCameraTransform",
            at = @At(value = "INVOKE",
                    target = "Lorg/lwjgl/util/glu/Project;gluPerspective(FFFF)V",
                    remap = false)
    )
    private void impetus$captureProjection(float fovDegrees, float aspect, float near, float far,
                                           Operation<Void> original) {
        float cotHalfFov = (float) (1.0D / Math.tan(Math.toRadians(fovDegrees) / 2.0D));
        PaniniProjection.captureProjection(cotHalfFov / aspect, cotHalfFov);

        original.call(fovDegrees, aspect, near, far);
    }

    // Runs around the call of RenderGlobal.renderEntityOutlineFramebuffer in updateCameraAndRender.
    @Inject(
            method = "updateCameraAndRender",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/RenderGlobal;renderEntityOutlineFramebuffer()V",
                    shift = At.Shift.AFTER)
    )
    private void impetus$applyPanini(float partialTicks, long nanoTime, CallbackInfo ci) {
        PaniniProjection.render(partialTicks);
    }
}
