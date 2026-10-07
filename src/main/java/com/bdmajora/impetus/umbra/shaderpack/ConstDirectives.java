package com.bdmajora.impetus.umbra.shaderpack;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// The pack's `const <type> <name> = <value>;` directives, scanned once from the concatenated (conditional-resolved) sources; last declaration wins, which is Umbra's and OptiFine's rule since each directive overwrites the previous in file order. Replaces one full-text regex scan per directive read
public final class ConstDirectives {
    // The GLSL float literal grammar, as permissive as Float#parseFloat (sign, .5 and 1. forms, exponent)
    public static final String FLOAT_LITERAL = "([-+]?(?:[0-9]+\\.?[0-9]*|\\.[0-9]+)(?:[eE][-+]?[0-9]+)?)[fF]?";
    private static final Pattern CONST = Pattern.compile("const\\s+(int|float|bool|vec4)\\s+(\\w+)\\s*=\\s*([^;]+?)\\s*;");
    // vec4(x, y, z, w), or vec4(x) splatted, the two spellings packs write clear colours in
    private static final Pattern VEC4 = Pattern.compile("^vec4\\s*\\(([^)]*)\\)$");
    // Only the leading literal of a value is read, as the per-directive scans did, so `2048 * 2` still yields 2048 rather than a parse failure
    private static final Pattern INT_PREFIX = Pattern.compile("^[-+]?\\d+");
    private static final Pattern FLOAT_PREFIX = Pattern.compile("^" + FLOAT_LITERAL);

    private final Map<String, String> ints = new HashMap<>();
    private final Map<String, String> floats = new HashMap<>();
    private final Map<String, String> bools = new HashMap<>();
    private final Map<String, String> vec4s = new HashMap<>();

    public ConstDirectives(String text) {
        Matcher matcher = CONST.matcher(text);
        while (matcher.find()) {
            String name = matcher.group(2);
            String value = matcher.group(3);
            switch (matcher.group(1)) {
                case "int" -> ints.put(name, value);
                case "float" -> floats.put(name, value);
                case "vec4" -> vec4s.put(name, value);
                default -> bools.put(name, value);
            }
        }
    }

    // const int NAME = value;
    public int getInt(String name, int fallback) {
        String value = ints.get(name);
        if (value == null) {
            return fallback;
        }
        Matcher literal = INT_PREFIX.matcher(value);
        if (!literal.find()) {
            return fallback;
        }
        try {
            return Integer.parseInt(literal.group());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // const float NAME = value;, or const int since packs write `const float shadowDistance = 120;` and both Umbra and OptiFine tolerate it
    public Float getFloat(String name) {
        String value = floats.get(name);
        if (value == null) {
            value = ints.get(name);
        }
        if (value == null) {
            return null;
        }
        Matcher literal = FLOAT_PREFIX.matcher(value);
        if (!literal.find()) {
            return null;
        }
        try {
            return Float.parseFloat(literal.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public float getFloat(String name, float fallback) {
        Float value = getFloat(name);
        return value != null ? value : fallback;
    }

    // const bool NAME = value;, false when absent
    public boolean getBool(String name) {
        return getBool(name, false);
    }

    public boolean getBool(String name, boolean fallback) {
        return getOptionalBool(name).orElse(fallback);
    }

    // const bool with presence distinguished from false
    public Optional<Boolean> getOptionalBool(String name) {
        String value = bools.get(name);
        if ("true".equals(value)) {
            return Optional.of(Boolean.TRUE);
        }
        if ("false".equals(value)) {
            return Optional.of(Boolean.FALSE);
        }
        return Optional.empty();
    }

    // The raw value of a const int, for directives whose value is an identifier rather than a number (const int shadowcolor0Format = RGBA16F;)
    public Optional<String> getIntToken(String name) {
        return Optional.ofNullable(ints.get(name));
    }

    // const vec4 NAME = vec4(...); a single component fills all four, anything malformed reads as absent
    public Optional<float[]> getVec4(String name) {
        String value = vec4s.get(name);
        if (value == null) {
            return Optional.empty();
        }
        Matcher matcher = VEC4.matcher(value);
        if (!matcher.find()) {
            return Optional.empty();
        }
        String[] parts = matcher.group(1).split(",");
        if (parts.length != 1 && parts.length != 4) {
            return Optional.empty();
        }
        float[] components = new float[4];
        try {
            for (int i = 0; i < 4; i++) {
                String part = parts[parts.length == 1 ? 0 : i].trim();
                components[i] = Float.parseFloat(part.endsWith("f") || part.endsWith("F") ? part.substring(0, part.length() - 1) : part);
            }
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        return Optional.of(components);
    }
}
