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

// Resolver of include lines in pack sources.
public final class IncludeProcessor {
    // Pattern of an include line.
    private static final Pattern INCLUDE_PATTERN =
            Pattern.compile("^\\s*#include\\s+[\"<]([^\">]+)[\">].*$");

    // Logger of this class.
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");

    private final Map<AbsolutePackPath, String> sources;
    // Missing includes already reported.
    private final Set<String> reportedMissing = new HashSet<>();

    // Creates an include processor from its sources.
    public IncludeProcessor(Map<AbsolutePackPath, String> sources) {
        this.sources = sources;
    }

    // Returns the lines of a file with its includes inlined.
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

    // Expands the include lines of a file into the output and refuses include cycles.
    private void processInto(AbsolutePackPath path, List<String> lines, List<String> out, Deque<AbsolutePackPath> stack) {
        if (stack.contains(path)) {
            throw new IllegalStateException("Cyclic #include detected involving " + path.getPathString());
        }
        stack.push(path);
        try {
            for (String line : lines) {
                if (!line.contains("#include")) {
                    out.add(line);
                    continue;
                }

                Matcher matcher = INCLUDE_PATTERN.matcher(line);
                if (matcher.matches()) {
                    AbsolutePackPath target = path.resolve(matcher.group(1).trim());
                    String included = this.sources.get(target);
                    if (included == null) {
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

    // Splits text into lines.
    private static List<String> splitLines(String source) {
        List<String> list = new ArrayList<>(Arrays.asList(source.split("\r\n|\r|\n", -1)));
        if (!list.isEmpty() && list.get(list.size() - 1).isEmpty()) {
            list.remove(list.size() - 1);
        }
        return list;
    }
}
