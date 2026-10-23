package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.pipeline.CustomImageManager;
import com.bdmajora.impetus.umbra.pipeline.CustomTextureManager;
import com.bdmajora.impetus.umbra.pipeline.UmbraShadowRenderer;
import com.bdmajora.impetus.umbra.targets.UmbraRenderTarget;
import com.bdmajora.impetus.umbra.targets.UmbraRenderTargets;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL15;
import org.mockito.Mockito;

import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImageUnitsTest {
    private PipelineGl gl;
    private UmbraRenderTargets targets;
    private CustomImageManager images;
    private UmbraShadowRenderer shadow;

    @BeforeAll
    static void bootstrap() {
        PipelineGl.bootstrap();
    }

    @BeforeEach
    void open() {
        this.gl = new PipelineGl();
        this.targets = new UmbraRenderTargets(4, 4);
        this.images = mock(CustomImageManager.class);
        when(this.images.getNextAvailableImageUnit()).thenReturn(2);
        when(this.images.getHardwareImageUnits()).thenReturn(6);
        this.shadow = mock(UmbraShadowRenderer.class);
        for (int i = 0; i < 8; i++) {
            when(this.shadow.getColorTextureId(i)).thenReturn(i == 1 ? 0 : 500 + i);
            when(this.shadow.getColorInternalFormat(i)).thenReturn(0x8058);
        }
    }

    @AfterEach
    void close() {
        this.targets.destroy();
        this.gl.close();
    }

    private static BitSet bits(int... indices) {
        BitSet set = new BitSet();
        for (int index : indices) {
            set.set(index);
        }
        return set;
    }

    @Test
    void unitsAreHandedOutAscendingAboveTheCustomImagesUntilTheHardwareRunsOut() {
        ImageUnits units = new ImageUnits();
        assertTrue(units.isEmpty());
        // colortex3, 5 and 9 on units 2, 3, 4; colortex12 still fits on 5; colortex15 is out of units
        units.allocateRenderTargets(bits(3, 5, 9, 12, 15, 40), this.images, this.targets);
        // A target only a compute writes still has to exist
        assertNotNull(this.targets.get(3));
        assertNotNull(this.targets.get(12));
        assertNull(this.targets.get(15));
        // Shadow colours continue after them at unit 6, which is the limit, so none fit
        units.allocateShadowColors(bits(0, 1, 2), this.images, this.shadow);
        Map<String, CustomTextureManager.Override> overrides = new LinkedHashMap<>();
        units.addOverrides(overrides);
        assertEquals(java.util.List.of("colorimg3", "colorimg5", "colorimg9", "colorimg12"), java.util.List.copyOf(overrides.keySet()));
        assertEquals(2, overrides.get("colorimg3").unit);
        assertEquals(5, overrides.get("colorimg12").unit);
        assertEquals(-1, overrides.get("colorimg5").colorTarget);
        assertFalse(units.isEmpty());
    }

    @Test
    void shadowColourImagesNeedTheShadowPassToOwnTheBuffer() {
        when(this.images.getHardwareImageUnits()).thenReturn(16);
        ImageUnits units = new ImageUnits();
        units.allocateShadowColors(bits(0, 1, 2), this.images, null);
        assertTrue(units.isEmpty());
        units.allocateRenderTargets(bits(4), this.images, this.targets);
        // shadowcolor1 has no texture; the others follow colortex4's unit 2
        units.allocateShadowColors(bits(0, 1, 2, 9), this.images, this.shadow);
        Map<String, CustomTextureManager.Override> overrides = new LinkedHashMap<>();
        units.addOverrides(overrides);
        assertEquals(3, overrides.get("shadowcolorimg0").unit);
        assertNull(overrides.get("shadowcolorimg1"));
        assertEquals(4, overrides.get("shadowcolorimg2").unit);
        assertEquals(3, overrides.size());
    }

    @Test
    void bindingFollowsTheFlipStateAndTheTwoHalvesAreIndependent() {
        when(this.images.getHardwareImageUnits()).thenReturn(16);
        ImageUnits units = new ImageUnits();
        units.allocateRenderTargets(bits(4, 6), this.images, this.targets);
        UmbraRenderTarget four = this.targets.get(4);
        UmbraRenderTarget six = this.targets.get(6);
        units.bind(bits(6), this.targets, this.shadow);
        verify(TestGl.gl()).glBindImageTexture(2, four.getMainTexture(), 0, false, 0, GL15.GL_READ_WRITE,
                four.getInternalFormat().getInternalFormat());
        verify(TestGl.gl()).glBindImageTexture(3, six.getAltTexture(), 0, false, 0, GL15.GL_READ_WRITE,
                six.getInternalFormat().getInternalFormat());
        Mockito.clearInvocations(TestGl.gl());
        // A pack using only shadowcolorimg still gets its images bound
        ImageUnits shadowOnly = new ImageUnits();
        shadowOnly.allocateShadowColors(bits(2), this.images, this.shadow);
        assertFalse(shadowOnly.isEmpty());
        shadowOnly.bind(new BitSet(), this.targets, this.shadow);
        verify(TestGl.gl()).glBindImageTexture(2, 502, 0, false, 0, GL15.GL_READ_WRITE, 0x8058);
        Mockito.clearInvocations(TestGl.gl());
        // No shadow pass to read from, or nothing allocated, binds nothing
        shadowOnly.bind(new BitSet(), this.targets, null);
        new ImageUnits().bind(new BitSet(), this.targets, this.shadow);
        verify(TestGl.gl(), never()).glBindImageTexture(anyInt(), anyInt(), anyInt(), anyBoolean(), anyInt(), anyInt(), anyInt());
    }

    @Test
    void aTargetDestroyedUnderTheAllocationIsSkipped() {
        when(this.images.getHardwareImageUnits()).thenReturn(16);
        ImageUnits units = new ImageUnits();
        units.allocateRenderTargets(bits(4), this.images, this.targets);
        UmbraRenderTargets empty = new UmbraRenderTargets(4, 4);
        try {
            units.bind(new BitSet(), empty, this.shadow);
            verify(TestGl.gl(), never()).glBindImageTexture(anyInt(), anyInt(), anyInt(), anyBoolean(), anyInt(), anyInt(), anyInt());
        } finally {
            empty.destroy();
        }
    }
}
