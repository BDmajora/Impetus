package com.bdmajora.impetus.umbra.pipeline.rendering;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShaderSourceFixesTest {
    @Test
    void floatConditionalsTheDriverRejectsAreFolded() {
        assertEquals("#if 0\n#endif", ShaderSourceFixes.foldUncompilableConditionals("#if 1.5 > 2.0\n#endif"));
        assertEquals("#if 1\n#endif", ShaderSourceFixes.foldUncompilableConditionals("#if 3.5 > 2.0\n#endif"));
        // Literals truncate toward zero like Iris's JCPP, which packs rely on (Clarity's MOTION_BLUR > 0.0)
        assertEquals("#if 0\n#endif", ShaderSourceFixes.foldUncompilableConditionals("#if 2.5 > 2.0\n#endif"));
        // Integer conditionals stay for the driver
        assertEquals("#if 3 > 2\n#endif", ShaderSourceFixes.foldUncompilableConditionals("#if 3 > 2\n#endif"));
    }

    @Test
    void everyArbLookupBecomesItsCoreNameFrom130On() {
        String source = String.join("\n", "#version 430",
                "a = texture2DGradARB(t, uv, x, y);",
                "b = texture3DGradARB (t, uvw, x, y);",
                "c = textureCubeGradARB(t, d, x, y);",
                "d = texture2DLodARB(t, uv, 0.0);",
                "e = texture2DProjGradARB(t, uv, x, y);",
                "f = shadow2DGradARB(t, uv, x, y);");
        String fixed = ShaderSourceFixes.normalizeArbTextureLookups(source);
        assertEquals(String.join("\n", "#version 430",
                "a = textureGrad(t, uv, x, y);",
                "b = textureGrad (t, uvw, x, y);",
                "c = textureGrad(t, d, x, y);",
                "d = textureLod(t, uv, 0.0);",
                "e = textureProjGrad(t, uv, x, y);",
                "f = textureGrad(t, uv, x, y);"), fixed);
        assertEquals("#version 130\nx = textureLod(t, uv, 0.0);",
                ShaderSourceFixes.normalizeArbTextureLookups("#version 130\nx = texture2DLodARB(t, uv, 0.0);"));
    }

    @Test
    void onlyWholeCallsAreRenamed() {
        String source = String.join("\n", "#version 330",
                // A longer identifier, a non-call use and a define without parentheses all stay
                "vec4 mytexture2DLodARB(sampler2D t) { return vec4(0); }",
                "float texture2DLodARBScale = 1.0;",
                "#define LOOKUP texture2DLodARB",
                "x = texture2DLodARB(t, uv, 0.0);");
        String fixed = ShaderSourceFixes.normalizeArbTextureLookups(source);
        assertTrue(fixed.contains("vec4 mytexture2DLodARB(sampler2D t)"), fixed);
        assertTrue(fixed.contains("float texture2DLodARBScale"), fixed);
        assertTrue(fixed.contains("#define LOOKUP texture2DLodARB"), fixed);
        assertTrue(fixed.contains("x = textureLod(t, uv, 0.0);"), fixed);
    }

    @Test
    void sourcesBelow130OrWithoutAVersionKeepTheirArbNames() {
        String legacy = "#version 120\nx = texture2DLodARB(t, uv, 0.0);";
        assertSame(legacy, ShaderSourceFixes.normalizeArbTextureLookups(legacy));
        String unversioned = "x = texture2DLodARB(t, uv, 0.0);";
        assertSame(unversioned, ShaderSourceFixes.normalizeArbTextureLookups(unversioned));
        // A version no driver accepts leaves the source alone rather than throwing
        String absurd = "#version 99999999999999\nx = texture2DLodARB(t, uv, 0.0);";
        assertSame(absurd, ShaderSourceFixes.normalizeArbTextureLookups(absurd));
        // Only the first #version counts, as for the driver
        assertFalse(ShaderSourceFixes.usesCoreLookups("#version 120\n#version 430\n"));
        assertTrue(ShaderSourceFixes.usesCoreLookups("  #version 150 compatibility\n"));
        assertTrue(ShaderSourceFixes.usesCoreLookups("#version 999999999\n"));
        assertFalse(ShaderSourceFixes.usesCoreLookups("#version 129\n"));
    }

    @Test
    void stabilizeInitialisesTheLightVolumeAndLeavesCleanSourcesAsTheyWere() {
        String source = "#version 430\n\tvec4 lightVolume;\nvec4 lightVolume = vec4(1.0);\nvec4 lightVolumeX;";
        String stabilized = ShaderSourceFixes.stabilize("x", source);
        assertTrue(stabilized.contains("\tvec4 lightVolume = vec4(0.0);"), stabilized);
        // An initialised declaration and a longer name stay
        assertTrue(stabilized.contains("vec4 lightVolume = vec4(1.0);"), stabilized);
        assertTrue(stabilized.contains("vec4 lightVolumeX;"), stabilized);
        assertEquals("void main() {}", ShaderSourceFixes.stabilize("x", "void main() {}"));
        String legacy = "#version 120\nvec4 a = texture2DLodARB(tex, uv, 0.0);";
        assertEquals(legacy, ShaderSourceFixes.stabilize("x", legacy));
    }
}
