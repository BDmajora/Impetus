package com.bdmajora.impetus.engine.impl.model.light.data;

import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;
import com.bdmajora.testing.FakeLightData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LightDataAccessTest {
    @Test
    void packsEveryFieldIndependently() {
        int word = LightDataAccess.packBL(3) | LightDataAccess.packSL(7) | LightDataAccess.packLU(9) | LightDataAccess.packAO(0.5f)
                | LightDataAccess.packEM(true) | LightDataAccess.packOP(true) | LightDataAccess.packFO(false) | LightDataAccess.packFC(true);
        assertEquals(3, LightDataAccess.unpackBL(word));
        assertEquals(7, LightDataAccess.unpackSL(word));
        assertEquals(9, LightDataAccess.unpackLU(word));
        assertEquals(0.5f, LightDataAccess.unpackAO(word), 1e-3);
        assertTrue(LightDataAccess.unpackEM(word));
        assertTrue(LightDataAccess.unpackOP(word));
        assertFalse(LightDataAccess.unpackFO(word));
        assertTrue(LightDataAccess.unpackFC(word));
        assertEquals(LightDataAccess.pack(9, 7), LightDataAccess.getLightmap(word));
        assertEquals(LightDataAccess.FULL_BRIGHT, LightDataAccess.getEmissiveLightmap(word));
        int plain = LightDataAccess.packBL(4) | LightDataAccess.packSL(2);
        assertEquals(LightDataAccess.pack(4, 2), LightDataAccess.getEmissiveLightmap(plain));
        assertEquals(4, LightDataAccess.unpackBlock(LightDataAccess.pack(4, 2)));
        assertEquals(2, LightDataAccess.unpackSky(LightDataAccess.pack(4, 2)));
    }

    @Test
    void cacheComputesOncePerPositionUntilReset() {
        FakeLightData data = new FakeLightData();
        data.put(1, 2, 3, FakeLightData.stone());
        assertEquals(FakeLightData.stone(), data.get(1, 2, 3));
        assertEquals(FakeLightData.stone(), data.get(1, 2, 3));
        assertEquals(1, data.computes);
        assertEquals(FakeLightData.stone(), data.get(0, 2, 3, ModelQuadFacing.POS_X));
        assertEquals(FakeLightData.stone(), data.get(0, 1, 3, ModelQuadFacing.POS_X, ModelQuadFacing.POS_Y));
        data.reset(0, 0, 0);
        data.get(1, 2, 3);
        assertEquals(2, data.computes);
        QuadLightData out = new QuadLightData();
        assertEquals(4, out.br.length);
        assertEquals(4, out.lm.length);
    }
}
