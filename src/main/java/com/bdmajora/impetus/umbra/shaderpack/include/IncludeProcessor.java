package com.bdmajora.impetus.umbra.shaderpack.include;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// Inlines OptiFine-style #include recursively from a flat file map, resolving relative to the including file and rejecting cycles; textual only, #ifdef is left to GlslPreprocessor so an include inside a false gate still inlines
public final class IncludeProcessor {
    // Matches:  #include "path"   or   #include <path>   with optional surrounding whitespace.
    private static final Pattern INCLUDE_PATTERN =
            Pattern.compile("^\\s*#include\\s+[\"<]([^\">]+)[\">].*$");

    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");

    private final Map<AbsolutePackPath, String> sources;
    // Targets already reported missing, so a common.glsl included by forty programs does not log forty times
    private final Set<String> reportedMissing = new HashSet<>();

    public IncludeProcessor(Map<AbsolutePackPath, String> sources) {
        this.sources = sources;
    }

    // Flattens the file at root, inlining everything it transitively includes; throws IllegalStateException on an unresolvable include or cycle, since continuing hands the driver a truncated shader
    public List<String> process(AbsolutePackPath root) {
        String source = this.sources.get(root);
        if (source == null) {
            throw new IllegalStateException("Cannot process missing file: " + root.getPathString());
        }
        List<String> out = new ArrayList<>();
        Deque<AbsolutePackPath> stack = new ArrayDeque<>();
        processInto(root, splitLines(source), out, stack);
        return out;
    }

    // Splices each #include in place, detecting cycles via the stack
    private void processInto(AbsolutePackPath path, List<String> lines, List<String> out, Deque<AbsolutePackPath> stack) {
        if (stack.contains(path)) {
            throw new IllegalStateException("Cyclic #include detected involving " + path.getPathString());
        }
        stack.push(path);
        try {
            for (String line : lines) {
                // Almost every line is plain GLSL, so the substring test keeps the regex off them
                if (!line.contains("#include")) {
                    out.add(line);
                    continue;
                }

                Matcher matcher = INCLUDE_PATTERN.matcher(line);
                if (matcher.matches()) {
                    AbsolutePackPath target = path.resolve(matcher.group(1).trim());
                    String included = this.sources.get(target);
                    if (included == null) {
                        // Tolerate optional includes that do not exist, emitting a marker and continuing, but loudly: Umbra and OptiFine both treat this as FATAL, and skipping quietly turns one precise error into a flood of undefined-variable failures (miniature-shader's dropped /shader.h). No local pack relies on this tolerance
                        if (this.reportedMissing.add(target.getPathString())) {
                            LOGGER.warn("[Umbra] Unresolved #include \"{}\" from {} — it resolved to {}, which is not in"
                                    + " the pack. Everything that file defined will be undefined at compile time.",
                                    matcher.group(1).trim(), path.getPathString(), target.getPathString());
                        }
                        out.add("// [Impetus/Umbra] skipped unresolved #include \"" + matcher.group(1) + "\"");
                    } else {
                        processInto(target, splitLines(included), out, stack);
                    }
                } else {
                    out.add(line);
                }
            }
        } finally {
            stack.pop();
        }
    }

    // Tolerates both line ending styles
    private static List<String> splitLines(String source) {
        // Preserve empty trailing structure; split on any newline form.
        List<String> list = new ArrayList<>(Arrays.asList(source.split("\r\n|\r|\n", -1)));
        // Drop a single trailing empty element introduced by a terminating newline.
        if (!list.isEmpty() && list.get(list.size() - 1).isEmpty()) {
            list.remove(list.size() - 1);
        }
        return list;
    }
}
