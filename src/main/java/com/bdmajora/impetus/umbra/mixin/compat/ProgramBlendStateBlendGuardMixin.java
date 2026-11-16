package com.bdmajora.impetus.umbra.mixin.compat;

import com.bdmajora.impetus.umbra.gl.blending.BlendOverrideGuard;
import com.bdmajora.impetus.umbra.gl.blending.ProgramBlendState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes ProgramBlendState.
@Mixin(ProgramBlendState.class)
public abstract class ProgramBlendStateBlendGuardMixin {
    // Reference to the method has directives of the target.
    @Shadow
    public abstract boolean hasDirectives();

    // Runs at the start of apply.
    @Inject(method = "apply([I)V", at = @At("HEAD"))
    private void impetus$beforeBlendApply(int[] drawBuffers, CallbackInfo ci) {
        BlendOverrideGuard.beforeProgramBlendApply(hasDirectives());
    }

    // Runs before every return of apply.
    @Inject(method = "apply([I)V", at = @At("RETURN"))
    private void impetus$afterBlendApply(int[] drawBuffers, CallbackInfo ci) {
        BlendOverrideGuard.afterProgramBlendApply();
    }
}
