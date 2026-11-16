package com.bdmajora.impetus.engine.impl.model.light;

import com.bdmajora.impetus.engine.impl.model.light.data.LightDataAccess;
import com.bdmajora.impetus.engine.impl.model.light.flat.FlatLightPipeline;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

// Tests null refusals of the lighters and their provider.
class LightProviderNullsTest {
    // Rejects a null cache or diffuse provider.
    @Test
    void providerRejectsNulls() {
        LightDataAccess cache = Mockito.mock(LightDataAccess.class);
        assertThrows(NullPointerException.class, () -> new LightPipelineProvider(null, DiffuseProvider.NONE, true));
        assertThrows(NullPointerException.class, () -> new LightPipelineProvider(cache, null, true));
        LightPipelineProvider provider = new LightPipelineProvider(cache, DiffuseProvider.NONE, false);
        assertSame(cache, provider.getLightData());
        assertNotSame(provider.getLighter(LightMode.SMOOTH), provider.getLighter(LightMode.FLAT));
        assertSame(provider.getLighter(LightMode.FLAT), provider.getLighter(null));
        provider.reset();
    }

    // Rejects a null cache or diffuse provider in the flat lighter.
    @Test
    void flatRejectsNulls() {
        assertThrows(NullPointerException.class, () -> new FlatLightPipeline(null, DiffuseProvider.NONE, true));
        assertThrows(NullPointerException.class, () -> new FlatLightPipeline(Mockito.mock(LightDataAccess.class), null, true));
    }
}
