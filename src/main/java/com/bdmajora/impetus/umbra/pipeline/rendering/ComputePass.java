package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.gl.program.GlProgram;
import com.bdmajora.impetus.umbra.gl.program.ProgramUniforms;
import com.bdmajora.impetus.umbra.gl.sampler.ShadowSamplerKinds;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// One compute dispatch: the linked program, its uniforms, and how many groups to launch (indirect from a pack buffer, screen-relative, or fixed)
public final class ComputePass {
    // GL43 dispatch-indirect binding target, a literal to avoid a hard generated-constant dependency
    private static final int GL_DISPATCH_INDIRECT_BUFFER = 0x90EE;

    final GlProgram program;
    final ProgramUniforms uniforms;
    // Sampler objects also govern a compute stage's texture fetches, so a compute reading raw shadow depth needs the same per-program choice
    final ShadowSamplerKinds shadowSamplerKinds;
    private final int groupsX;
    private final int groupsY;
    private final int groupsZ;
    // Screen-relative dispatch (const vec2 workGroupsRender); NaN = fixed dispatch
    private final float renderScaleX;
    private final float renderScaleY;
    private final int localSizeX;
    private final int localSizeY;
    // Indirect dispatch (indirect.<pass> directive): GL buffer id, or -1 for direct dispatch
    private final int indirectBuffer;
    private final long indirectOffset;

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

    // Launches with the program already bound; a screen-relative dispatch is recomputed from the render size each frame
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

    // Frees the program, which this pass owns
    void destroy() {
        this.program.destroy();
    }
}
