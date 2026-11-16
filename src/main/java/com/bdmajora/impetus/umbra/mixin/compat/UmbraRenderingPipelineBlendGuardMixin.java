package com.bdmajora.impetus.umbra.mixin.compat;

import com.bdmajora.impetus.umbra.gl.blending.BlendOverrideGuard;
import com.bdmajora.impetus.umbra.gl.blending.ProgramAlphaTest;
import com.bdmajora.impetus.umbra.gl.blending.ProgramBlendState;
import com.bdmajora.impetus.umbra.pipeline.UmbraRenderingPipeline;
import com.bdmajora.impetus.umbra.shaderpack.loading.ProgramId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Mixin that changes UmbraRenderingPipeline.
@Mixin(UmbraRenderingPipeline.class)
public abstract class UmbraRenderingPipelineBlendGuardMixin {
    // Runs at the start of beginWorldRendering.
    @Inject(method = "beginWorldRendering(F)V", at = @At("HEAD"))
    private void impetus$releaseAtWorldStart(float partialTicks, CallbackInfo ci) {
        BlendOverrideGuard.release();
    }

    // Runs at the start of setPhase.
    @Inject(method = "setPhase(Lcom/bdmajora/impetus/umbra/shaderpack/loading/ProgramId;)V", at = @At("HEAD"))
    private void impetus$releaseAtPhaseChange(ProgramId phase, CallbackInfo ci) {
        BlendOverrideGuard.release();
    }

    // Runs at the start of onTerrainDraw.
    @Inject(method = "onTerrainDraw([ILcom/bdmajora/impetus/umbra/gl/blending/ProgramBlendState;)V", at = @At("HEAD"))
    private void impetus$releaseAtTerrainDraw(int[] drawBuffers, ProgramBlendState blendState, CallbackInfo ci) {
        BlendOverrideGuard.release();
    }

    // Runs at the start of onTerrainDraw.
    @Inject(
            method = "onTerrainDraw([ILcom/bdmajora/impetus/umbra/gl/blending/ProgramBlendState;Lcom/bdmajora/impetus/umbra/gl/blending/ProgramAlphaTest;Z)V",
            at = @At("HEAD"))
    private void impetus$releaseAtTerrainDrawFull(
            // Draw buffers and blend state.
            int[] drawBuffers, ProgramBlendState blendState, ProgramAlphaTest alphaTest,
            // Flag set for the translucent pass.
            boolean translucentPass, CallbackInfo ci) {
        BlendOverrideGuard.release();
    }

    // Runs at the start of beginTranslucents.
    @Inject(method = "beginTranslucents()V", at = @At("HEAD"))
    private void impetus$releaseAtTranslucents(CallbackInfo ci) {
        BlendOverrideGuard.release();
    }

    // Runs at the start of beginHand.
    @Inject(method = "beginHand()V", at = @At("HEAD"))
    private void impetus$releaseAtHandDepthCopy(CallbackInfo ci) {
        BlendOverrideGuard.release();
    }

    // Runs at the start of beginHandRendering.
    @Inject(method = "beginHandRendering()Z", at = @At("HEAD"))
    private void impetus$releaseAtHandRendering(CallbackInfoReturnable<Boolean> cir) {
        BlendOverrideGuard.release();
    }

    // Runs at the start of beginHandTranslucentRendering.
    @Inject(method = "beginHandTranslucentRendering()Z", at = @At("HEAD"))
    private void impetus$releaseAtHandTranslucentRendering(CallbackInfoReturnable<Boolean> cir) {
        BlendOverrideGuard.release();
    }

    // Runs at the start of endHandRendering.
    @Inject(method = "endHandRendering()V", at = @At("HEAD"))
    private void impetus$releaseAtHandEnd(CallbackInfo ci) {
        BlendOverrideGuard.release();
    }

    // Runs at the start of finishWorldRendering.
    @Inject(method = "finishWorldRendering()V", at = @At("HEAD"))
    private void impetus$releaseAtWorldFinish(CallbackInfo ci) {
        BlendOverrideGuard.release();
    }
}
