package com.bdmajora.impetus.engine.impl.model.light.flat;

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

class FlatLightPipelineTest {
    private static final DiffuseProvider HALF = (nx, ny, nz, shade) -> shade ? 0.5f : 1.0f;

    @Test
    void takesLightFromTheBlockInFrontOfTheFace() {
        FakeLightData data = new FakeLightData();
        data.put(0, 0, 0, FakeLightData.stone());
        data.put(0, 1, 0, FakeLightData.air(7));
        data.put(1, 0, 0, FakeLightData.air(3));
        FlatLightPipeline pipeline = new FlatLightPipeline(data, HALF, false);
        ModelQuad quad = ModelQuadTest.top();
        quad.setFlags(ModelQuadFlags.IS_ALIGNED);
        QuadLightData out = new QuadLightData();
        pipeline.calculate(quad, 0, 0, 0, out, ModelQuadFacing.POS_Y, ModelQuadFacing.POS_Y, true, false);
        assertEquals(LightDataAccess.pack(7, 15), out.lm[0]);
        assertEquals(0.5f, out.br[3]);

        pipeline.calculate(quad, 0, 0, 0, out, ModelQuadFacing.UNASSIGNED, ModelQuadFacing.POS_X, false, false);
        assertEquals(LightDataAccess.pack(3, 15), out.lm[0]);
        assertEquals(1.0f, out.br[0]);

        quad.setFlags(ModelQuadFlags.IS_PARALLEL);
        pipeline.calculate(quad, 0, 0, 0, out, ModelQuadFacing.UNASSIGNED, ModelQuadFacing.POS_Y, false, false);
        assertEquals(LightDataAccess.pack(7, 15), out.lm[0]);

        quad.setFlags(0);
        data.put(2, 2, 2, LightDataAccess.packBL(5) | LightDataAccess.packSL(1) | LightDataAccess.packLU(9));
        pipeline.calculate(quad, 2, 2, 2, out, ModelQuadFacing.UNASSIGNED, ModelQuadFacing.POS_Y, false, false);
        assertEquals(LightDataAccess.pack(9, 1), out.lm[0]);

        data.put(4, 4, 4, LightDataAccess.packEM(true) | LightDataAccess.packBL(1));
        pipeline.calculate(quad, 4, 4, 4, out, ModelQuadFacing.POS_Y, ModelQuadFacing.POS_Y, false, false);
        assertEquals(LightDataAccess.FULL_BRIGHT, out.lm[0]);
        assertThrows(IllegalStateException.class, () -> pipeline.calculate(quad, 0, 0, 0, out, ModelQuadFacing.POS_Y, ModelQuadFacing.UNASSIGNED, false, false));
    }

    @Test
    void quadNormalsShadeWhenEnabled() {
        FakeLightData data = new FakeLightData();
        FlatLightPipeline pipeline = new FlatLightPipeline(data, (nx, ny, nz, shade) -> ny, true);
        ModelQuad quad = ModelQuadTest.top();
        QuadLightData out = new QuadLightData();
        pipeline.calculate(quad, 0, 0, 0, out, ModelQuadFacing.POS_Y, ModelQuadFacing.POS_Y, true, false);
        assertEquals(1.0f, out.br[0], 0.01f);
        quad.setFlags(ModelQuadFlags.IS_VANILLA_SHADED);
        pipeline.calculate(quad, 0, 0, 0, out, ModelQuadFacing.POS_X, ModelQuadFacing.POS_X, true, false);
        assertEquals(0f, out.br[0], 0.01f);
        pipeline.reset();
    }
}
