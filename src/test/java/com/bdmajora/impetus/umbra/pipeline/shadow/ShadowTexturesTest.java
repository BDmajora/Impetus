package com.bdmajora.impetus.umbra.pipeline.shadow;

import com.bdmajora.impetus.lwjgl.LWJGLService;
import com.bdmajora.impetus.umbra.gl.texture.InternalTextureFormat;
import com.bdmajora.impetus.umbra.pipeline.rendering.PipelineGl;
import com.bdmajora.impetus.umbra.targets.DepthTexture;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.nio.ByteBuffer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// Covers the shadow texture factory
class ShadowTexturesTest {
    // GL model of the test
    private PipelineGl gl;

    // Registers the vanilla content once
    @BeforeAll
    static void bootstrap() {
        PipelineGl.bootstrap();
    }

    // Opens a fresh GL model
    @BeforeEach
    void open() {
        this.gl = new PipelineGl();
    }

    // Closes the GL model
    @AfterEach
    void close() {
        this.gl.close();
    }

    // Checks the minifying and magnifying filter for every flag pair
    @Test
    void filtersFollowTheMipmapAndNearestFlags() {
        assertEquals(GL11.GL_NEAREST_MIPMAP_NEAREST, ShadowTextures.minFilter(true, true));
        assertEquals(GL11.GL_LINEAR_MIPMAP_LINEAR, ShadowTextures.minFilter(true, false));
        assertEquals(GL11.GL_NEAREST, ShadowTextures.minFilter(false, true));
        assertEquals(GL11.GL_LINEAR, ShadowTextures.minFilter(false, false));
        assertEquals(GL11.GL_NEAREST, ShadowTextures.magFilter(true));
        assertEquals(GL11.GL_LINEAR, ShadowTextures.magFilter(false));
    }

    // Checks that the resolution check passes positive values and rejects zero and negatives
    @Test
    void theResolutionCheckAcceptsOnlyPositiveSizes() {
        assertEquals(1, ShadowTextures.checkResolution(1));
        assertEquals(Integer.MAX_VALUE, ShadowTextures.checkResolution(Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> ShadowTextures.checkResolution(0));
        assertThrows(IllegalArgumentException.class, () -> ShadowTextures.checkResolution(-1));
        assertThrows(IllegalArgumentException.class, () -> ShadowTextures.checkResolution(Integer.MIN_VALUE));
    }

    // Checks that a depth texture is generated and configured on the scratch unit and leaves unit 0 bound as before
    @Test
    void aDepthTextureIsBuiltOnTheScratchUnitAndLeavesUnitZeroAlone() {
        this.gl.textures[0] = 77;
        LWJGLService service = TestGl.gl();
        DepthTexture texture = ShadowTextures.createDepth(512, false, false, false, false);
        InOrder order = Mockito.inOrder(service);
        order.verify(service).glActiveTexture(GL13.GL_TEXTURE0 + ShadowTextures.SETUP_UNIT);
        order.verify(service).glGenTextures();
        assertEquals(77, this.gl.textures[0]);
        assertEquals(0, this.gl.textures[ShadowTextures.SETUP_UNIT]);
        assertEquals(0, this.gl.activeUnit);
        assertEquals(0, PipelineGl.cachedUnit());
        verify(service).glTexImage2D(eq(GL11.GL_TEXTURE_2D), eq(0), eq(GL14.GL_DEPTH_COMPONENT24), eq(512), eq(512),
                eq(0), eq(GL11.GL_DEPTH_COMPONENT), eq(GL11.GL_FLOAT), nullable(ByteBuffer.class));
        assertTrue(texture.getTextureId() > 0);
    }

    // Checks that comparison mode is set only with hardware filtering and without separate samplers
    @Test
    void comparisonIsSetOnlyForHardwareFilteringWithoutSeparateSamplers() {
        ShadowTextures.createDepth(64, true, false, false, false);
        assertEquals(List.of(GL30.GL_COMPARE_REF_TO_TEXTURE), GlCalls.texParameter(GL14.GL_TEXTURE_COMPARE_MODE));
        Mockito.clearInvocations(TestGl.gl());
        ShadowTextures.createDepth(64, true, false, false, true);
        ShadowTextures.createDepth(64, false, false, false, false);
        ShadowTextures.createDepth(64, false, false, false, true);
        assertTrue(GlCalls.texParameter(GL14.GL_TEXTURE_COMPARE_MODE).isEmpty());
    }

    // Checks the swizzle and clamping and the filters a depth texture ends with for each flag pair
    @Test
    void aDepthTextureSwizzlesClampsAndFilters() {
        boolean[][] flags = {{false, false}, {false, true}, {true, false}, {true, true}};
        for (boolean[] pair : flags) {
            Mockito.clearInvocations(TestGl.gl());
            ShadowTextures.createDepth(32, false, pair[0], pair[1], false);
            assertEquals(ShadowTextures.minFilter(pair[0], pair[1]), GlCalls.lastTexParameter(GL11.GL_TEXTURE_MIN_FILTER));
            assertEquals(ShadowTextures.magFilter(pair[1]), GlCalls.lastTexParameter(GL11.GL_TEXTURE_MAG_FILTER));
            assertEquals(GL12.GL_CLAMP_TO_EDGE, GlCalls.lastTexParameter(GL11.GL_TEXTURE_WRAP_S));
            assertEquals(GL12.GL_CLAMP_TO_EDGE, GlCalls.lastTexParameter(GL11.GL_TEXTURE_WRAP_T));
        }
        verify(TestGl.gl(), Mockito.atLeastOnce()).glTexParameteriv(eq(GL11.GL_TEXTURE_2D), eq(GL33.GL_TEXTURE_SWIZZLE_RGBA),
                aryEq(new int[]{GL11.GL_RED, GL11.GL_RED, GL11.GL_RED, GL11.GL_ONE}));
    }

    // Checks that the largest int resolution reaches GL unchanged for both kinds of texture
    @Test
    void theIntLimitResolutionReachesGlUnchanged() {
        ShadowTextures.createDepth(Integer.MAX_VALUE, false, false, false, false);
        ShadowTextures.createColor(Integer.MAX_VALUE, InternalTextureFormat.RGBA, false, false);
        verify(TestGl.gl(), Mockito.times(2)).glTexImage2D(eq(GL11.GL_TEXTURE_2D), eq(0), anyInt(), eq(Integer.MAX_VALUE),
                eq(Integer.MAX_VALUE), eq(0), anyInt(), anyInt(), nullable(ByteBuffer.class));
    }

    // Checks that zero and negative resolutions fail before any texture is generated
    @Test
    void nonPositiveResolutionsFailBeforeAnyGlWork() {
        for (int resolution : new int[]{0, -1, Integer.MIN_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> ShadowTextures.createDepth(resolution, false, false, false, false));
            assertThrows(IllegalArgumentException.class, () -> ShadowTextures.createColor(resolution, InternalTextureFormat.RGBA, false, false));
        }
        verify(TestGl.gl(), never()).glGenTextures();
        verify(TestGl.gl(), never()).glActiveTexture(anyInt());
    }

    // Checks that a depth texture whose configuration throws is deleted and the scratch unit released
    @Test
    void aDepthTextureThatFailsToConfigureIsDeleted() {
        doThrow(new IllegalStateException("driver")).when(TestGl.gl()).glTexParameteriv(anyInt(), anyInt(), any(int[].class));
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> ShadowTextures.createDepth(16, false, false, false, false));
        assertEquals("driver", failure.getMessage());
        assertEquals(1, GlCalls.deletedTextures().size());
        assertEquals(0, this.gl.activeUnit);
        assertEquals(0, PipelineGl.cachedUnit());
    }

