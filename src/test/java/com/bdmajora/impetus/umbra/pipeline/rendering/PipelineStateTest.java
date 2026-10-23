package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.compat.dh.DhCompat;
import com.bdmajora.impetus.umbra.material.WorldRenderingSettings;
import com.bdmajora.impetus.umbra.pipeline.CustomTextureManager;
import com.bdmajora.impetus.umbra.pipeline.UmbraRenderingPipeline;
import com.bdmajora.impetus.umbra.shaderpack.texture.TextureStage;
import com.bdmajora.testing.Statics;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PipelineStateTest {
    private static final String VERTEX = "#version 120\nvoid main() { gl_Position = ftransform(); }";
    private PipelineGl gl;

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
        PackSwitches.resetWorldSettings();
        ScalarDirectives.reset();
        this.gl.close();
    }

    @Test
    void packTexturesStayBelowThePipelinesFixedHighUnits() {
        // A driver reporting 48 units used to hand the pack units 32+ that the scratch, overlay, DH and shadowcolor binds overwrote
        assertEquals(31, (int) Statics.call(PipelineState.class, "lastProgrammableUnit"));
        this.gl.limits(28, 8, 8, 8);
        assertEquals(27, (int) Statics.call(PipelineState.class, "lastProgrammableUnit"));
        // Too few units for any: the range ends below its start rather than wrapping onto fixed units
        this.gl.limits(16, 8, 8, 8);
        assertEquals(SamplerLayout.CUSTOM_TEX_FIRST_UNIT - 1, (int) Statics.call(PipelineState.class, "lastProgrammableUnit"));
    }

    @Test
    void theMergedOverridesAreBuiltOncePerStageReadOnlyAndCarryTheImageUnits() {
        UmbraRenderingPipeline pipeline = PipelineGl.pipeline(
                "/composite.vsh", VERTEX,
                "/composite.fsh", "void main() { imageStore(colorimg4, ivec2(0), vec4(0)); }");
        try {
            PipelineState state = PipelineGl.state(pipeline);
            Map<String, CustomTextureManager.Override> overrides = state.overrides(TextureStage.COMPOSITE_AND_FINAL);
            assertSame(overrides, state.overrides(TextureStage.COMPOSITE_AND_FINAL));
            assertTrue(overrides.containsKey("colorimg4"));
            assertEquals(-1, overrides.get("colorimg4").colorTarget);
            assertThrows(UnsupportedOperationException.class, () -> overrides.put("x", null));
            // The live pipeline's gbuffer overrides are published for the terrain and DH programs, and withdrawn with it
            assertTrue(SamplerAssignment.activeOverrides().containsKey("colorimg4"));
            pipeline.destroy();
            assertTrue(SamplerAssignment.activeOverrides().isEmpty());
        } finally {
            pipeline.destroy();
        }
    }

    @Test
    void teardownGivesVanillaItsLightingBackAndIsSafeTwice() {
        UmbraRenderingPipeline pipeline = PipelineGl.pipeline("/shaders.properties", "oldLighting = false",
                "/composite.vsh", VERTEX, "/composite.fsh", "const float ambientOcclusionLevel = 0.0;\nvoid main() {}");
        assertFalse(WorldRenderingSettings.isOldLighting());
        assertEquals(0.0f, WorldRenderingSettings.getAmbientOcclusionLevel());
        pipeline.destroy();
        // Turning shaders off used to leave vanilla terrain flat-shaded and without AO
        assertTrue(WorldRenderingSettings.isOldLighting());
        assertEquals(1.0f, WorldRenderingSettings.getAmbientOcclusionLevel());
        Mockito.clearInvocations(TestGl.gl());
        pipeline.destroy();
        verify(TestGl.gl(), never()).glDeleteSamplers(anyInt());
        PipelineState state = PipelineGl.state(pipeline);
        assertTrue(state.destroyed);
        assertFalse(state.active);
    }

    @Test
    void aBrokenDistantHorizonsBridgeDoesNotStopTheRestOfTheTeardown() {
        UmbraRenderingPipeline pipeline = PipelineGl.pipeline("/shaders.properties", "");
        PipelineState state = PipelineGl.state(pipeline);
        DhCompat dh = mock(DhCompat.class);
        doThrow(new RuntimeException("bridge gone")).when(dh).clearPipeline();
        state.dhCompat = dh;
        int depth = state.targets.getDepthTexture().getTextureId();
        pipeline.destroy();
        verify(dh).clearPipeline();
        assertNull(state.dhCompat);
        verify(TestGl.gl()).glDeleteTextures(depth);
        verify(TestGl.gl(), times(8)).glDeleteSamplers(anyInt());
    }

    @Test
    void aStateThatCannotBeBuiltFreesEverythingItMade() {
        when(TestGl.gl().glCheckFramebufferStatus(anyInt())).thenReturn(0);
        assertThrows(IllegalStateException.class, () -> PipelineGl.pipeline("/shaders.properties", "oldLighting = false"));
        verify(TestGl.gl(), times(8)).glDeleteSamplers(anyInt());
        verify(TestGl.gl(), Mockito.atLeast(3)).glDeleteTextures(anyInt());
        assertTrue(WorldRenderingSettings.isOldLighting());
        assertTrue(SamplerAssignment.activeOverrides().isEmpty());
    }

    @Test
    void aFailedDrawBufferQueryFallsBackToTheGl33Floor() {
        when(TestGl.gl().glGetInteger(0x8824)).thenReturn(0, 8);
        UmbraRenderingPipeline pipeline = PipelineGl.pipeline("/shaders.properties", "");
        try {
            assertEquals(8, PipelineGl.state(pipeline).maxDrawBuffers);
        } finally {
            pipeline.destroy();
        }
    }

    @Test
    void bufferSizesForTargetsThatCannotExistAreIgnored() {
        UmbraRenderingPipeline pipeline = PipelineGl.pipeline("/shaders.properties",
                "size.buffer.colortex20 = 64 64\nsize.buffer.colortex-1 = 32 32\nsize.buffer.colortex3 = 16 8");
        try {
            PipelineState state = PipelineGl.state(pipeline);
            assertTrue(state.targets.hasCustomSize(3));
            assertEquals(16, state.targets.getWidth(3));
        } finally {
            pipeline.destroy();
        }
    }

    @Test
    void aStateThatFailsBeforeMostOfItExistsStillTearsDownWhatDid() {
        when(TestGl.gl().glGenSamplers()).thenThrow(new IllegalStateException("no samplers"));
        assertThrows(IllegalStateException.class, () -> PipelineGl.pipeline("/shaders.properties", ""));
        // The quad and the targets' depth textures were made and are gone again
        verify(TestGl.gl(), Mockito.atLeast(3)).glDeleteTextures(anyInt());
        verify(TestGl.gl(), Mockito.atLeastOnce()).glDeleteVertexArrays(anyInt());
        Mockito.reset(TestGl.gl());
        when(TestGl.gl().glGenVertexArrays()).thenThrow(new IllegalStateException("no vertex arrays"));
        assertThrows(IllegalStateException.class, () -> PipelineGl.pipeline("/shaders.properties", ""));
        verify(TestGl.gl(), Mockito.atLeast(3)).glDeleteTextures(anyInt());
    }
}
