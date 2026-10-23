package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.gl.blending.ProgramAlphaTest;
import com.bdmajora.impetus.umbra.gl.blending.ProgramBlendState;
import com.bdmajora.impetus.umbra.gl.program.GlProgram;
import com.bdmajora.impetus.umbra.gl.program.ProgramUniforms;
import com.bdmajora.impetus.umbra.gl.program.UmbraProgram;
import com.bdmajora.impetus.umbra.gl.sampler.ShadowSamplerKinds;
import com.bdmajora.impetus.umbra.pipeline.GbufferPrograms;
import com.bdmajora.impetus.umbra.pipeline.UmbraRenderingPipeline;
import com.bdmajora.impetus.umbra.pipeline.UmbraShadowRenderer;
import com.bdmajora.impetus.umbra.shaderpack.loading.ProgramId;
import com.bdmajora.impetus.umbra.uniforms.CapturedRenderingState;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PhaseControllerTest {
    private PipelineGl gl;
    private UmbraRenderingPipeline pipeline;
    private PipelineState state;
    private PhaseController phases;
    private final Map<ProgramId, GbufferPrograms.Entry> entries = new EnumMap<>(ProgramId.class);

    @BeforeAll
    static void bootstrap() {
        PipelineGl.bootstrap();
    }

    @BeforeEach
    void open() {
        this.gl = new PipelineGl();
        this.pipeline = PipelineGl.pipeline("/shaders.properties", "");
        this.state = PipelineGl.state(this.pipeline);
        GbufferPrograms programs = mock(GbufferPrograms.class);
        when(programs.get(Mockito.any())).thenAnswer(inv -> this.entries.get(inv.<ProgramId>getArgument(0)));
        when(programs.hasDirect(Mockito.any())).thenAnswer(inv -> inv.getArgument(0) == ProgramId.Entities);
        Mixins.set(this.state, "gbufferPrograms", programs);
        this.phases = new PhaseController(this.state);
    }

    @AfterEach
    void close() {
        CapturedRenderingState.INSTANCE.setRenderStage(0);
        this.pipeline.destroy();
        this.gl.close();
    }

    private GbufferPrograms.Entry entry(ProgramId phase, int glId, int... drawBuffers) {
        GbufferPrograms.Entry entry = mock(GbufferPrograms.Entry.class);
        UmbraProgram program = mock(UmbraProgram.class);
        GlProgram glProgram = mock(GlProgram.class);
        when(glProgram.getGlId()).thenReturn(glId);
        when(program.getProgram()).thenReturn(glProgram);
        when(entry.getProgram()).thenReturn(program);
        when(entry.getUniforms()).thenReturn(mock(ProgramUniforms.class));
        when(entry.drawBuffersReadOnly()).thenReturn(drawBuffers.length == 0 ? new int[]{0} : drawBuffers);
        when(entry.getBlendState()).thenReturn(mock(ProgramBlendState.class));
        when(entry.getAlphaTest()).thenReturn(mock(ProgramAlphaTest.class));
        when(entry.getShadowSamplerKinds()).thenReturn(ShadowSamplerKinds.ALL_COMPARE);
        this.entries.put(phase, entry);
        return entry;
    }

    private void shadowPass(boolean active) {
        Statics.set(UmbraShadowRenderer.class, "shadowPassActive", active);
    }

    @Test
    void identifiablePhasesPublishUmbrasRenderStages() {
        assertEquals(0, PhaseController.defaultRenderStage(null));
        assertEquals(1, PhaseController.defaultRenderStage(ProgramId.SkyBasic));
        assertEquals(4, PhaseController.defaultRenderStage(ProgramId.SkyTextured));
        assertEquals(11, PhaseController.defaultRenderStage(ProgramId.Entities));
        assertEquals(11, PhaseController.defaultRenderStage(ProgramId.SpiderEyes));
        assertEquals(13, PhaseController.defaultRenderStage(ProgramId.DamagedBlock));
        assertEquals(14, PhaseController.defaultRenderStage(ProgramId.Line));
        assertEquals(19, PhaseController.defaultRenderStage(ProgramId.Particles));
        assertEquals(20, PhaseController.defaultRenderStage(ProgramId.Clouds));
        assertEquals(21, PhaseController.defaultRenderStage(ProgramId.Weather));
        assertEquals(0, PhaseController.defaultRenderStage(ProgramId.Hand));
        assertEquals(0, PhaseController.defaultRenderStage(ProgramId.Terrain));
    }

    @Test
    void outsideAFrameNothingSwitchesOrAnswersYes() {
        entry(ProgramId.Entities, 50);
        assertFalse(this.phases.has(ProgramId.Entities));
        assertFalse(this.phases.hasDirect(ProgramId.Entities));
        assertEquals(ProgramId.Entities, this.phases.translucentEntityPhase());
        this.phases.select(ProgramId.Entities, 11);
        this.phases.lightmapToggled(true);
        this.phases.refreshDynamicUniforms();
        assertFalse(this.phases.enterEyes());
        assertFalse(this.phases.enterGlint());
        this.phases.exitEyes();
        this.phases.exitGlint();
        assertNull(this.phases.current());
        verify(this.entries.get(ProgramId.Entities), never()).getProgram();
    }

    @Test
    void aPhaseBindsItsProgramUploadsAndPointsTheGbufferAtItsTargets() {
        PipelineGl.enterFrame(this.state);
        GbufferPrograms.Entry entities = entry(ProgramId.Entities, 50, 0);
        when(entities.getAlphaTest().hasDirectives()).thenReturn(true);
        assertTrue(this.phases.has(ProgramId.Entities));
        assertTrue(this.phases.hasDirect(ProgramId.Entities));
        assertFalse(this.phases.hasDirect(ProgramId.Particles));
        this.phases.select(ProgramId.Entities, 11);
        assertEquals(ProgramId.Entities, this.phases.current());
        assertEquals(11, CapturedRenderingState.INSTANCE.getRenderStage());
        verify(entities.getProgram()).bind();
        verify(entities.getUniforms()).update();
        verify(entities.getBlendState()).apply(new int[]{0});
        verify(entities.getAlphaTest()).apply();
        // The fixed-function vertex arrays are handed back on every switch
        verify(TestGl.gl(), Mockito.atLeastOnce()).glDisableVertexAttribArray(15);
        // A phase with no program draws fixed-function into colortex0, and the previous alpha override is undone first
        this.phases.select(ProgramId.Particles, 19);
        verify(entities.getAlphaTest()).restore();
        assertEquals(0, this.gl.program);
        this.phases.select(null, 0);
        assertNull(this.phases.current());
        verify(entities.getAlphaTest(), times(1)).restore();
    }

    @Test
    void skyPhasesSitAtTheFarPlaneAndTheRangeOnlyMovesOnAChange() {
        PipelineGl.enterFrame(this.state);
        this.phases.select(ProgramId.SkyBasic, 1);
        this.phases.select(ProgramId.SkyTextured, 4);
        verify(TestGl.gl(), times(1)).glDepthRange(1.0, 1.0);
        this.phases.select(ProgramId.Clouds, 20);
        this.phases.select(ProgramId.Entities, 11);
        verify(TestGl.gl(), times(1)).glDepthRange(0.0, 1.0);
        this.phases.resetDepthRange();
        verify(TestGl.gl(), times(2)).glDepthRange(0.0, 1.0);
    }

    @Test
    void theShadowPassNeverSelectsAGbufferPhase() {
        PipelineGl.enterFrame(this.state);
        GbufferPrograms.Entry entities = entry(ProgramId.Entities, 50);
        shadowPass(true);
        try {
            this.phases.select(ProgramId.Entities, 11);
            assertFalse(this.phases.enterEyes());
            assertFalse(this.phases.enterGlint());
            this.phases.refreshDynamicUniforms();
            assertNull(this.phases.current());
            verify(entities.getProgram(), never()).bind();
        } finally {
            shadowPass(false);
        }
    }

    @Test
    void theLightmapToggleSwapsOnlyTheTexturedPairAndKeepsTheStage() {
        PipelineGl.enterFrame(this.state);
        entry(ProgramId.TexturedLit, 61);
        this.phases.select(ProgramId.Textured, 19);
        this.phases.lightmapToggled(true);
        assertEquals(ProgramId.TexturedLit, this.phases.current());
        assertEquals(19, CapturedRenderingState.INSTANCE.getRenderStage());
        this.phases.lightmapToggled(true);
        assertEquals(ProgramId.TexturedLit, this.phases.current());
        this.phases.lightmapToggled(false);
        assertEquals(ProgramId.Textured, this.phases.current());
        this.phases.select(ProgramId.Entities, 11);
        this.phases.lightmapToggled(true);
        this.phases.lightmapToggled(false);
        assertEquals(ProgramId.Entities, this.phases.current());
    }

    @Test
    void perObjectUniformsGoOnlyToTheProgramActuallyBound() {
        PipelineGl.enterFrame(this.state);
        GbufferPrograms.Entry entities = entry(ProgramId.Entities, 50);
        this.phases.refreshDynamicUniforms();
        this.phases.select(ProgramId.Entities, 11);
        this.gl.program = 50;
        this.phases.refreshDynamicUniforms();
        verify(entities.getUniforms(), times(1)).updatePerObject();
        // A held item drawn under another program must not receive the entity program's uploads
        this.gl.program = 7;
        this.phases.refreshDynamicUniforms();
        this.phases.select(ProgramId.Particles, 19);
        this.phases.refreshDynamicUniforms();
        verify(entities.getUniforms(), times(1)).updatePerObject();
    }

    @Test
    void theEyesBracketRestoresWhatWasBoundAndAnUnmatchedEndChangesNothing() {
        PipelineGl.enterFrame(this.state);
        entry(ProgramId.EntitiesTrans, 52);
        assertEquals(ProgramId.EntitiesTrans, this.phases.translucentEntityPhase());
        this.phases.select(ProgramId.EntitiesTrans, 11);
        // An end with no begin (one that bailed) used to restore a stale phase and unbind the entity program
        this.phases.exitEyes();
        assertEquals(ProgramId.EntitiesTrans, this.phases.current());
        assertTrue(this.phases.enterEyes());
        verify(TestGl.gl()).glMultiTexCoord2f(eq(net.minecraft.client.renderer.OpenGlHelper.lightmapTexUnit), eq(240.0f), eq(240.0f));
        this.phases.select(ProgramId.SpiderEyes, 11);
        // A nested begin keeps the outer phase
        assertTrue(this.phases.enterEyes());
        this.phases.exitEyes();
        assertEquals(ProgramId.EntitiesTrans, this.phases.current());
        this.phases.exitEyes();
        assertEquals(ProgramId.EntitiesTrans, this.phases.current());
    }

    @Test
    void theGlintBracketRestoresTheHandOrEntityPhaseItInterrupted() {
        PipelineGl.enterFrame(this.state);
        this.phases.select(ProgramId.Hand, 16);
        assertTrue(this.phases.enterGlint());
        this.phases.select(ProgramId.ArmorGlint, 0);
        assertTrue(this.phases.enterGlint());
        this.phases.exitGlint();
        assertEquals(ProgramId.Hand, this.phases.current());
        assertEquals(16, CapturedRenderingState.INSTANCE.getRenderStage());
        this.phases.select(ProgramId.Particles, 19);
        this.phases.exitGlint();
        assertEquals(ProgramId.Particles, this.phases.current());
    }

    @Test
    void theFrameEndForgetsOpenBracketsAndUndoesAnAlphaOverride() {
        PipelineGl.enterFrame(this.state);
        ProgramAlphaTest forced = mock(ProgramAlphaTest.class);
        this.phases.forceAlphaTest(forced);
        verify(forced).apply();
        this.phases.select(ProgramId.Entities, 11);
        this.phases.enterEyes();
        this.phases.enterGlint();
        this.phases.endFrame();
        this.phases.select(ProgramId.Particles, 19);
        this.phases.exitEyes();
        this.phases.exitGlint();
        assertEquals(ProgramId.Particles, this.phases.current());
        verify(forced, times(1)).restore();
        this.phases.restoreAlphaTest();
        verify(forced, times(1)).restore();
        // The eyes' full-bright coordinate was set by the one begin that ran
        verify(TestGl.gl(), times(1)).glMultiTexCoord2f(anyInt(), anyFloat(), anyFloat());
    }

    @Test
    void anOpenEyesBracketIsNotClosedFromTheShadowPassOrAfterTheFrame() {
        PipelineGl.enterFrame(this.state);
        this.phases.select(ProgramId.Entities, 11);
        assertTrue(this.phases.enterEyes());
        this.phases.select(ProgramId.SpiderEyes, 11);
        shadowPass(true);
        try {
            this.phases.exitEyes();
        } finally {
            shadowPass(false);
        }
        assertEquals(ProgramId.SpiderEyes, this.phases.current());
        this.state.active = false;
        this.phases.exitEyes();
        assertEquals(ProgramId.SpiderEyes, this.phases.current());
        this.state.active = true;
        this.phases.exitEyes();
        assertEquals(ProgramId.Entities, this.phases.current());
    }
}
