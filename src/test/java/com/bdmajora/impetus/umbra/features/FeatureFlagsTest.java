package com.bdmajora.impetus.umbra.features;

import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FeatureFlagsTest {
    @Test
    void everyIrisFlagIsHonouredWhenTheGpuCanRunIt() {
        // TestGl runs the interface's default here, which answers false
        Mockito.when(TestGl.gl().supportsBufferBlending()).thenReturn(true);
        for (FeatureFlags flag : FeatureFlags.values()) {
            assertEquals(flag != FeatureFlags.UNKNOWN, flag.isUsable(), flag.name());
        }
        // Iris's old misspelling still names the flag, loosely written tokens are normalised, and anything else is unknown
        assertEquals(FeatureFlags.TESSELLATION_SHADERS, FeatureFlags.byName("tesselation_shaders"));
        assertEquals(FeatureFlags.FADE_VARIABLE, FeatureFlags.byName(" fade_variable "));
        assertEquals(FeatureFlags.UNKNOWN, FeatureFlags.byName("MADE_UP"));

        assertEquals(Set.of(FeatureFlags.SSBO, FeatureFlags.HIGHER_SHADOWCOLOR),
                FeatureFlags.parseDeclared("SSBO MADE_UP", "HIGHER_SHADOWCOLOR"));
        assertEquals(List.of("MADE_UP"), FeatureFlags.findUnsupported("SSBO MADE_UP"));
        assertTrue(FeatureFlags.findUnsupported("  ").isEmpty());
        assertTrue(FeatureFlags.findUnsupported(null).isEmpty());

        Map<String, String> macros = new HashMap<>();
        FeatureFlags.addUsableDefines(macros);
        assertTrue(macros.containsKey("IRIS_FEATURE_PER_BUFFER_BLENDING"));
        assertFalse(macros.containsKey("IRIS_FEATURE_UNKNOWN"));
    }

    @Test
    void hardwareGatesTheFlagsThatNeedIt() {
        Mockito.when(TestGl.gl().isOpenGLVersionSupported(4, 0)).thenReturn(false);
        Mockito.when(TestGl.gl().isOpenGLVersionSupported(4, 3)).thenReturn(false);
        Mockito.when(TestGl.gl().isExtensionSupported(Mockito.any())).thenReturn(false);
        Mockito.when(TestGl.gl().supportsBufferBlending()).thenReturn(false);
        assertFalse(FeatureFlags.TESSELLATION_SHADERS.isUsable());
        assertFalse(FeatureFlags.COMPUTE_SHADERS.isUsable());
        assertFalse(FeatureFlags.SSBO.isUsable());
        assertFalse(FeatureFlags.PER_BUFFER_BLENDING.isUsable());
        assertTrue(FeatureFlags.HIGHER_SHADOWCOLOR.isUsable());
        assertEquals("not supported by this GPU", FeatureFlags.describeUnsupported(List.of("SSBO")));
        assertEquals("not supported by Umbra", FeatureFlags.describeUnsupported(List.of("MADE_UP")));
        assertEquals("not supported by Umbra or by this GPU", FeatureFlags.describeUnsupported(List.of("SSBO", "MADE_UP")));

        // A thread with no GL context cannot ask, and is treated as capable
        Mockito.when(TestGl.gl().isOpenGLVersionSupported(4, 2)).thenThrow(new IllegalStateException("no context"));
        assertTrue(FeatureFlags.CUSTOM_IMAGES.isUsable());
    }
}
