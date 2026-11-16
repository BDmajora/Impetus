package com.bdmajora.impetus.umbra.shaderpack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Constant directives found in pack sources.
public final class ConstDirectives {
    // Pattern of a float literal.
    public static final String FLOAT_LITERAL = "([-+]?(?:[0-9]+\\.?[0-9]*|\\.[0-9]+)(?:[eE][-+]?[0-9]+)?)[fF]?";
    // Pattern of a constant declaration.
    private static final Pattern CONST = Pattern.compile("const\\s+(int|float|bool|vec4)\\s+(\\w+)\\s*=\\s*([^;]+?)\\s*;");
    // Vec4.
    private static final Pattern VEC4 = Pattern.compile("^vec4\\s*\\(([^)]*)\\)$");
    // Int prefix.
    private static final Pattern INT_PREFIX = Pattern.compile("^[-+]?\\d+");
    // Float prefix.
    private static final Pattern FLOAT_PREFIX = Pattern.compile("^" + FLOAT_LITERAL);

    private final Map<String, String> ints = new HashMap<>();
    private final Map<String, String> floats = new HashMap<>();
    private final Map<String, String> bools = new HashMap<>();
    private final Map<String, String> vec4s = new HashMap<>();

    // Creates a const directives from its text.
    public ConstDirectives(String text) {
        scan(text);
    }

    // Creates a const directives from its texts.
    public ConstDirectives(List<String> texts) {
        for (String text : texts) {
            scan(text);
        }
    }

    // Records the constants of a source.
    private void scan(String text) {
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

    // Returns an int constant or a fallback.
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

    // Returns a float constant or null.
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

    // Returns a float constant or a fallback.
    public float getFloat(String name, float fallback) {
        Float value = getFloat(name);
        return value != null ? value : fallback;
    }

    // Returns a bool constant or false.
    public boolean getBool(String name) {
        return getBool(name, false);
    }

    // Returns a bool constant or a fallback.
    public boolean getBool(String name, boolean fallback) {
        return getOptionalBool(name).orElse(fallback);
    }

    // Returns a bool constant or empty.
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

    // Returns the raw text of an int constant or empty.
    public Optional<String> getIntToken(String name) {
        return Optional.ofNullable(ints.get(name));
    }

    // Returns a vec4 constant or empty.
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
