package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.gl.blending.ProgramAlphaTest;
import com.bdmajora.impetus.umbra.gl.blending.ProgramBlendState;
import com.bdmajora.impetus.umbra.gl.framebuffer.UmbraFramebuffer;
import com.bdmajora.impetus.umbra.pipeline.UmbraRenderingPipeline;
import com.bdmajora.impetus.umbra.pipeline.UmbraShadowRenderer;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TerrainHooksTest {
    private static final String VERTEX = "#version 120\nvoid main() { gl_Position = ftransform(); }";
    private PipelineGl gl;
    private UmbraRenderingPipeline pipeline;
    private PipelineState state;
    private PhaseController phases;
    private TerrainHooks terrain;
    private TextureBinder binder;
    private GbufferFeedback feedback;

    @BeforeAll
    static void bootstrap() {
        PipelineGl.bootstrap();
    }

    @BeforeEach
    void open() {
        this.gl = new PipelineGl();
        this.pipeline = PipelineGl.pipeline("/gbuffers_terrain.vsh", VERTEX, "/gbuffers_terrain.fsh", "/* DRAWBUFFERS:024 */\nvoid main() {}",
                "/deferred.vsh", VERTEX, "/deferred.fsh", "/* DRAWBUFFERS:4 */\nvoid main() {}");
        this.state = PipelineGl.state(this.pipeline);
        this.binder = Mockito.spy(this.state.binder);
        Mixins.set(this.state, "binder", this.binder);
        this.feedback = Mockito.spy(this.state.feedback);
        Mixins.set(this.state, "feedback", this.feedback);
        this.phases = new PhaseController(this.state);
        this.terrain = new TerrainHooks(this.state, this.phases);
        Mockito.clearInvocations(TestGl.gl(), this.gl.client.getFramebuffer());
    }

    @AfterEach
    void close() {
        Statics.set(UmbraShadowRenderer.class, "shadowPassActive", false);
        this.pipeline.destroy();
        this.gl.close();
    }

    @Test
    void outsideAFrameTheHooksDoNothing() {
        this.terrain.onTerrainDraw(new int[]{0}, ProgramBlendState.empty(), ProgramAlphaTest.empty(), false);
        this.terrain.afterTerrainDraw(2);
        this.terrain.onDhLodDraw(mock(UmbraFramebuffer.class), new int[]{0}, ProgramBlendState.empty(), false);
        this.terrain.afterDhLodDraw(2);
        verify(this.binder, never()).bindDepth();
        verify(this.gl.client.getFramebuffer(), never()).bindFramebuffer(Mockito.anyBoolean());
    }

    @Test
    void aTerrainDrawRebindsThePackSamplersAndPointsTheGbufferAtItsTargets() {
        PipelineGl.enterFrame(this.state);
        ProgramBlendState blend = mock(ProgramBlendState.class);
        ProgramAlphaTest alpha = mock(ProgramAlphaTest.class);
        when(alpha.hasDirectives()).thenReturn(true);
        this.terrain.onTerrainDraw(new int[]{0, 4, 99}, blend, alpha, false);
        verify(this.binder).bindDepth();
        verify(this.binder).bindShadow();
        verify(this.binder).bindNoise();
        verify(this.binder).bindPbr();
        verify(this.feedback).prepare(new int[]{0, 4}, this.state.activeGbufferFlips);
        verify(blend).apply(new int[]{0, 4});
        verify(alpha).apply();
        // Opaque: blend off on every attachment the draw writes
        verify(TestGl.gl(), times(2)).glDisablei(Mockito.eq(GL11.GL_BLEND), anyInt());
        // The override is undone by the next phase switch
        this.phases.select(null, 0);
        verify(alpha).restore();
    }

    @Test
    void aTranslucentDrawBlendsEveryAttachmentAndNullStatesCountAsNoDirectives() {
        PipelineGl.enterFrame(this.state);
        this.terrain.onTerrainDraw(new int[]{0, 2}, null, null, true);
        verify(TestGl.gl(), times(2)).glEnablei(Mockito.eq(GL11.GL_BLEND), anyInt());
        verify(TestGl.gl()).glBlendFuncSeparatei(1, GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        // A plain empty alpha test is not forced either
        ProgramAlphaTest none = mock(ProgramAlphaTest.class);
        this.terrain.onTerrainDraw(new int[]{0}, ProgramBlendState.empty(), none, false);
        verify(none, never()).apply();
    }

    @Test
    void afterATerrainPassVanillasTargetIsBackUnlessTheShadowPassOwnsIt() {
        PipelineGl.enterFrame(this.state);
        this.terrain.afterTerrainDraw(3);
        verify(this.gl.client.getFramebuffer()).bindFramebuffer(true);
        verify(TestGl.gl()).glDrawBuffers(GL30.GL_COLOR_ATTACHMENT0);
        verify(TestGl.gl(), times(3)).glDisablei(Mockito.eq(GL11.GL_BLEND), anyInt());
        assertEquals(0, this.gl.activeUnit);
        Statics.set(UmbraShadowRenderer.class, "shadowPassActive", true);
        this.terrain.afterTerrainDraw(3);
        verify(this.gl.client.getFramebuffer(), times(1)).bindFramebuffer(true);
    }

    @Test
    void lodFramebuffersUseTheFlipsOfThePhaseTheyRunIn() {
        PipelineGl.enterFrame(this.state);
        UmbraFramebuffer opaque = this.terrain.createDhFramebuffer(new int[]{4}, false);
        UmbraFramebuffer translucent = this.terrain.createDhFramebuffer(new int[]{4}, true);
        try {
            // The deferred pass flipped colortex4, so the post-deferred LOD pass writes its other side
            assertEquals(this.state.targets.get(4).getMainTexture(), opaque.getColorAttachment(4));
            assertEquals(this.state.targets.get(4).getAltTexture(), translucent.getColorAttachment(4));
        } finally {
            opaque.destroy();
            translucent.destroy();
        }
    }

    @Test
    void aLodPassTurnsTheAlphaTestOffAndPutsBackOnlyWhatItFoundOncePerPass() {
        PipelineGl.enterFrame(this.state);
        UmbraFramebuffer framebuffer = mock(UmbraFramebuffer.class);
        when(TestGl.gl().glGetBoolean(GL11.GL_ALPHA_TEST)).thenReturn(true, false);
        this.terrain.onDhLodDraw(framebuffer, new int[]{0}, ProgramBlendState.empty(), false);
        // DH's generic objects re-enter mid-pass and must not overwrite what the pass found
        this.terrain.onDhLodDraw(framebuffer, new int[]{0}, ProgramBlendState.empty(), true);
        verify(framebuffer, times(2)).bind();
        verify(TestGl.gl(), times(1)).glGetBoolean(GL11.GL_ALPHA_TEST);
        verify(TestGl.gl(), times(2)).glDisable(GL11.GL_ALPHA_TEST);
        verify(this.binder, times(2)).bindPackResources();
        this.terrain.afterDhLodDraw(1);
        verify(TestGl.gl()).glEnable(GL11.GL_ALPHA_TEST);
        // The next pass found it off and leaves it off
        this.terrain.onDhLodDraw(framebuffer, new int[]{0}, null, false);
        this.terrain.afterDhLodDraw(1);
        verify(TestGl.gl(), times(1)).glEnable(GL11.GL_ALPHA_TEST);
        verify(TestGl.gl(), times(2)).glGetBoolean(GL11.GL_ALPHA_TEST);
        assertEquals(0, this.gl.activeUnit);
    }

    @Test
    void anEndWithoutALodPassLeavesTheAlphaTestAlone() {
        PipelineGl.enterFrame(this.state);
        this.terrain.afterDhLodDraw(1);
        verify(TestGl.gl(), never()).glEnable(GL11.GL_ALPHA_TEST);
        verify(TestGl.gl(), times(1)).glDisablei(Mockito.eq(GL11.GL_BLEND), anyInt());
    }
}
