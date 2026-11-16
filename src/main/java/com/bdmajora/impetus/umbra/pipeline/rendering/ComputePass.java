package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.gl.program.GlProgram;
import com.bdmajora.impetus.umbra.gl.program.ProgramUniforms;
import com.bdmajora.impetus.umbra.gl.sampler.ShadowSamplerKinds;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Compute program of a pass with its fixed or screen relative or indirect work group count.
public final class ComputePass {
    // GL enum of the indirect dispatch target.
    private static final int GL_DISPATCH_INDIRECT_BUFFER = 0x90EE;

    // Compute program.
    final GlProgram program;
    // Uniforms of the program.
    final ProgramUniforms uniforms;
    // Compare or raw kind of each shadow sampler.
    final ShadowSamplerKinds shadowSamplerKinds;
    // Fixed work groups along x.
    private final int groupsX;
    // Fixed work groups along y.
    private final int groupsY;
    // Fixed work groups along z.
    private final int groupsZ;
    // Screen scale along x or NaN for fixed groups.
    private final float renderScaleX;
    // Screen scale along y or NaN for fixed groups.
    private final float renderScaleY;
    // Local size of the program along x.
    private final int localSizeX;
    // Local size of the program along y.
    private final int localSizeY;
    // GL name of the indirect buffer or minus one.
    private final int indirectBuffer;
    // Byte offset of the indirect command.
    private final long indirectOffset;

    // Creates a pass.
    ComputePass(GlProgram program, ProgramUniforms uniforms, ShadowSamplerKinds shadowSamplerKinds, int[] groups,
                float[] renderScale, int[] localSize, int indirectBuffer, long indirectOffset) {
        this.program = program;
        this.uniforms = uniforms;
        this.shadowSamplerKinds = shadowSamplerKinds;
        this.groupsX = groups[0];
        this.groupsY = groups[1];
        this.groupsZ = groups[2];
        this.renderScaleX = renderScale != null ? renderScale[0] : Float.NaN;
        this.renderScaleY = renderScale != null ? renderScale[1] : Float.NaN;
        this.localSizeX = localSize != null ? localSize[0] : 1;
        this.localSizeY = localSize != null ? localSize[1] : 1;
        this.indirectBuffer = indirectBuffer;
        this.indirectOffset = indirectOffset;
    }

    // Dispatches the program with its indirect or screen relative or fixed group count.
    void dispatch(int renderWidth, int renderHeight) {
        if (this.indirectBuffer != -1) {
            LWJGL.glBindBuffer(GL_DISPATCH_INDIRECT_BUFFER, this.indirectBuffer);
            LWJGL.glDispatchComputeIndirect(this.indirectOffset);
            LWJGL.glBindBuffer(GL_DISPATCH_INDIRECT_BUFFER, 0);
        } else if (!Float.isNaN(this.renderScaleX)) {
            LWJGL.glDispatchCompute(ComputeDirectives.screenGroups(renderWidth, this.renderScaleX, this.localSizeX),
                    ComputeDirectives.screenGroups(renderHeight, this.renderScaleY, this.localSizeY), 1);
        } else {
            LWJGL.glDispatchCompute(this.groupsX, this.groupsY, this.groupsZ);
        }
    }

    // Deletes the program.
    void destroy() {
        this.program.destroy();
    }
}
