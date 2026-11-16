package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.shaderpack.preprocessor.GlslPreprocessor;
import com.bdmajora.impetus.umbra.terrain.GlslIntegerOverloadPolyfill;

import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Text fixes applied to pack shader sources.
public final class ShaderSourceFixes {
    // Pattern of an uninitialised light volume.
    private static final Pattern UNINITIALIZED_LIGHT_VOLUME =
            Pattern.compile("(?m)^([\\t ]*)vec4\\s+lightVolume\\s*;[\\t ]*$");
    // Version directive.
    private static final Pattern VERSION_DIRECTIVE = Pattern.compile("(?m)^\\s*#version\\s+(\\d+)");
    // ARB lookup function names.
    private static final String[] ARB_NAMES = {"texture2DGradARB", "texture3DGradARB", "textureCubeGradARB",
            "texture2DLodARB", "texture2DProjGradARB", "shadow2DGradARB"};
    // Core lookup function names.
    private static final String[] CORE_NAMES = {"textureGrad", "textureGrad", "textureGrad", "textureLod",
            "textureProjGrad", "textureGrad"};
    // Patterns of the ARB lookup calls.
    private static final Pattern[] ARB_CALLS = new Pattern[ARB_NAMES.length];
    // Lowest version with core lookups.
    private static final int CORE_LOOKUP_VERSION = 130;

    static {
        for (int i = 0; i < ARB_NAMES.length; i++) {
            ARB_CALLS[i] = Pattern.compile("(?<![A-Za-z0-9_])" + Pattern.quote(ARB_NAMES[i]) + "(?=\\s*\\()");
        }
    }

    // Prevents instantiation
    private ShaderSourceFixes() {
    }

    // Folds preprocessor branches that cannot compile.
    public static String foldUncompilableConditionals(String source) {
        return GlslPreprocessor.foldFloatConditionals(source, Collections.emptyMap());
    }

    // Applies the fixes that make a source compile.
    public static String stabilize(String name, String source) {
        String folded = foldUncompilableConditionals(source);
        String normalized = normalizeArbTextureLookups(folded);
        String widened = GlslIntegerOverloadPolyfill.widenIntegerBuiltinCalls(name, normalized);
        return UNINITIALIZED_LIGHT_VOLUME.matcher(widened).replaceAll("$1vec4 lightVolume = vec4(0.0);");
    }

    // Renames ARB lookups to core lookups.
    static String normalizeArbTextureLookups(String source) {
        if (!usesCoreLookups(source)) {
            return source;
        }
        String result = source;
        for (int i = 0; i < ARB_NAMES.length; i++) {
            if (result.contains(ARB_NAMES[i])) {
                result = ARB_CALLS[i].matcher(result).replaceAll(Matcher.quoteReplacement(CORE_NAMES[i]));
            }
        }
        return result;
    }

    // Returns whether a source version has core lookups.
    static boolean usesCoreLookups(String source) {
        Matcher version = VERSION_DIRECTIVE.matcher(source);
        if (!version.find()) {
            return false;
        }
        String digits = version.group(1);
        return digits.length() <= 9 && Integer.parseInt(digits) >= CORE_LOOKUP_VERSION;
    }
}
