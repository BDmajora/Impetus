package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.shaderpack.preprocessor.GlslPreprocessor;
import com.bdmajora.impetus.umbra.terrain.GlslIntegerOverloadPolyfill;

import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Source fixes every pack program needs before any transform, for the constructs drivers reject
public final class ShaderSourceFixes {
    private static final Pattern UNINITIALIZED_LIGHT_VOLUME =
            Pattern.compile("(?m)^([\\t ]*)vec4\\s+lightVolume\\s*;[\\t ]*$");
    // `#version <number>`, first one wins like the driver; Umbra never rewrites pack source beyond this, so a flattened dither reroll that froze Complementary's light shafts into static slabs is gone
    private static final Pattern VERSION_DIRECTIVE = Pattern.compile("(?m)^\\s*#version\\s+(\\d+)");
    // The ARB_shader_texture_lod entry points, legal in #version 130+ only with the extension; renamed to the core textureGrad family like Umbra's CommonTransformer, since Just Colored Lighting and Sildur's call texture2DGradARB from 430 sources and NVIDIA rejects it (C7531)
    private static final String[] ARB_NAMES = {"texture2DGradARB", "texture3DGradARB", "textureCubeGradARB",
            "texture2DLodARB", "texture2DProjGradARB", "shadow2DGradARB"};
    private static final String[] CORE_NAMES = {"textureGrad", "textureGrad", "textureGrad", "textureLod",
            "textureProjGrad", "textureGrad"};
    private static final Pattern[] ARB_CALLS = new Pattern[ARB_NAMES.length];
    // The first GLSL version with the core lookups; a 120 pack enables the extension itself and textureGrad does not exist there
    private static final int CORE_LOOKUP_VERSION = 130;

    static {
        for (int i = 0; i < ARB_NAMES.length; i++) {
            ARB_CALLS[i] = Pattern.compile("(?<![A-Za-z0-9_])" + Pattern.quote(ARB_NAMES[i]) + "(?=\\s*\\()");
        }
    }

    private ShaderSourceFixes() {
    }

    // Resolves the #if directives the driver cannot accept (float comparisons, malformed expressions), leaving the rest to it; callers inline the macro environment as #define lines first
    public static String foldUncompilableConditionals(String source) {
        return GlslPreprocessor.foldFloatConditionals(source, Collections.emptyMap());
    }

    // Applies the fixes every source needs before any transform
    public static String stabilize(String name, String source) {
        String folded = foldUncompilableConditionals(source);
        String normalized = normalizeArbTextureLookups(folded);
        String widened = GlslIntegerOverloadPolyfill.widenIntegerBuiltinCalls(name, normalized);
        return UNINITIALIZED_LIGHT_VOLUME.matcher(widened).replaceAll("$1vec4 lightVolume = vec4(0.0);");
    }

    // Rewrites the *ARB lookups to their core names only for #version 130+ sources
    static String normalizeArbTextureLookups(String source) {
        if (!usesCoreLookups(source)) {
            return source;
        }
        String result = source;
        for (int i = 0; i < ARB_NAMES.length; i++) {
            // The substring check is far cheaper than the regex, and almost no source names any of these
            if (result.contains(ARB_NAMES[i])) {
                result = ARB_CALLS[i].matcher(result).replaceAll(Matcher.quoteReplacement(CORE_NAMES[i]));
            }
        }
        return result;
    }

    // Whether the first #version is 130 or newer; a number too long for an int is no version a driver accepts, so the source is left alone
    static boolean usesCoreLookups(String source) {
        Matcher version = VERSION_DIRECTIVE.matcher(source);
        if (!version.find()) {
            return false;
        }
        String digits = version.group(1);
        return digits.length() <= 9 && Integer.parseInt(digits) >= CORE_LOOKUP_VERSION;
    }
}
