package com.bdmajora.impetus.umbra.terrain;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Adder of integer overloads that old drivers lack.
public final class GlslIntegerOverloadPolyfill {

    // Integer uniforms.
    private static final Set<String> INTEGER_UNIFORMS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "eyeBrightness", "eyeBrightnessSmooth", "worldTime", "worldDay", "moonPhase", "frameCounter",
            "isEyeInWater", "heldItemId", "heldItemId2", "heldBlockLightValue", "heldBlockLightValue2",
            "entityId", "blockEntityId", "currentRenderedItemId", "currentSelectedBlockId", "fogMode", "fogShape",
            "renderStage", "instanceId", "hideGUI")));

    // Version directive.
    private static final Pattern VERSION_DIRECTIVE = Pattern.compile("(?m)^\\s*#version\\s+(\\d+)");

    // Simple two arg call.
    private static final Pattern SIMPLE_TWO_ARG_CALL = Pattern.compile(
            "(?<![A-Za-z0-9_])(max|min)\\s*\\(\\s*([^(),]+?)\\s*,\\s*([^(),]+?)\\s*\\)");

    // Integer expression.
    private static final Pattern INTEGER_EXPRESSION = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)(\\.[xyzwrgba]+)?|(\\d+)");

    // Prevents instantiation
    private GlslIntegerOverloadPolyfill() {
    }

    // Casts integer arguments of float built ins.
    public static String widenIntegerBuiltinCalls(String name, String source) {
        if (source == null || !isLegacyVersion(source)) {
            return source;
        }

        Matcher matcher = SIMPLE_TWO_ARG_CALL.matcher(source);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String first = matcher.group(2);
            String second = matcher.group(3);
            if (isIntegerExpression(first) && isIntegerExpression(second)) {
                matcher.appendReplacement(result, Matcher.quoteReplacement(
                        matcher.group(1) + "(float(" + first + "), float(" + second + "))"));
            } else {
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(result);

        return result.toString();
    }

    // Returns whether a source targets an old version.
    private static boolean isLegacyVersion(String source) {
        Matcher matcher = VERSION_DIRECTIVE.matcher(source);
        if (!matcher.find()) {
            return true;
        }
        try {
            return Integer.parseInt(matcher.group(1)) < 130;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // Returns whether an expression is an integer.
    private static boolean isIntegerExpression(String expression) {
        String trimmed = expression.trim();
        Matcher matcher = INTEGER_EXPRESSION.matcher(trimmed);
        if (!matcher.matches()) {
            return false;
        }
        if (matcher.group(3) != null) {
            return true;
        }
        return INTEGER_UNIFORMS.contains(matcher.group(1));
    }
}
