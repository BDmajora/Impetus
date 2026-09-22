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

// The hurt flash and creeper charge are the only entity tints vanilla draws with fixed-function combiners, which a bound program drops; published as OptiFine's entityColor uniform instead, and the combiner setup is skipped since setBrightness binds over unit 2, the gbuffer normals sampler
@Mixin(RenderLivingBase.class)
public abstract class RenderLivingBaseEntityColorMixin {
    @Shadow
    protected abstract int getColorMultiplier(EntityLivingBase entity, float lightBrightness, float partialTicks);

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

        // Mirrors vanilla's two early exits: nothing to tint at all, and a hurt flash that this pass does not own.
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

        // Returning true still pairs us with the unsetBrightness call that clears the tint again.
        cir.setReturnValue(true);
    }

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

    // doRender swallows exceptions thrown while rendering an entity, which would skip unsetBrightness and leave every later entity tinted red; the blend factor is zero on normal entities, so this is a float compare
    @Inject(method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V", at = @At("RETURN"))
    private void impetus$clearStrandedEntityColor(EntityLivingBase entity, double x, double y, double z,
                                                  float entityYaw, float partialTicks, CallbackInfo ci) {
        if (CapturedRenderingState.INSTANCE.getEntityColor().w == 0.0f) {
            return;
        }

        CapturedRenderingState.INSTANCE.resetEntityColor();
        Umbra.refreshDynamicUniforms();
    }
}
