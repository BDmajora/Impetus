package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.compat.dh.DhCompat;
import com.bdmajora.impetus.umbra.gl.texture.PlainTexture;
import com.bdmajora.impetus.umbra.gl.texture.StubShadowMap;
import com.bdmajora.impetus.umbra.pipeline.CustomImageManager;
import com.bdmajora.impetus.umbra.pipeline.CustomTextureManager;
import com.bdmajora.impetus.umbra.pipeline.UmbraShadowRenderer;
import com.bdmajora.impetus.umbra.targets.NoiseTexture;
import com.bdmajora.impetus.umbra.targets.UmbraRenderTargets;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TextureBinderTest {
    private PipelineGl gl;
    private UmbraRenderTargets targets;
    private NoiseTexture noise;
    private CustomTextureManager customTextures;
    private CustomImageManager customImages;
    private ShadowSamplers samplers;
    private final AtomicReference<DhCompat> dh = new AtomicReference<>();
    private TextureBinder binder;

    @BeforeAll
    static void bootstrap() {
        PipelineGl.bootstrap();
    }

    @BeforeEach
    void open() {
        this.gl = new PipelineGl();
        this.targets = new UmbraRenderTargets(320, 240);
        this.noise = mock(NoiseTexture.class);
        when(this.noise.getTextureId()).thenReturn(55);
        this.customTextures = mock(CustomTextureManager.class);
        when(this.customTextures.getNoiseTextureId()).thenReturn(-1);
        this.customImages = mock(CustomImageManager.class);
        when(this.customImages.isEmpty()).thenReturn(true);
        this.samplers = new ShadowSamplers(false);
        this.binder = new TextureBinder(this.targets, this.noise, this.customTextures, this.customImages, this.samplers, this.dh::get);
    }

    @AfterEach
    void close() {
        this.binder.destroy();
        this.samplers.destroy();
        this.targets.destroy();
        this.gl.close();
    }

    private int texture(String field) {
        Object texture = Mixins.get(this.binder, field);
        return texture instanceof PlainTexture plain ? plain.getTextureId() : ((StubShadowMap) texture).getTextureId();
    }

    private int depth0() {
        return this.targets.getDepthTexture().getTextureId();
    }

    private int depth1() {
        return this.targets.getDepthTextureNoTranslucents().getTextureId();
    }

    @Test
    void theFrameLongTexturesLandOnTheirFixedUnitsAndTheSelectorReturnsToZero() {
        this.binder.bindFrameTextures();
        assertEquals(55, this.gl.textures[SamplerLayout.NOISE_TEX_UNIT]);
        assertEquals(55, this.gl.textures[SamplerLayout.GBUFFER_NOISE_TEX_UNIT]);
        assertEquals(texture("noOverlay"), this.gl.textures[SamplerLayout.OVERLAY_TEX_UNIT]);
        assertEquals(texture("noOverlay"), this.gl.textures[SamplerLayout.GBUFFER_OVERLAY_UNIT]);
        assertEquals(texture("stubShadowMap"), this.gl.textures[SamplerLayout.SHADOW_TEX_0_UNIT]);
        assertEquals(texture("stubShadowMap"), this.gl.textures[SamplerLayout.SHADOW_TEX_1_UNIT]);
        assertEquals(0, this.gl.activeUnit);
        assertEquals(0, PipelineGl.cachedUnit());
        // A pack-supplied texture.noise replaces the generated one
        when(this.customTextures.getNoiseTextureId()).thenReturn(77);
        this.binder.bindNoise();
        assertEquals(77, this.gl.textures[SamplerLayout.NOISE_TEX_UNIT]);
        assertEquals(77, this.gl.textures[SamplerLayout.GBUFFER_NOISE_TEX_UNIT]);
    }

    @Test
    void depthGoesOnBothLayoutsWithNoSamplerObjectAndDhDepthFallsBackToTheTerrainDepth() {
        Arrays.fill(this.gl.samplers, 9);
        this.binder.bindDepth();
        int[] units = {16, 17, 18, 6, 12, 35, 36};
        int[] expected = {depth0(), depth1(), this.targets.getDepthTextureNoHand().getTextureId(), depth0(), depth1(), depth0(), depth1()};
        for (int i = 0; i < units.length; i++) {
            assertEquals(expected[i], this.gl.textures[units[i]], "unit " + units[i]);
            assertEquals(0, this.gl.samplers[units[i]], "sampler on " + units[i]);
        }
        assertEquals(depth0(), PipelineGl.cachedTexture(6));
        // Once DH has rendered, its LOD depth replaces the stand-in; a texture it has not made yet keeps it
        DhCompat compat = mock(DhCompat.class);
        when(compat.getDepthTex()).thenReturn(501);
        when(compat.getDepthTexNoTranslucent()).thenReturn(0);
        this.dh.set(compat);
        this.binder.bindDepth();
        assertEquals(501, this.gl.textures[SamplerLayout.DH_DEPTH_TEX_0_UNIT]);
        assertEquals(depth1(), this.gl.textures[SamplerLayout.DH_DEPTH_TEX_1_UNIT]);
        assertEquals(0, this.gl.activeUnit);
    }

    @Test
    void thePbrFallbacksGoOnUnits2And3ThroughTheCache() {
        this.binder.bindPbr();
        assertEquals(texture("defaultNormals"), this.gl.textures[2]);
        assertEquals(texture("defaultSpecular"), this.gl.textures[3]);
        assertEquals(texture("defaultNormals"), PipelineGl.cachedTexture(2));
        assertEquals(0, PipelineGl.cachedUnit());
    }

    @Test
    void withoutAShadowPassTheStubIsComparedOnThePlainUnitsAndTheColoursAreEmpty() {
        Arrays.fill(this.gl.textures, 1234);
        this.binder.bindShadow();
        int stub = texture("stubShadowMap");
        for (int unit : new int[]{19, 20, 4, 5}) {
            assertEquals(stub, this.gl.textures[unit]);
        }
        assertEquals(this.samplers.compareFor(0), this.gl.samplers[19]);
        assertEquals(this.samplers.compareFor(1), this.gl.samplers[20]);
        assertEquals(this.samplers.compareFor(0), this.gl.samplers[4]);
        assertEquals(this.samplers.compareFor(1), this.gl.samplers[5]);
        // The *HW aliases are not held hostage for packs that do not use them
        assertEquals(1234, this.gl.textures[24]);
        assertEquals(1234, this.gl.textures[25]);
        for (int unit : new int[]{21, 22, 13, 14, 37, 38, 39, 40, 41, 42}) {
            assertEquals(0, this.gl.textures[unit], "unit " + unit);
        }
        assertEquals(0, this.gl.activeUnit);
    }

    @Test
    void underSeparateHardwareSamplersThePlainUnitsReadRawDepthAndTheAliasesCompare() {
        ShadowSamplers separate = new ShadowSamplers(true);
        TextureBinder binder = new TextureBinder(this.targets, this.noise, this.customTextures, this.customImages, separate, this.dh::get);
        try {
            UmbraShadowRenderer shadow = mock(UmbraShadowRenderer.class);
            when(shadow.getDepthTextureId()).thenReturn(70);
            when(shadow.getDepthTextureNoTranslucentsId()).thenReturn(71);
            for (int i = 0; i < 8; i++) {
                when(shadow.getColorTextureId(i)).thenReturn(80 + i);
            }
            binder.setShadowRenderer(shadow);
            binder.bindShadow();
            assertEquals(70, this.gl.textures[19]);
            assertEquals(71, this.gl.textures[20]);
            assertEquals(0, this.gl.samplers[19]);
            assertEquals(0, this.gl.samplers[5]);
            assertEquals(70, this.gl.textures[24]);
            assertEquals(71, this.gl.textures[25]);
            assertEquals(separate.compareFor(0), this.gl.samplers[24]);
            assertEquals(separate.compareFor(1), this.gl.samplers[25]);
            assertEquals(80, this.gl.textures[21]);
            assertEquals(81, this.gl.textures[14]);
            for (int i = 2; i < 8; i++) {
                assertEquals(80 + i, this.gl.textures[SamplerLayout.shadowColorUnit(i)]);
            }
        } finally {
            binder.destroy();
            separate.destroy();
        }
    }

    @Test
    void packResourcesBindWithABarrierOnlyWhenThereAreImagesAndNeverAfterDestroy() {
        this.binder.bindPackResources();
        verify(TestGl.gl(), never()).glMemoryBarrier(anyInt());
        verify(this.customTextures).bindAll();
        verify(this.customImages).bindAll();
        when(this.customImages.isEmpty()).thenReturn(false);
        this.binder.bindPackResources();
        verify(TestGl.gl()).glMemoryBarrier(0x00000020 | 0x00000008 | 0x00002000);
        this.binder.destroy();
        Mockito.clearInvocations(this.customTextures, this.customImages, TestGl.gl());
        this.binder.bindPackResources();
        verify(this.customTextures, never()).bindAll();
        verify(TestGl.gl(), never()).glMemoryBarrier(anyInt());
    }

    @Test
    void aPassBindsOnlyTheTargetsItUsesOnTheirOwnUnits() {
        Arrays.fill(this.gl.samplers, 9);
        this.gl.textures[1] = 1234;
        int[] samplers = new int[16];
        samplers[0] = 100;
        samplers[3] = 103;
        samplers[9] = 109;
        samplers[15] = 115;
        TextureBinder.bindColorSamplers(samplers);
        assertEquals(100, this.gl.textures[0]);
        assertEquals(103, this.gl.textures[3]);
        assertEquals(109, this.gl.textures[9]);
        assertEquals(115, this.gl.textures[15]);
        assertEquals(103, PipelineGl.cachedTexture(3));
        assertEquals(1234, this.gl.textures[1]);
        assertEquals(0, this.gl.samplers[9]);
        assertEquals(9, this.gl.samplers[1]);
        assertEquals(0, this.gl.activeUnit);
    }

    @Test
    void restoringUnbindsEveryUnitTheFrameUsedAndLeavesTheCacheAgreeing() {
        Arrays.fill(this.gl.samplers, 9);
        this.binder.bindFrameTextures();
        this.binder.bindDepth();
        this.binder.bindShadow();
        TextureBinder.bindColorSamplers(new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16});
        this.binder.restoreUnits();
        verify(this.customTextures).unbindAll();
        verify(this.customImages).unbindAll();
        for (int unit = 0; unit < 26; unit++) {
            assertEquals(0, this.gl.textures[unit], "unit " + unit);
            assertEquals(0, this.gl.samplers[unit], "sampler on " + unit);
        }
        for (int unit : new int[]{35, 36}) {
            assertEquals(0, this.gl.textures[unit]);
        }
        for (int unit = 0; unit < 8; unit++) {
            assertEquals(0, PipelineGl.cachedTexture(unit));
        }
        assertEquals(0, this.gl.activeUnit);
        assertEquals(0, PipelineGl.cachedUnit());
    }

    @Test
    void destroyFreesTheFallbackTexturesAndAFailedConstructionFreesWhatItMade() {
        int[] made4 = {texture("defaultNormals"), texture("defaultSpecular"), texture("noOverlay"), texture("stubShadowMap")};
        this.binder.destroy();
        for (int texture : made4) {
            verify(TestGl.gl()).glDeleteTextures(texture);
        }
        AtomicInteger made = new AtomicInteger();
        when(TestGl.gl().glGenTextures()).thenAnswer(inv -> {
            if (made.incrementAndGet() == 4) {
                throw new IllegalStateException("out of textures");
            }
            return 900 + made.get();
        });
        Mockito.clearInvocations(TestGl.gl());
        assertThrows(IllegalStateException.class, () -> new TextureBinder(this.targets, this.noise, this.customTextures,
                this.customImages, this.samplers, this.dh::get));
        verify(TestGl.gl()).glDeleteTextures(901);
        verify(TestGl.gl()).glDeleteTextures(902);
        verify(TestGl.gl()).glDeleteTextures(903);
    }

    @Test
    void aFailureOnTheSecondTextureFreesOnlyTheFirst() {
        AtomicInteger made = new AtomicInteger();
        when(TestGl.gl().glGenTextures()).thenAnswer(inv -> {
            if (made.incrementAndGet() == 2) {
                throw new IllegalStateException("out of textures");
            }
            return 950 + made.get();
        });
        Mockito.clearInvocations(TestGl.gl());
        assertThrows(IllegalStateException.class, () -> new TextureBinder(this.targets, this.noise, this.customTextures,
                this.customImages, this.samplers, this.dh::get));
        verify(TestGl.gl()).glDeleteTextures(951);
        verify(TestGl.gl(), Mockito.times(1)).glDeleteTextures(anyInt());
    }

    @Test
    void bothLodDepthTexturesReplaceTheirStandInsOnceDhHasMadeThem() {
        DhCompat compat = mock(DhCompat.class);
        when(compat.getDepthTex()).thenReturn(501);
        when(compat.getDepthTexNoTranslucent()).thenReturn(502);
        this.dh.set(compat);
        this.binder.bindDepth();
        assertEquals(501, this.gl.textures[SamplerLayout.DH_DEPTH_TEX_0_UNIT]);
        assertEquals(502, this.gl.textures[SamplerLayout.DH_DEPTH_TEX_1_UNIT]);
    }
}
