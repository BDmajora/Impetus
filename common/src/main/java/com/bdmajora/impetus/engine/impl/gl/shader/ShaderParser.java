package com.bdmajora.impetus.engine.impl.gl.shader;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Resolves import lines in shader sources and injects constant definitions.
public class ShaderParser {
    // Pattern of an import line with a namespace and a path.
    private static final Pattern IMPORT_PATTERN = Pattern.compile("#import <(?<namespace>.*):(?<path>.*)>");

    // Resolves import lines and inserts the definitions after the first line.
    public static String parseShader(String src, Function<String, String> sourceProvider, ShaderConstants constants) {
        Objects.requireNonNull(constants, "constants");
        List<String> lines = parseShader(src, sourceProvider);
        List<String> defines = constants.getDefineStrings();

        int size = lines.size();
        int at = Math.min(1, size);
        StringBuilder out = new StringBuilder(Math.max(16, (size + defines.size()) * 24));

        for (int i = 0; i <= size; i++) {
            if (i == at) {
                for (int d = 0, n = defines.size(); d < n; d++) {
                    out.append(defines.get(d)).append('\n');
                }
            }
            if (i < size) {
                out.append(lines.get(i)).append('\n');
            }
        }

        int length = out.length();
        if (length > 0) {
            out.setLength(length - 1);
        }
        return out.toString();
    }

    // Resolves import lines recursively and returns the lines of the expanded source.
    public static List<String> parseShader(String src, Function<String, String> sourceProvider) {
        Objects.requireNonNull(src, "src");
        Objects.requireNonNull(sourceProvider, "sourceProvider");

        List<String> out = new ArrayList<>();
        collect(src, sourceProvider, out, new HashSet<>());
        return out;
    }

    // Appends the lines of one source to the output and expands each import in place.
    private static void collect(String src, Function<String, String> sourceProvider, List<String> out, Set<String> active) {
        // Visits each line of the source in order
        src.lines().forEachOrdered(line -> {
            if (line.startsWith("#import")) {
                resolveImport(line, sourceProvider, out, active);
            } else {
                out.add(line);
            }
        });
    }

    // Loads one imported file and appends its expanded lines and rejects cyclic imports.
    private static void resolveImport(String line, Function<String, String> sourceProvider, List<String> out, Set<String> active) {
        Matcher matcher = IMPORT_PATTERN.matcher(line);

        if (!matcher.matches()) {
            throw new IllegalArgumentException("Malformed import statement (expected format: " + IMPORT_PATTERN + ")");
        }

        String name = matcher.group("namespace") + ":" + matcher.group("path");

        if (!active.add(name)) {
            throw new IllegalStateException("Cyclic shader import of " + name);
        }

        try {
            String source = sourceProvider.apply(name);
            if (source == null) {
                throw new IllegalStateException("No shader source for import " + name);
            }
            collect(source, sourceProvider, out, active);
        } finally {
            active.remove(name);
        }
    }
}
