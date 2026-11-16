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

// One composite pass with its program and framebuffer and the computes that run before it.
public final class FullscreenPass {
    // Name of the program.
    final String name;
    // Raster program or null for a compute only pass.
    final UmbraProgram program;
    // Uniforms of the program or null.
    final ProgramUniforms uniforms;
    // Framebuffer the pass draws into or null.
    final UmbraFramebuffer framebuffer;
    // Textures sampled as each color target.
    final int[] colorSamplers;
    // Draw buffers of the program.
    final int[] drawBuffers;
    // Blend override of the program or null.
    final ProgramBlendState blendState;
    // Flip state before the pass.
    final BitSet flipsBefore;
    // Targets mipmapped before the pass.
    final BitSet mipmappedBuffers;
    // Compute programs that run before the raster program.
    final List<ComputePass> computes;
    // Compare or raw kind of each shadow sampler.
    final ShadowSamplerKinds shadowSamplerKinds;
    // Core profile state of the program.
    final boolean modern;
    // Fixed viewport width or zero for the render width.
    private final int viewportWidth;
    // Fixed viewport height or zero for the render height.
    private final int viewportHeight;
    // Scale of the viewport.
    private final float viewportScale;
    // Horizontal offset of the viewport as a share of the width.
    private final float viewportOffsetX;
    // Vertical offset of the viewport as a share of the height.
    private final float viewportOffsetY;

    // Creates a pass.
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

    // Creates a pass that only runs compute programs.
    static FullscreenPass computeOnly(String name, int[] colorSamplers, BitSet flips, List<ComputePass> computes) {
        return new FullscreenPass(name, null, null, null, colorSamplers, DrawBuffers.DEFAULT.clone(), null, flips,
                new BitSet(), computes, ShadowSamplerKinds.ALL_COMPARE, false, new int[2], null);
    }

    // Sets the scaled and offset viewport of the pass.
    void applyViewport(int renderWidth, int renderHeight) {
        int width = this.viewportWidth > 0 ? this.viewportWidth : renderWidth;
        int height = this.viewportHeight > 0 ? this.viewportHeight : renderHeight;
        LWJGL.glViewport((int) (width * this.viewportOffsetX), (int) (height * this.viewportOffsetY),
                Math.max(0, (int) (width * this.viewportScale)), Math.max(0, (int) (height * this.viewportScale)));
    }

    // Destroys the compute programs of the pass.
    void destroyComputes() {
        for (ComputePass compute : this.computes) {
            compute.destroy();
        }
    }
}
