package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.pipeline.ColorSpaceConverter;
import com.bdmajora.impetus.umbra.pipeline.UmbraRenderingPipeline;
import com.bdmajora.impetus.umbra.pipeline.UmbraShadowRenderer;
import com.bdmajora.impetus.umbra.uniforms.CapturedRenderingState;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import com.bdmajora.testing.TestGl;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.entity.passive.EntityPig;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FrameLifecycleTest {
    private static final String VERTEX = "#version 120\nvoid main() { gl_Position = ftransform(); }";
    private static final String COMPUTE = "#version 430\nlayout(local_size_x = 8) in;\nconst ivec3 workGroups = ivec3(3, 1, 1);\nvoid main() {}";
    private PipelineGl gl;
    private UmbraRenderingPipeline pipeline;
    private PipelineState state;
    private PassRunner runner;
    private PhaseController phases;
    private FrameLifecycle frame;
    private FrameCopies copies;

    @BeforeAll
    static void bootstrap() {
        PipelineGl.bootstrap();
    }

    @BeforeEach
    void open() {
        this.gl = new PipelineGl();
    }

    @AfterEach
    void close() {
        ColorSpaceConverter.setColorSpace(ColorSpaceConverter.ColorSpace.SRGB);
        if (this.pipeline != null) {
            this.pipeline.destroy();
        }
        this.gl.close();
    }

    private void build(String... pathsAndSources) {
        this.pipeline = PipelineGl.pipeline(pathsAndSources);
        this.state = PipelineGl.state(this.pipeline);
        this.copies = Mockito.spy(this.state.copies);
        Mixins.set(this.state, "copies", this.copies);
        this.runner = Mockito.spy(new PassRunner(this.state));
        this.phases = Mockito.spy(new PhaseController(this.state));
        this.frame = new FrameLifecycle(this.state, this.runner, this.phases);
        Mockito.clearInvocations(TestGl.gl(), this.gl.client.getFramebuffer());
    }

    private void fullPack() {
        build("/setup.csh", COMPUTE,
                "/begin.vsh", VERTEX, "/begin.fsh", "/* DRAWBUFFERS:8 */\nvoid main() {}",
                "/prepare.csh", COMPUTE,
                "/deferred.vsh", VERTEX, "/deferred.fsh", "/* DRAWBUFFERS:04 */\nvoid main() {}",
                "/composite.vsh", VERTEX, "/composite.fsh", "/* DRAWBUFFERS:0 */\nvoid main() {}");
    }

    @Test
    void aDestroyedPipelineNeverStartsAFrameAndTheOtherStagesWaitForOne() {
        fullPack();
        this.frame.captureRenderingState();
        this.frame.renderShadowMap();
        this.frame.beginTranslucents();
        this.frame.beginHand();
        this.frame.finish();
        verify(this.runner, never()).runFamily(any(), any());
        verify(this.runner, never()).copyDepth(any());
        this.state.destroy();
        this.frame.begin(0.5f);
        assertFalse(this.state.active);
        verify(this.copies, never()).clear(anyBoolean(), anyInt(), anyInt());
    }

    @Test
    void theFirstFrameClearsEverythingRunsSetupOnceAndHandsTheWorldTheGbuffer() {
        fullPack();
        this.frame.begin(0.25f);
        assertTrue(this.state.active);
        assertSame(this.state.schedule.gbuffer, this.state.gbuffers.current);
        assertSame(this.state.schedule.preTranslucentFlips, this.state.activeGbufferFlips);
        verify(this.copies).clear(true, 320, 240);
        verify(this.runner).runFamily(this.state.schedule.begin, this.gl.client);
        // setup (3 groups) runs once, on the first frame only
        verify(TestGl.gl(), times(1)).glDispatchCompute(3, 1, 1);
        assertEquals(0.25f, CapturedRenderingState.INSTANCE.getTickDelta());
        this.frame.finish();
        this.frame.begin(0.5f);
        verify(this.copies).clear(false, 320, 240);
        verify(TestGl.gl(), times(1)).glDispatchCompute(3, 1, 1);
        // The world starts with fixed-function output into colortex0 and the sky range reset
        verify(TestGl.gl(), Mockito.atLeastOnce()).glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.state.schedule.gbuffer.getGlId());
    }

    @Test
    void aResizeReallocatesOnTheScratchUnitAndForcesAFullClear() {
        build("/shaders.properties", "");
        List<Integer> unitsDuringUpload = new ArrayList<>();
        Mockito.doAnswer(inv -> unitsDuringUpload.add(this.gl.activeUnit)).when(TestGl.gl())
                .glTexImage2D(anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), Mockito.nullable(ByteBuffer.class));
        this.frame.begin(0.0f);
        this.frame.finish();
        this.gl.client.displayWidth = 400;
        this.gl.client.displayHeight = 300;
        unitsDuringUpload.clear();
        this.frame.begin(0.0f);
        assertEquals(400, this.state.targets.getWidth());
        assertFalse(unitsDuringUpload.isEmpty());
        // The targets bind raw while reallocating; on a cached unit that desynced GlStateManager until frame end
        for (int unit : unitsDuringUpload) {
            assertEquals(SamplerLayout.MIPMAP_SCRATCH_UNIT, unit);
        }
        verify(this.copies).clear(true, 400, 300);
        assertFalse(this.state.fullClearRequired);
    }

    @Test
    void theCameraIsInterpolatedAndTheMatricesCapturedWithoutAllocating() {
        build("/shaders.properties", "");
        EntityPig camera = mock(EntityPig.class);
        camera.lastTickPosX = 0;
        camera.posX = 10;
        camera.lastTickPosY = 64;
        camera.posY = 66;
        camera.lastTickPosZ = -4;
        camera.posZ = -8;
        when(this.gl.client.getRenderViewEntity()).thenReturn(camera);
        this.frame.begin(0.5f);
        assertEquals(new Vector3d(5, 65, -6), CapturedRenderingState.INSTANCE.getCameraPosition());
        FloatBuffer modelView = Statics.get(ActiveRenderInfo.class, "MODELVIEW");
        FloatBuffer projection = Statics.get(ActiveRenderInfo.class, "PROJECTION");
        float[] savedView = new float[16];
        float[] savedProjection = new float[16];
        modelView.get(0, savedView);
        projection.get(0, savedProjection);
        try {
            Matrix4f view = new Matrix4f().translation(1, 2, 3);
            Matrix4f proj = new Matrix4f().perspective(1.0f, 1.5f, 0.05f, 256.0f);
            view.get(0, modelView);
            proj.get(0, projection);
            Matrix4f scratch = Mixins.get(this.frame, "scratch");
            this.frame.captureRenderingState();
            assertEquals(view, CapturedRenderingState.INSTANCE.getGbufferModelView());
            assertEquals(proj, CapturedRenderingState.INSTANCE.getGbufferProjection());
            assertSame(scratch, Mixins.get(this.frame, "scratch"));
        } finally {
            modelView.put(0, savedView);
            projection.put(0, savedProjection);
            CapturedRenderingState.INSTANCE.setGbufferModelView(new Matrix4f());
            CapturedRenderingState.INSTANCE.setGbufferProjection(new Matrix4f());
        }
    }

    @Test
    void prepareRunsAfterTheShadowMapUnlessThePackAsksForBefore() {
        build("/prepare.csh", COMPUTE);
        UmbraShadowRenderer shadow = mock(UmbraShadowRenderer.class);
        Mixins.set(this.state, "shadowRenderer", shadow);
        this.frame.begin(0.0f);
        this.frame.renderShadowMap();
        InOrder order = Mockito.inOrder(shadow, this.runner);
        order.verify(shadow).render();
        order.verify(this.runner).runShadowComposites(this.gl.client);
        order.verify(this.runner).runFamily(this.state.schedule.prepare, this.gl.client);
        this.pipeline.destroy();
        build("/shaders.properties", "prepareBeforeShadow = true", "/prepare.csh", COMPUTE);
        Mixins.set(this.state, "shadowRenderer", shadow);
        Mockito.clearInvocations(shadow);
        this.frame.begin(0.0f);
        this.frame.renderShadowMap();
        InOrder before = Mockito.inOrder(shadow, this.runner);
        before.verify(this.runner).runFamily(this.state.schedule.prepare, this.gl.client);
        before.verify(shadow).render();
        // No shadow pass: only prepare
        Mixins.set(this.state, "shadowRenderer", null);
        Mockito.clearInvocations(this.runner);
        this.frame.renderShadowMap();
        verify(this.runner, never()).runShadowComposites(any());
        verify(this.runner).runFamily(this.state.schedule.prepare, this.gl.client);
    }

    @Test
    void theTranslucentAnchorSnapshotsDepthRunsDeferredAndSwitchesToThePostDeferredGbuffer() {
        fullPack();
        this.frame.begin(0.0f);
        // Before the anchor the frame still draws into the pre-deferred gbuffer
        assertFalse(this.state.isPostDeferred());
        this.frame.beginTranslucents();
        verify(this.runner).copyDepth(this.state.targets.getDepthTextureNoTranslucents());
        verify(this.runner).run(this.state.schedule.deferred.get(0), this.gl.client);
        assertSame(this.state.schedule.translucentGbuffer, this.state.gbuffers.current);
        assertSame(this.state.schedule.translucentFlips, this.state.activeGbufferFlips);
        assertTrue(this.state.isPostDeferred());
        // OptiFine's beginWater() state: blending and depth writes on
        this.gl.gl11.verify(() -> GL11.glDepthMask(true), Mockito.atLeastOnce());
        this.frame.beginHand();
        verify(this.runner).copyDepth(this.state.targets.getDepthTextureNoHand());
    }

    @Test
    void withoutADeferredChainTheTranslucentAnchorOnlySnapshotsDepth() {
        build("/composite.vsh", VERTEX, "/composite.fsh", "void main() {}");
        this.frame.begin(0.0f);
        this.frame.beginTranslucents();
        verify(this.runner).copyDepth(this.state.targets.getDepthTextureNoTranslucents());
        verify(this.runner, never()).run(any(), any());
        assertSame(this.state.schedule.gbuffer, this.state.gbuffers.current);
        assertFalse(this.state.isPostDeferred());
    }

    @Test
    void finishingRunsTheChainBlitsWithoutAFinalProgramCopiesBackAndHandsVanillaACleanState() {
        fullPack();
        this.frame.begin(0.0f);
        this.frame.finish();
        assertFalse(this.state.active);
        verify(this.phases).endFrame();
        verify(this.runner).run(this.state.schedule.composite.get(0), this.gl.client);
        InOrder order = Mockito.inOrder(TestGl.gl(), this.runner, this.copies);
        order.verify(TestGl.gl()).glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, this.state.schedule.blitSource.getGlId());
        order.verify(TestGl.gl()).glBlitFramebuffer(0, 0, 320, 240, 0, 0, 320, 240, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        order.verify(this.runner).resetMipmaps();
        order.verify(this.copies).swap(this.state.targets);
        order.verify(TestGl.gl()).glUseProgram(0);
        verify(this.gl.client.getFramebuffer(), Mockito.atLeastOnce()).bindFramebuffer(true);
        assertEquals(0, this.gl.activeUnit);
        // A second finish in the same frame (the hand anchor ran first) does nothing
        Mockito.clearInvocations(this.runner);
        this.frame.finish();
        verify(this.runner, never()).resetMipmaps();
    }

    @Test
    void theBlitTargetsTheDefaultFramebufferWhenVanillaHasNone() {
        build("/shaders.properties", "");
        this.frame.begin(0.0f);
        net.minecraft.client.renderer.OpenGlHelper.framebufferSupported = false;
        this.frame.finish();
        verify(TestGl.gl()).glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, 0);
        verify(TestGl.gl()).glDrawBuffers(0x0405);
    }

    @Test
    void wideGamutOutputConvertsThePresentedImageLast() {
        build("/final.vsh", VERTEX, "/final.fsh", "void main() { gl_FragColor = vec4(1.0); }");
        ColorSpaceConverter converter = mock(ColorSpaceConverter.class);
        when(converter.isActive()).thenReturn(true);
        Mixins.set(this.state, "colorSpace", converter);
        this.frame.begin(0.0f);
        this.frame.finish();
        verify(converter).run(320, 240, this.state.quad);
        assertNull(this.state.schedule.blitSource);
        verify(TestGl.gl(), never()).glBlitFramebuffer(anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
        verify(TestGl.gl(), never()).glActiveTexture(eq(GL13.GL_TEXTURE0 + SamplerLayout.MIPMAP_SCRATCH_UNIT));
    }

    @Test
    void aHeightOnlyResizeStillReallocates() {
        build("/shaders.properties", "");
        this.frame.begin(0.0f);
        this.frame.finish();
        this.gl.client.displayHeight = 200;
        this.frame.begin(0.0f);
        assertEquals(200, this.state.targets.getHeight());
        verify(this.copies).clear(true, 320, 200);
    }
}
