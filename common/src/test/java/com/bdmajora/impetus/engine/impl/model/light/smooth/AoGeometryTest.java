package com.bdmajora.impetus.engine.impl.model.light.smooth;

import com.bdmajora.impetus.engine.impl.model.light.DiffuseProvider;
import com.bdmajora.impetus.engine.impl.model.light.data.LightDataAccess;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

// Tests face layouts and constructor refusals of the smooth lighter.
class AoGeometryTest {
    // Gives corner weights that sum to one on every face and every point of a grid.
    @Test
    void cornerWeightsSumToOne() {
        float[] out = new float[4];
        for (ModelQuadFacing facing : ModelQuadFacing.DIRECTIONS) {
            AoNeighborInfo info = AoNeighborInfo.get(facing);
            for (float a = 0; a <= 1; a += 0.25f) {
                for (float b = 0; b <= 1; b += 0.25f) {
                    info.calculateCornerWeights(a, b, 1 - a, out);
                    assertEquals(1f, out[0] + out[1] + out[2] + out[3], 1e-5, facing.name());
                    float depth = info.getDepth(a, b, 1 - a);
                    assertTrue(depth >= 0 && depth <= 1, facing.name());
                }
            }
            assertEquals(4, info.faces.length);
        }
    }

    // Maps corners as a permutation of the four inputs.
    @Test
    void mapCornersIsAPermutation() {
        for (ModelQuadFacing facing : ModelQuadFacing.DIRECTIONS) {
            int[] lm1 = new int[4];
            float[] ao1 = new float[4];
            AoNeighborInfo.get(facing).mapCorners(new int[]{10, 20, 30, 40}, new float[]{1, 2, 3, 4}, lm1, ao1);
            int sum = 0;
            for (int i = 0; i < 4; i++) {
                sum += lm1[i];
                assertEquals(lm1[i] / 10f, ao1[i], facing.name());
            }
            assertEquals(100, sum, facing.name());
        }
    }

    // Rejects an unassigned facing and null.
    @Test
    void rejectsUnassignedFacing() {
        assertThrows(IllegalArgumentException.class, () -> AoNeighborInfo.get(ModelQuadFacing.UNASSIGNED));
        assertThrows(NullPointerException.class, () -> AoNeighborInfo.get(null));
    }

    // Rejects null constructor arguments.
    @Test
    void lighterRejectsNulls() {
        LightDataAccess cache = Mockito.mock(LightDataAccess.class);
        assertThrows(NullPointerException.class, () -> new SmoothLightPipeline(null, DiffuseProvider.NONE, true));
        assertThrows(NullPointerException.class, () -> new SmoothLightPipeline(cache, null, true));
        new SmoothLightPipeline(cache, DiffuseProvider.NONE, false).reset();
    }

    // Tracks completion flags and clears them on reset.
    @Test
    void faceDataFlags() {
        AoFaceData data = new AoFaceData();
        assertFalse(data.hasLightData());
        assertFalse(data.hasUnpackedLightData());
        data.lm[0] = 0x00F000A0;
        data.unpackLightData();
        assertTrue(data.hasUnpackedLightData());
        assertEquals(0xA0, data.bl[0]);
        assertEquals(0xF0, data.sl[0]);
        data.reset();
        assertFalse(data.hasUnpackedLightData());
    }
}
