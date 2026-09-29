package com.bdmajora.impetus.engine.impl.model.light;

import com.bdmajora.impetus.engine.impl.model.light.flat.FlatLightPipeline;
import com.bdmajora.impetus.engine.impl.model.light.smooth.SmoothLightPipeline;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;
import com.bdmajora.testing.FakeLightData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LightPipelineProviderTest {
    @Test
    void handsOutTheRightPipelineAndResetsBoth() {
        FakeLightData data = new FakeLightData();
        LightPipelineProvider provider = new LightPipelineProvider(data, DiffuseProvider.NONE, true);
        assertInstanceOf(SmoothLightPipeline.class, provider.getLighter(LightMode.SMOOTH));
        assertInstanceOf(FlatLightPipeline.class, provider.getLighter(LightMode.FLAT));
        assertSame(data, provider.getLightData());
        provider.reset();
        assertEquals(1.0f, DiffuseProvider.NONE.getDiffuse(ModelQuadFacing.NEG_Y, true));
        assertEquals(1.0f, DiffuseProvider.NONE.getDiffuse(0, 1, 0, false));
        assertEquals(2, LightMode.values().length);
        LightPipeline bare = (quad, x, y, z, out, cull, light, shade, blend) -> {};
        bare.reset();
    }
}
