package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.gl.blending.ProgramBlendState;
import com.bdmajora.impetus.umbra.gl.framebuffer.UmbraFramebuffer;
import com.bdmajora.impetus.umbra.gl.program.DrawBuffers;
import com.bdmajora.impetus.umbra.gl.program.ProgramUniforms;
import com.bdmajora.impetus.umbra.gl.program.UmbraProgram;
import com.bdmajora.impetus.umbra.gl.sampler.ShadowSamplerKinds;

import java.util.BitSet;
import java.util.List;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// One entry of a numbered pass family: a fullscreen draw into its own framebuffer, the final pass (framebuffer == null), or compute-only (program == null) for a slot with only .csh files; its computes dispatch before its draw under the same flip state, like Umbra
public final class FullscreenPass {
    final String name;
    final UmbraProgram program;
    final ProgramUniforms uniforms;
    final UmbraFramebuffer framebuffer;
    // Per colour buffer, the texture to bind as colortexN when this pass runs (0 = target not in use)
    final int[] colorSamplers;
    // Logical render targets in shader output-slot order, used for per-target blend directives
    final int[] drawBuffers;
    final ProgramBlendState blendState;
    final BitSet flipsBefore;
    final BitSet mipmappedBuffers;
    // Compute stages dispatched before this pass draws; never null, usually empty
    final List<ComputePass> computes;
    // How the program declared shadowtex0/1, applied to the shadow units after it binds
    final ShadowSamplerKinds shadowSamplerKinds;
    // A modern (#version 130+ compatibility) program positions the quad through ftransform()/gl_TextureMatrix[0], so only it needs the fixed-function matrices pushed; the 330-core rewrite has its own
    final boolean modern;
    // The size of the buffers this pass writes (size.buffer.colortexN); zero means the current render size
    private final int viewportWidth;
    private final int viewportHeight;
    // scale.<program> (Umbra's ViewportData): shrinks the rasterised rectangle inside whatever size the pass has
    private final float viewportScale;
    private final float viewportOffsetX;
    private final float viewportOffsetY;

    FullscreenPass(String name, UmbraProgram program, ProgramUniforms uniforms, UmbraFramebuffer framebuffer,
                   int[] colorSamplers, int[] drawBuffers, ProgramBlendState blendState, BitSet flipsBefore,
                   BitSet mipmappedBuffers, List<ComputePass> computes, ShadowSamplerKinds shadowSamplerKinds,
                   boolean modern, int[] viewportSize, float[] viewportScale) {
        this.name = name;
        this.program = program;
        this.uniforms = uniforms;
        this.framebuffer = framebuffer;
        this.colorSamplers = colorSamplers;
        this.drawBuffers = drawBuffers;
        this.blendState = blendState;
        this.flipsBefore = flipsBefore;
        this.mipmappedBuffers = mipmappedBuffers;
        this.computes = computes;
        this.shadowSamplerKinds = shadowSamplerKinds;
        this.modern = modern;
        this.viewportWidth = viewportSize[0];
        this.viewportHeight = viewportSize[1];
        this.viewportScale = viewportScale == null ? 1.0f : viewportScale[0];
        this.viewportOffsetX = viewportScale == null ? 0.0f : viewportScale[1];
        this.viewportOffsetY = viewportScale == null ? 0.0f : viewportScale[2];
    }

    // Umbra's ComputeOnlyPass: computes but no vertex/fragment pair; draws nothing, but still needs the flip state and colortex snapshot of its position in the chain
    static FullscreenPass computeOnly(String name, int[] colorSamplers, BitSet flips, List<ComputePass> computes) {
        return new FullscreenPass(name, null, null, null, colorSamplers, DrawBuffers.DEFAULT.clone(), null, flips,
                new BitSet(), computes, ShadowSamplerKinds.ALL_COMPARE, false, new int[2], null);
    }

    // Sets the viewport for a pass into its own framebuffer: an explicitly sized buffer draws at its own resolution, then scale.<program> as Umbra does it (offsets are fractions of the pass size, the scaled extent truncated); a negative scale cannot become a negative glViewport, which GL rejects and so left the last pass's viewport in place
    void applyViewport(int renderWidth, int renderHeight) {
        int width = this.viewportWidth > 0 ? this.viewportWidth : renderWidth;
        int height = this.viewportHeight > 0 ? this.viewportHeight : renderHeight;
        LWJGL.glViewport((int) (width * this.viewportOffsetX), (int) (height * this.viewportOffsetY),
                Math.max(0, (int) (width * this.viewportScale)), Math.max(0, (int) (height * this.viewportScale)));
    }

    // Frees the computes this pass owns; the draw program belongs to the program cache
    void destroyComputes() {
        for (ComputePass compute : this.computes) {
            compute.destroy();
        }
    }
}
