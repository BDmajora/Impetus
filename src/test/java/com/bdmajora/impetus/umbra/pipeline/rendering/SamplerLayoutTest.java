package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.shaderpack.texture.TextureStage;
import com.bdmajora.impetus.umbra.targets.UmbraRenderTargets;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SamplerLayoutTest {
    @Test
    void everyColourTargetAnswersByBothNamesInBothLayouts() {
        Map<String, Integer> names = SamplerLayout.colorTargetsByName();
        for (int i = 0; i < UmbraRenderTargets.MAX_COLOR_BUFFERS; i++) {
            assertEquals(i, names.get("colortex" + i));
            // Fullscreen programs keep colortexN on unit N
            assertEquals(i, SamplerLayout.fullscreen().get("colortex" + i));
        }
        for (int i = 0; i < UmbraRenderTargets.LEGACY_COLOR_TARGETS.length; i++) {
            assertEquals(i, names.get(UmbraRenderTargets.LEGACY_COLOR_TARGETS[i]));
            assertEquals(i, SamplerLayout.fullscreen().get(UmbraRenderTargets.LEGACY_COLOR_TARGETS[i]));
        }
        assertEquals(16 + 8, names.size());
    }

    @Test
    void theGbufferStageSamplesOnlyGaux1To4OnOptiFinesAuxUnits() {
        for (int i = 0; i < UmbraRenderTargets.MAX_COLOR_BUFFERS; i++) {
            int expected = i >= 4 && i <= 7 ? i + 3 : -1;
            assertEquals(expected, SamplerLayout.gbufferColorUnit(i), "colortex" + i);
            assertEquals(expected == -1 ? null : expected, SamplerLayout.gbuffer().get("colortex" + i));
        }
        assertEquals(7, SamplerLayout.gbuffer().get("gaux1"));
        assertEquals(10, SamplerLayout.gbuffer().get("gaux4"));
        assertNull(SamplerLayout.gbuffer().get("gcolor"));
        assertEquals(-1, SamplerLayout.gbufferColorUnit(-1));
        assertEquals(-1, SamplerLayout.gbufferColorUnit(99));
    }

    @Test
    void theTwoLayoutsPlaceDepthShadowAndNoiseWhereOptiFineDoes() {
        Map<String, Integer> fullscreen = SamplerLayout.fullscreen();
        Map<String, Integer> gbuffer = SamplerLayout.gbuffer();
        Object[][] fixed = {
                {"depthtex0", 16, 6}, {"gdepthtex", 16, 6}, {"depthtex1", 17, 12}, {"depthtex2", 18, 18},
                {"dhDepthTex", 35, 35}, {"dhDepthTex0", 35, 35}, {"dhDepthTex1", 36, 36},
                {"shadowtex0", 19, 4}, {"shadowtex0DH", 19, 4}, {"shadow", 19, 4}, {"watershadow", 19, 4},
                {"shadowtex1", 20, 5}, {"shadowtex1DH", 20, 5}, {"shadowtex0HW", 24, 24}, {"shadowtex1HW", 25, 25},
                {"shadowcolor", 21, 13}, {"shadowcolor0", 21, 13}, {"shadowcolor1", 22, 14}, {"noisetex", 23, 15},
                {"normals", 2, 2}, {"texNorm", 2, 2}, {"specular", 3, 3}, {"texSpecular", 3, 3}};
        for (Object[] row : fixed) {
            assertEquals(row[1], fullscreen.get(row[0]), "fullscreen " + row[0]);
            assertEquals(row[2], gbuffer.get(row[0]), "gbuffer " + row[0]);
        }
        // iris_overlay is gbuffer-only; no fullscreen program declares it
        assertEquals(11, gbuffer.get("iris_overlay"));
        assertNull(fullscreen.get("iris_overlay"));
        for (int i = 2; i < 8; i++) {
            assertEquals(37 + i - 2, SamplerLayout.shadowColorUnit(i));
            assertEquals(37 + i - 2, fullscreen.get("shadowcolor" + i));
            assertEquals(37 + i - 2, gbuffer.get("shadowcolor" + i));
        }
    }

    @Test
    void noTwoSamplerNamesOfOneKindCollideAndThePackRangeStaysBelowTheFixedHighUnits() {
        // Every fixed high unit the frame binds is distinct
        Set<Integer> units = new HashSet<>();
        for (int unit : SamplerLayout.FRAME_UNITS) {
            assertTrue(units.add(unit), "unit " + unit + " twice");
        }
        assertTrue(SamplerLayout.CUSTOM_TEX_FIRST_UNIT <= SamplerLayout.CUSTOM_TEX_LAST_UNIT);
        int[] fixedHigh = {SamplerLayout.MIPMAP_SCRATCH_UNIT, SamplerLayout.DEPTH_COPY_SCRATCH_UNIT, SamplerLayout.OVERLAY_TEX_UNIT,
                SamplerLayout.DH_DEPTH_TEX_0_UNIT, SamplerLayout.DH_DEPTH_TEX_1_UNIT, SamplerLayout.shadowColorUnit(7)};
        for (int unit : fixedHigh) {
            assertTrue(unit > SamplerLayout.CUSTOM_TEX_LAST_UNIT, "unit " + unit + " inside the pack range");
            // All under the 48 combined units GL 3.3 guarantees
            assertTrue(unit < 48);
        }
    }

    @Test
    void stagesMapOntoTheirLayoutAndTheTablesAreReadOnly() {
        for (TextureStage stage : TextureStage.values()) {
            Map<String, Integer> expected = stage == TextureStage.GBUFFERS_AND_SHADOW ? SamplerLayout.gbuffer() : SamplerLayout.fullscreen();
            assertSame(expected, SamplerLayout.forStage(stage));
            assertSame(expected, SamplerLayout.byStage().get(stage));
        }
        assertSame(SamplerLayout.byStage(), SamplerLayout.byStage());
        assertTrue(SamplerLayout.isGbufferLayout(SamplerLayout.gbuffer()));
        assertFalse(SamplerLayout.isGbufferLayout(SamplerLayout.fullscreen()));
        // A copy still answers, and a table without the marker is not the gbuffer one
        assertTrue(SamplerLayout.isGbufferLayout(new HashMap<>(SamplerLayout.gbuffer())));
        assertFalse(SamplerLayout.isGbufferLayout(Map.of()));
        assertThrows(UnsupportedOperationException.class, () -> SamplerLayout.fullscreen().put("x", 1));
        assertThrows(UnsupportedOperationException.class, () -> SamplerLayout.gbuffer().put("x", 1));
        assertThrows(UnsupportedOperationException.class, () -> SamplerLayout.colorTargetsByName().put("x", 1));
        assertThrows(UnsupportedOperationException.class, () -> SamplerLayout.byStage().put(TextureStage.BEGIN, Map.of()));
    }
}
