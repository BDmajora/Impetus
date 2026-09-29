package com.bdmajora.impetus.engine.impl.model.light.smooth;

import com.bdmajora.impetus.engine.api.util.NormI8;
import com.bdmajora.impetus.engine.impl.model.light.DiffuseProvider;
import com.bdmajora.impetus.engine.impl.model.light.data.LightDataAccess;
import com.bdmajora.impetus.engine.impl.model.light.data.QuadLightData;
import com.bdmajora.impetus.engine.impl.model.quad.ModelQuad;
import com.bdmajora.impetus.engine.impl.model.quad.ModelQuadTest;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFlags;
import com.bdmajora.testing.FakeLightData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SmoothLightPipelineTest {
    private static FakeLightData world() {
        FakeLightData data = new FakeLightData();
        data.put(0, 0, 0, FakeLightData.stone());
        // A wall beside the top face darkens two corners
        data.put(1, 1, 0, FakeLightData.stone());
        data.put(1, 1, 1, FakeLightData.stone());
        data.put(-1, 1, -1, LightDataAccess.packEM(true) | LightDataAccess.packAO(1.0f));
        return data;
    }

    private static ModelQuad quad(int flags) {
        ModelQuad quad = ModelQuadTest.top();
        quad.setFlags(flags);
        return quad;
    }

    @Test
    void alignedFullFacesMapCornersDirectly() {
        SmoothLightPipeline pipeline = new SmoothLightPipeline(world(), DiffuseProvider.NONE, false);
        QuadLightData out = new QuadLightData();
        pipeline.calculate(quad(ModelQuadFlags.IS_ALIGNED), 0, 0, 0, out, ModelQuadFacing.POS_Y, ModelQuadFacing.POS_Y, true, false);
        assertTrue(out.br[2] < out.br[0], "the corner beside the wall is darker");
        assertTrue(out.lm[0] != 0);
        QuadLightData again = new QuadLightData();
        pipeline.calculate(quad(ModelQuadFlags.IS_PARALLEL), 0, 0, 0, again, ModelQuadFacing.POS_Y, ModelQuadFacing.POS_Y, true, false);
        assertArrayEquals(out.br, again.br);
        pipeline.reset();
        pipeline.calculate(quad(ModelQuadFlags.IS_ALIGNED), 0, 0, 0, again, ModelQuadFacing.POS_Y, ModelQuadFacing.POS_Y, true, false);
        assertArrayEquals(out.br, again.br);
    }

    @Test
    void partialNonParallelAndIrregularFaces() {
        SmoothLightPipeline pipeline = new SmoothLightPipeline(world(), (nx, ny, nz, shade) -> 0.5f, true);
        QuadLightData out = new QuadLightData();
        ModelQuad partial = quad(ModelQuadFlags.IS_ALIGNED | ModelQuadFlags.IS_PARTIAL);
        pipeline.calculate(partial, 0, 0, 0, out, ModelQuadFacing.POS_Y, ModelQuadFacing.POS_Y, true, false);
        assertTrue(out.br[0] > 0 && out.br[0] <= 0.5f);

        ModelQuad inset = quad(0);
        for (int i = 0; i < 4; i++) {
            inset.setY(i, 0.5f);
        }
        pipeline.calculate(inset, 0, 0, 0, out, ModelQuadFacing.POS_Y, ModelQuadFacing.POS_Y, true, true);
        assertTrue(out.br[0] > 0);
        pipeline.calculate(inset, 0, 0, 0, out, ModelQuadFacing.POS_Y, ModelQuadFacing.POS_Y, true, false);
        for (int i = 0; i < 4; i++) {
            inset.setY(i, 0.0f);
        }
        pipeline.calculate(inset, 0, 0, 0, out, ModelQuadFacing.POS_Y, ModelQuadFacing.POS_Y, true, true);
        for (int i = 0; i < 4; i++) {
            inset.setY(i, 1.0f);
        }
        pipeline.calculate(inset, 0, 0, 0, out, ModelQuadFacing.POS_Y, ModelQuadFacing.POS_Y, true, true);
        pipeline.calculate(inset, 0, 0, 0, out, ModelQuadFacing.POS_Y, ModelQuadFacing.POS_Y, true, false);

        ModelQuad slanted = quad(ModelQuadFlags.IS_VANILLA_SHADED);
        slanted.setY(0, 0f);
        slanted.setY(1, 0f);
        pipeline.calculate(slanted, 0, 0, 0, out, ModelQuadFacing.POS_Y, ModelQuadFacing.POS_Y, true, true);
        ModelQuad diagonal = quad(0);
        diagonal.setY(0, 0f);
        diagonal.setY(1, 0f);
        assertEquals(ModelQuadFacing.UNASSIGNED, diagonal.getNormalFace());
        pipeline.calculate(diagonal, 0, 0, 0, out, ModelQuadFacing.POS_Y, ModelQuadFacing.POS_Y, true, true);
        assertTrue(out.br[0] > 0);
        pipeline.calculate(diagonal, 0, 0, 0, out, ModelQuadFacing.POS_Y, ModelQuadFacing.POS_Y, true, false);
        diagonal.setForgeNormal(0, NormI8.pack(0.577f, 0.577f, 0.577f));
        pipeline.calculate(diagonal, 0, 0, 0, out, ModelQuadFacing.POS_Y, ModelQuadFacing.POS_Y, true, true);
    }

    @Test
    void neighbourInfoCoversEveryFace() {
        float[] weights = new float[4];
        int[] lm0 = {1, 2, 3, 4}, lm1 = new int[4];
        float[] ao0 = {1, 2, 3, 4}, ao1 = new float[4];
        for (ModelQuadFacing facing : ModelQuadFacing.DIRECTIONS) {
            AoNeighborInfo info = AoNeighborInfo.get(facing);
            info.calculateCornerWeights(0.25f, 0.5f, 0.75f, weights);
            assertEquals(1f, weights[0] + weights[1] + weights[2] + weights[3], 1e-5);
            info.mapCorners(lm0, ao0, lm1, ao1);
            assertEquals(10, lm1[0] + lm1[1] + lm1[2] + lm1[3]);
            assertTrue(info.getDepth(0.25f, 0.5f, 0.75f) >= 0);
            assertEquals(4, info.faces.length);
        }
        assertThrows(IllegalArgumentException.class, () -> AoNeighborInfo.get(ModelQuadFacing.UNASSIGNED));
        assertEquals(0b01, AoCompletionFlags.HAS_LIGHT_DATA);
        new AoCompletionFlags();
    }

    @Test
    void faceDataHandlesOpaqueCornersAndZeroLight() {
        FakeLightData data = new FakeLightData();
        data.put(0, 1, 0, FakeLightData.stone());
        data.put(1, 1, 0, FakeLightData.stone() | LightDataAccess.packOP(true));
        data.put(0, 1, 1, FakeLightData.stone());
        data.put(-1, 1, 0, FakeLightData.stone());
        data.put(0, 1, -1, FakeLightData.stone());
        data.put(0, 0, 0, FakeLightData.stone());
        AoFaceData face = new AoFaceData();
        assertFalse(face.hasLightData());
        face.initLightData(data, 0, 0, 0, ModelQuadFacing.POS_Y, true);
        assertTrue(face.hasLightData());
        assertFalse(face.hasUnpackedLightData());
        face.unpackLightData();
        assertTrue(face.hasUnpackedLightData());
        float[] w = {0.25f, 0.25f, 0.25f, 0.25f};
        assertTrue(face.getBlendedShade(w) >= 0);
        assertTrue(face.getBlendedSkyLight(w) >= 0);
        assertTrue(face.getBlendedBlockLight(w) >= 0);
        face.reset();
        assertFalse(face.hasLightData());
        FakeLightData dark = new FakeLightData();
        dark.put(5, 5, 5, 0 | LightDataAccess.packAO(0.1f));
        AoFaceData unlit = new AoFaceData();
        unlit.initLightData(dark, 5, 5, 5, ModelQuadFacing.NEG_Y, false);
        assertTrue(unlit.hasLightData());
    }
}