    // Checks the format and filters and clamping of colour textures
    @Test
    void aColourTextureCarriesItsFormatFiltersAndClamp() {
        this.gl.textures[0] = 55;
        int integer = ShadowTextures.createColor(128, InternalTextureFormat.R32UI, false, true);
        assertTrue(integer > 0);
        verify(TestGl.gl()).glTexImage2D(eq(GL11.GL_TEXTURE_2D), eq(0), eq(GL30.GL_R32UI), eq(128), eq(128), eq(0),
                eq(GL30.GL_RED_INTEGER), eq(GL11.GL_UNSIGNED_INT), nullable(ByteBuffer.class));
        assertEquals(GL11.GL_NEAREST, GlCalls.lastTexParameter(GL11.GL_TEXTURE_MIN_FILTER));
        assertEquals(GL11.GL_NEAREST, GlCalls.lastTexParameter(GL11.GL_TEXTURE_MAG_FILTER));
        assertEquals(GL12.GL_CLAMP_TO_EDGE, GlCalls.lastTexParameter(GL11.GL_TEXTURE_WRAP_S));
        assertEquals(GL12.GL_CLAMP_TO_EDGE, GlCalls.lastTexParameter(GL11.GL_TEXTURE_WRAP_T));
        ShadowTextures.createColor(128, InternalTextureFormat.RGBA16F, true, false);
        assertEquals(GL11.GL_LINEAR_MIPMAP_LINEAR, GlCalls.lastTexParameter(GL11.GL_TEXTURE_MIN_FILTER));
        assertEquals(GL11.GL_LINEAR, GlCalls.lastTexParameter(GL11.GL_TEXTURE_MAG_FILTER));
        assertEquals(55, this.gl.textures[0]);
        assertEquals(0, this.gl.textures[ShadowTextures.SETUP_UNIT]);
        assertEquals(0, this.gl.activeUnit);
    }

    // Checks that a colour texture whose allocation throws is deleted and the scratch unit released
    @Test
    void aColourTextureThatFailsToAllocateIsDeleted() {
        doThrow(new IllegalStateException("out of memory")).when(TestGl.gl()).glTexImage2D(anyInt(), anyInt(), anyInt(),
                anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), nullable(ByteBuffer.class));
        assertThrows(IllegalStateException.class, () -> ShadowTextures.createColor(8, InternalTextureFormat.RGBA, false, false));
        assertEquals(1, GlCalls.deletedTextures().size());
        assertEquals(0, this.gl.activeUnit);
    }

    // Checks that a null format is rejected before a texture is generated
    @Test
    void aNullFormatIsRejected() {
        assertThrows(NullPointerException.class, () -> ShadowTextures.createColor(8, null, false, false));
        verify(TestGl.gl(), never()).glGenTextures();
    }

    // Checks that the private constructor runs
    @Test
    void theConstructorIsPrivate() {
        assertNotNull(Mixins.construct(ShadowTextures.class));
    }
}
