package com.bdmajora.impetus.mixin.core.shader;

import com.bdmajora.impetus.umbra.Umbra;
import com.bdmajora.impetus.umbra.pipeline.UmbraRenderingPipeline;
import com.bdmajora.impetus.umbra.uniforms.CapturedRenderingState;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Mixin that changes RenderLivingBase.
@Mixin(RenderLivingBase.class)
public abstract class RenderLivingBaseEntityColorMixin {
    // Reference to the method get color multiplier of the target.
    @Shadow
    protected abstract int getColorMultiplier(EntityLivingBase entity, float lightBrightness, float partialTicks);

    // Runs at the start of setBrightness and can cancel it.
    @Inject(method = "setBrightness(Lnet/minecraft/entity/EntityLivingBase;FZ)Z", at = @At("HEAD"), cancellable = true)
    private void impetus$captureEntityColor(EntityLivingBase entity, float partialTicks, boolean combineTextures,
                                            CallbackInfoReturnable<Boolean> cir) {
        UmbraRenderingPipeline pipeline = Umbra.getRenderingPipeline();
        if (pipeline == null) {
            return;
        }

        int multiplier = this.getColorMultiplier(entity, entity.getBrightness(), partialTicks);
        boolean hasMultiplier = (multiplier >> 24 & 255) > 0;
        boolean hurt = entity.hurtTime > 0 || entity.deathTime > 0;

        if (!hasMultiplier && (!hurt || !combineTextures)) {
            cir.setReturnValue(false);
            return;
        }

        if (hurt) {
            CapturedRenderingState.INSTANCE.setEntityColor(1.0f, 0.0f, 0.0f, 0.3f);
        } else {
            CapturedRenderingState.INSTANCE.setEntityColor(
                    (float) (multiplier >> 16 & 255) / 255.0f,
                    (float) (multiplier >> 8 & 255) / 255.0f,
                    (float) (multiplier & 255) / 255.0f,
                    1.0f - (float) (multiplier >> 24 & 255) / 255.0f);
        }
        pipeline.refreshDynamicUniforms();

        cir.setReturnValue(true);
    }

    // Runs at the start of unsetBrightness and can cancel it.
    @Inject(method = "unsetBrightness", at = @At("HEAD"), cancellable = true)
    private void impetus$clearEntityColor(CallbackInfo ci) {
        UmbraRenderingPipeline pipeline = Umbra.getRenderingPipeline();
        if (pipeline == null) {
            return;
        }

        CapturedRenderingState.INSTANCE.resetEntityColor();
        pipeline.refreshDynamicUniforms();
        ci.cancel();
    }

    // Runs before every return of doRender.
    @Inject(method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V", at = @At("RETURN"))
    private void impetus$clearStrandedEntityColor(EntityLivingBase entity, double x, double y, double z,
                                                  // Yaw and partial ticks.
                                                  float entityYaw, float partialTicks, CallbackInfo ci) {
        if (CapturedRenderingState.INSTANCE.getEntityColor().w == 0.0f) {
            return;
        }

        CapturedRenderingState.INSTANCE.resetEntityColor();
        Umbra.refreshDynamicUniforms();
    }
}
