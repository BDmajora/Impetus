package com.bdmajora.impetus.umbra.terrain;

import java.util.regex.Matcher;

// Fog uniform declarations for pack shaders.
final class FogParameters {
    // Scale expression.
    static final String SCALE_EXPRESSION = "(1.0 / max(iris_FogEnd - iris_FogStart, 1e-6))";

    // Declarations added to a shader.
    static final String[] DECLARATIONS = {
            "uniform vec4 iris_FogColor;",
            "uniform float iris_FogDensity;",
            "uniform float iris_FogStart;",
            "uniform float iris_FogEnd;",
    };

    // Prevents instantiation
    private FogParameters() {
    }

    // Replaces fog struct reads with the fog uniforms.
    static String rewrite(String source) {
        source = source.replaceAll("\\bgl_Fog\\s*\\.\\s*color\\b", "iris_FogColor");
        source = source.replaceAll("\\bgl_Fog\\s*\\.\\s*density\\b", "iris_FogDensity");
        source = source.replaceAll("\\bgl_Fog\\s*\\.\\s*start\\b", "iris_FogStart");
        source = source.replaceAll("\\bgl_Fog\\s*\\.\\s*end\\b", "iris_FogEnd");
        return source.replaceAll("\\bgl_Fog\\s*\\.\\s*scale\\b", Matcher.quoteReplacement(SCALE_EXPRESSION));
    }
}
