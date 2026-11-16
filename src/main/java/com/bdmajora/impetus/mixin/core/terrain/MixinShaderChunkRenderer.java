package com.bdmajora.impetus.mixin.core.terrain;

import com.bdmajora.impetus.engine.impl.gl.shader.GlProgram;
import com.bdmajora.impetus.engine.impl.render.chunk.ShaderChunkRenderer;
import com.bdmajora.impetus.engine.impl.render.chunk.shader.ChunkShaderInterface;
import com.bdmajora.impetus.engine.impl.render.chunk.shader.ChunkShaderOptions;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.bdmajora.impetus.umbra.terrain.UmbraTerrainProgramOverride;

import java.util.Map;

// Mixin that changes ShaderChunkRenderer.
@Mixin(ShaderChunkRenderer.class)
public class MixinShaderChunkRenderer {

    @Shadow
    @Final
    private Map<ChunkShaderOptions, GlProgram<ChunkShaderInterface>> programs;

    // Runs at the start of compileProgram and can cancel it.
    @Inject(method = "compileProgram", at = @At("HEAD"), cancellable = true)
    private void impetus$overrideTerrainProgram(ChunkShaderOptions options,
                                                  CallbackInfoReturnable<GlProgram<ChunkShaderInterface>> cir) {
        if (!UmbraTerrainProgramOverride.areShadersActive()) {
            return;
        }
        if (com.bdmajora.impetus.umbra.pipeline.UmbraShadowRenderer.isShadowPass()) {
            cir.setReturnValue(UmbraTerrainProgramOverride.getShadowProgramOverride(options));
            return;
        }
        if (this.programs.containsKey(options)) {
            cir.setReturnValue(this.programs.get(options));
            return;
        }
        GlProgram<ChunkShaderInterface> override = UmbraTerrainProgramOverride.getProgramOverride(options);
        if (override != null) {
            this.programs.put(options, override);
            cir.setReturnValue(override);
        }
    }
}
