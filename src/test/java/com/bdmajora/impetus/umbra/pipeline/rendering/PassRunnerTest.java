package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.gl.blending.ProgramBlendState;
import com.bdmajora.impetus.umbra.gl.framebuffer.UmbraFramebuffer;
import com.bdmajora.impetus.umbra.gl.program.GlProgram;
import com.bdmajora.impetus.umbra.gl.program.ProgramUniforms;
import com.bdmajora.impetus.umbra.gl.program.UmbraProgram;
import com.bdmajora.impetus.umbra.gl.sampler.ShadowSamplerKinds;
import com.bdmajora.impetus.umbra.pipeline.FullscreenQuadRenderer;
import com.bdmajora.impetus.umbra.pipeline.UmbraRenderingPipeline;
import com.bdmajora.impetus.umbra.targets.UmbraRenderTarget;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL42;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.util.BitSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class PassRunnerTest {
    private PipelineGl gl;
    private UmbraRenderingPipeline pipeline;
    private PipelineState state;
    private PassRunner runner;
    private TextureBinder binder;
    private FullscreenQuadRenderer quad;

    @BeforeAll
    static void bootstrap() {
        PipelineGl.bootstrap();
    }

    @BeforeEach
    void open() {
        this.gl = new PipelineGl();
        this.pipeline = PipelineGl.pipeline("/shaders.properties", "allowConcurrentCompute = false");
        this.state = PipelineGl.state(this.pipeline);
        this.binder = Mockito.spy(this.state.binder);
        Mixins.set(this.state, "binder", this.binder);
        this.quad = mock(FullscreenQuadRenderer.class);
        Mixins.set(this.state, "quad", this.quad);
        this.runner = new PassRunner(this.state);
        PipelineGl.enterFrame(this.state);
        Mockito.clearInvocations(TestGl.gl(), this.gl.client.getFramebuffer());
    }

    @AfterEach
    void close() {
        this.pipeline.destroy();
        this.gl.close();
    }

    private static ComputePass compute() {
        return new ComputePass(mock(GlProgram.class), mock(ProgramUniforms.class), ShadowSamplerKinds.ALL_COMPARE,
                new int[]{2, 2, 1}, null, null, -1, 0L);
    }

    private static FullscreenPass pass(UmbraFramebuffer framebuffer, boolean draws, boolean modern, BitSet mipmapped,
                                       List<ComputePass> computes) {
        int[] samplers = new int[16];
        samplers[9] = 409;
        return new FullscreenPass("composite", draws ? mock(UmbraProgram.class) : null, draws ? mock(ProgramUniforms.class) : null,
                framebuffer, samplers, new int[]{0, 1}, mock(ProgramBlendState.class), new BitSet(), mipmapped, computes,
                ShadowSamplerKinds.ALL_COMPARE, modern, new int[]{64, 32}, null);
    }

    @Test
    void aPassDispatchesItsComputesFirstThenDrawsIntoItsFramebufferAtItsSize() {
        UmbraFramebuffer framebuffer = mock(UmbraFramebuffer.class);
        ComputePass compute = compute();
        FullscreenPass pass = pass(framebuffer, true, false, new BitSet(), List.of(compute));
        this.runner.run(pass, this.gl.client);
        InOrder order = Mockito.inOrder(compute.program, TestGl.gl(), framebuffer, pass.program, this.quad);
        order.verify(compute.program).bind();
        order.verify(TestGl.gl()).glDispatchCompute(2, 2, 1);
        order.verify(TestGl.gl()).glUseProgram(0);
        order.verify(framebuffer).bind();
        order.verify(TestGl.gl()).glViewport(0, 0, 64, 32);
        order.verify(pass.program).bind();
        order.verify(this.quad).draw();
        verify(pass.uniforms).update();
        verify(pass.blendState).apply(new int[]{0, 1});
        // colortex9 is beyond the cached units and still bound for the pass
        assertEquals(409, this.gl.textures[9]);
        // A legacy pass has its own matrices; only modern ones get the fixed-function ortho
        this.gl.gl11.verify(() -> GL11.glOrtho(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble()), never());
    }

    @Test
    void aModernPassDrawsUnderTheUnitOrthoAndPutsTheMatricesBack() {
        this.runner.run(pass(mock(UmbraFramebuffer.class), true, true, new BitSet(), List.of()), this.gl.client);
        this.gl.gl11.verify(() -> GL11.glOrtho(0.0, 1.0, 0.0, 1.0, -1.0, 1.0));
        this.gl.gl11.verify(GL11::glPopMatrix, times(3));
        verify(this.quad).draw();
    }

    @Test
    void theFinalPassDrawsIntoVanillasFramebufferAndAComputeOnlyEntryDrawsNothing() {
        this.runner.run(pass(null, true, false, new BitSet(), List.of()), this.gl.client);
        verify(this.gl.client.getFramebuffer()).bindFramebuffer(true);
        verify(TestGl.gl()).glDrawBuffers(GL30.GL_COLOR_ATTACHMENT0);
        verify(this.quad).draw();
        Mockito.clearInvocations(this.quad);
        ComputePass compute = compute();
        this.runner.run(pass(null, false, false, new BitSet(), List.of(compute)), this.gl.client);
        verify(compute.program).bind();
        verify(this.quad, never()).draw();
    }

    @Test
    void computesAreFencedOneByOneUnlessThePackDeclaresThemIndependent() {
        this.runner.dispatch(List.of(compute(), compute()));
        verify(TestGl.gl(), times(2)).glMemoryBarrier(GL42.GL_ALL_BARRIER_BITS);
        verify(this.binder, times(2)).bindPackResources();
        UmbraRenderingPipeline concurrent = PipelineGl.pipeline("/shaders.properties", "allowConcurrentCompute = true");
        try {
            PassRunner runner = new PassRunner(PipelineGl.state(concurrent));
            Mockito.clearInvocations(TestGl.gl());
            runner.dispatch(List.of(compute(), compute(), compute()));
            verify(TestGl.gl(), times(1)).glMemoryBarrier(GL42.GL_ALL_BARRIER_BITS);
            Mockito.clearInvocations(TestGl.gl());
            // Nothing dispatched, nothing to publish
            runner.dispatch(List.of());
            verify(TestGl.gl(), never()).glMemoryBarrier(anyInt());
        } finally {
            concurrent.destroy();
        }
    }

    @Test
    void aMidWorldFamilyPutsTheGbufferStateBackAfterItRuns() {
        this.runner.runFamily(List.of(), this.gl.client);
        verify(this.binder, never()).restoreUnits();
        this.runner.runFamily(List.of(pass(mock(UmbraFramebuffer.class), true, false, new BitSet(), List.of())), this.gl.client);
        InOrder order = Mockito.inOrder(this.binder, TestGl.gl());
        order.verify(this.binder).bindDepth();
        order.verify(this.binder).restoreUnits();
        order.verify(this.binder).bindShadow();
        order.verify(TestGl.gl()).glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.state.gbuffers.current.getGlId());
        order.verify(TestGl.gl()).glViewport(0, 0, 320, 240);
    }

    @Test
    void shadowcompComputesBindOnlyImagesAndARasterOneRestoresTheGbufferSamplersButNotTheShadowOnes() {
        this.runner.runShadowComposites(this.gl.client);
        verify(TestGl.gl(), never()).glMemoryBarrier(anyInt());
        ComputePass compute = compute();
        this.state.schedule.shadowComposite.add(FullscreenPass.computeOnly("shadowcomp", new int[16], new BitSet(), List.of(compute)));
        this.runner.runShadowComposites(this.gl.client);
        verify(compute.program).bind();
        verify(this.binder, never()).restoreUnits();
        // colortex0..7 stay off the atlas and lightmap units for a compute-only entry
        assertEquals(0, this.gl.textures[9]);
        this.state.schedule.shadowComposite.add(pass(mock(UmbraFramebuffer.class), true, false, new BitSet(), List.of()));
        Mockito.clearInvocations(this.binder);
        this.runner.runShadowComposites(this.gl.client);
        verify(this.binder).restoreUnits();
        verify(this.binder, never()).bindShadow();
        this.state.schedule.shadowComposite.clear();
    }

    @Test
    void mipmapsAreBuiltOnlyForDeclaredTargetsAndResetOnlyWhereSomePassBuiltThem() {
        UmbraRenderTarget target = Mockito.spy(this.state.targets.getOrCreate(5));
        UmbraRenderTarget[] targets = Mixins.get(this.state.targets, "targets");
        targets[5] = target;
        BitSet mipmapped = new BitSet();
        mipmapped.set(5);
        mipmapped.set(6);
        FullscreenPass pass = pass(mock(UmbraFramebuffer.class), true, false, mipmapped, List.of());
        pass.flipsBefore.set(5);
        this.runner.run(pass, this.gl.client);
        verify(target).generateMipmaps(true);
        this.gl.gl13.verify(() -> GL13.glActiveTexture(GL13.GL_TEXTURE0), Mockito.atLeastOnce());
        Mockito.clearInvocations(TestGl.gl());
        this.runner.resetMipmaps();
        verify(target, never()).resetMipmaps();
        verify(TestGl.gl(), never()).glActiveTexture(GL13.GL_TEXTURE0 + SamplerLayout.MIPMAP_SCRATCH_UNIT);
        this.state.schedule.mipmapped.set(5);
        this.runner.resetMipmaps();
        verify(target).resetMipmaps();
        verify(TestGl.gl()).glActiveTexture(GL13.GL_TEXTURE0 + SamplerLayout.MIPMAP_SCRATCH_UNIT);
        this.state.schedule.mipmapped.clear();
    }

    @Test
    void theDepthSnapshotCopiesFromTheGbufferOnTheScratchUnitAndRebindsThePackResources() {
        this.gl.textures[SamplerLayout.DEPTH_COPY_SCRATCH_UNIT] = 77;
        this.runner.copyDepth(this.state.targets.getDepthTextureNoTranslucents());
        InOrder order = Mockito.inOrder(TestGl.gl(), this.binder);
        order.verify(TestGl.gl()).glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.state.gbuffers.current.getGlId());
        order.verify(TestGl.gl()).glBindTexture(GL11.GL_TEXTURE_2D, this.state.targets.getDepthTextureNoTranslucents().getTextureId());
        order.verify(TestGl.gl()).glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, 320, 240);
        order.verify(this.binder).bindPackResources();
        assertEquals(77, this.gl.textures[SamplerLayout.DEPTH_COPY_SCRATCH_UNIT]);
        // Without a gbuffer yet the copy still runs from whatever is bound
        this.state.gbuffers.current = null;
        Mockito.clearInvocations(TestGl.gl());
        this.runner.copyDepth(this.state.targets.getDepthTextureNoHand());
        verify(TestGl.gl(), never()).glBindFramebuffer(anyInt(), anyInt());
        verify(TestGl.gl()).glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, 320, 240);
    }

    @Test
    void aFamilyRunBeforeAnyGbufferExistsLeavesTheFramebufferAloneAndAMissingMipmapTargetIsSkipped() {
        this.state.gbuffers.current = null;
        this.runner.runFamily(List.of(pass(mock(UmbraFramebuffer.class), true, false, new BitSet(), List.of())), this.gl.client);
        verify(TestGl.gl(), never()).glBindFramebuffer(Mockito.eq(GL30.GL_FRAMEBUFFER), anyInt());
        this.state.schedule.mipmapped.set(11);
        this.runner.resetMipmaps();
        assertNull(this.state.targets.get(11));
        assertEquals(0, this.gl.activeUnit);
        this.state.schedule.mipmapped.clear();
    }
}
