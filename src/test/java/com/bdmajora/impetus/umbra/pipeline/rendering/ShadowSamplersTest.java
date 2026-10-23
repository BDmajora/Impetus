package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.gl.sampler.ShadowSamplerKinds;
import com.bdmajora.impetus.umbra.shaderpack.ConstDirectives;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.mockito.Mockito;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShadowSamplersTest {
    private static ShadowSamplerKinds kinds(boolean raw0, boolean raw1) {
        return Mixins.construct(ShadowSamplerKinds.class, raw0, raw1);
    }

    @Test
    void fourFilteringFlavoursAreMadeWithAndWithoutDepthComparison() {
        ShadowSamplers samplers = new ShadowSamplers(false);
        verify(TestGl.gl(), times(8)).glGenSamplers();
        int[][] filters = {{GL11.GL_LINEAR, GL11.GL_LINEAR}, {GL11.GL_NEAREST, GL11.GL_NEAREST},
                {GL11.GL_LINEAR, GL11.GL_LINEAR_MIPMAP_LINEAR}, {GL11.GL_NEAREST, GL11.GL_NEAREST_MIPMAP_NEAREST}};
        // Names come out 1..8 as compare, raw per flavour
        for (int flavour = 0; flavour < 4; flavour++) {
            for (int compare = 0; compare < 2; compare++) {
                int sampler = flavour * 2 + compare + 1;
                verify(TestGl.gl()).glSamplerParameteri(sampler, GL11.GL_TEXTURE_MAG_FILTER, filters[flavour][0]);
                verify(TestGl.gl()).glSamplerParameteri(sampler, GL11.GL_TEXTURE_MIN_FILTER, filters[flavour][1]);
                verify(TestGl.gl()).glSamplerParameteri(sampler, GL14.GL_TEXTURE_COMPARE_MODE,
                        compare == 0 ? GL14.GL_COMPARE_R_TO_TEXTURE : GL11.GL_NONE);
            }
        }
        assertEquals(1, samplers.compareFor(0));
        assertEquals(2, samplers.rawFor(1));
        assertFalse(samplers.separateHardwareSamplers());
    }

    @Test
    void theFlagsPickTheFlavourPerShadowTexture() {
        ShadowSamplers samplers = new ShadowSamplers(false);
        samplers.parse(new ConstDirectives(String.join("\n",
                "const bool shadowHardwareFiltering = true;",
                "const bool shadowHardwareFiltering1 = false;",
                "const bool generateShadowMipmap = true;",
                "const bool shadowtex1Mipmap = false;",
                "const bool shadowtex0Nearest = true;")));
        assertArrayEquals(new boolean[]{true, false}, samplers.hardwareFiltering());
        assertArrayEquals(new boolean[]{true, false}, samplers.mipmap());
        assertArrayEquals(new boolean[]{true, false}, samplers.nearest());
        // shadowtex0: mipmapped + nearest = flavour 3; shadowtex1: plain linear = flavour 0
        assertEquals(7, samplers.compareFor(0));
        assertEquals(8, samplers.rawFor(0));
        assertEquals(1, samplers.compareFor(1));
        assertEquals(2, samplers.rawFor(1));
        // A later parse starts from all-false; the unsuffixed and MinMag spellings both reach a texture
        samplers.parse(new ConstDirectives(String.join("\n",
                "const bool shadowtexMipmap = true;",
                "const bool shadowtexNearest = true;",
                "const bool shadow1MinMagNearest = true;")));
        assertArrayEquals(new boolean[]{false, false}, samplers.hardwareFiltering());
        assertArrayEquals(new boolean[]{true, false}, samplers.mipmap());
        assertArrayEquals(new boolean[]{true, true}, samplers.nearest());
        assertEquals(3, samplers.compareFor(1));
    }

    @Test
    void applyPutsEachProgramsDeclaredFlavourOnItsStagesShadowUnits() {
        ShadowSamplers samplers = new ShadowSamplers(false);
        samplers.apply(kinds(true, false), false);
        verify(TestGl.gl()).glBindSampler(SamplerLayout.SHADOW_TEX_0_UNIT, samplers.rawFor(0));
        verify(TestGl.gl()).glBindSampler(SamplerLayout.SHADOW_TEX_1_UNIT, samplers.compareFor(1));
        samplers.apply(kinds(false, true), true);
        verify(TestGl.gl()).glBindSampler(SamplerLayout.GBUFFER_SHADOW_TEX_0_UNIT, samplers.compareFor(0));
        verify(TestGl.gl()).glBindSampler(SamplerLayout.GBUFFER_SHADOW_TEX_1_UNIT, samplers.rawFor(1));
        Mockito.clearInvocations(TestGl.gl());
        samplers.apply(null, false);
        verify(TestGl.gl(), never()).glBindSampler(anyInt(), anyInt());
        samplers.destroy();
        samplers.apply(ShadowSamplerKinds.ALL_COMPARE, false);
        verify(TestGl.gl(), never()).glBindSampler(anyInt(), anyInt());
    }

    @Test
    void underSeparateHardwareSamplersThePackOwnsThePlainUnits() {
        ShadowSamplers samplers = new ShadowSamplers(true);
        assertTrue(samplers.separateHardwareSamplers());
        samplers.apply(kinds(true, true), false);
        verify(TestGl.gl(), never()).glBindSampler(anyInt(), anyInt());
    }

    @Test
    void destroyDeletesAllEightOnce() {
        ShadowSamplers samplers = new ShadowSamplers(false);
        samplers.destroy();
        samplers.destroy();
        for (int sampler = 1; sampler <= 8; sampler++) {
            verify(TestGl.gl()).glDeleteSamplers(sampler);
        }
        verify(TestGl.gl(), times(8)).glDeleteSamplers(anyInt());
    }

    @Test
    void aFailedConstructionFreesWhatItMade() {
        AtomicInteger made = new AtomicInteger();
        when(TestGl.gl().glGenSamplers()).thenAnswer(inv -> {
            if (made.incrementAndGet() == 4) {
                throw new IllegalStateException("out of samplers");
            }
            return made.get();
        });
        assertThrows(IllegalStateException.class, () -> new ShadowSamplers(false));
        for (int sampler = 1; sampler <= 3; sampler++) {
            verify(TestGl.gl()).glDeleteSamplers(sampler);
        }
    }
}
