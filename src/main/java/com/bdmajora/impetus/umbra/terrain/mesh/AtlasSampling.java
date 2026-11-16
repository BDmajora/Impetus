package com.bdmajora.impetus.umbra.terrain.mesh;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Rewrites block atlas reads of a fragment stage to follow the mip state of a mesh shader quad.
final class AtlasSampling {
    // Name of the mip state varying.
    static final String MIPPED = "umbra_Mipped";
    // Define of the level of detail bias of an unmipped quad.
    static final String BIAS_DEFINE = "#define UMBRA_ATLAS_BIAS ((" + MIPPED + " - 1.0) * 8.0)\n";

    // Sampler names of the block atlas.
    private static final Set<String> ATLAS = Set.of("tex", "texture", "gtexture", "gcolor");

    // Implicit level reads with their argument counts before a bias.
    private static final Map<String, Integer> IMPLICIT = Map.of("texture", 2, "texture2D", 2, "textureProj", 2, "texture2DProj", 2,
            "textureOffset", 3, "textureProjOffset", 3);
    // Explicit level reads with the index of the level argument.
    private static final Map<String, Integer> LOD = Map.of("textureLod", 2, "texture2DLod", 2, "texture2DLodEXT", 2, "textureLodOffset", 2);
    // Gradient reads with the index of the first gradient argument.
    private static final Map<String, Integer> GRAD = Map.of("textureGrad", 2, "texture2DGradARB", 2, "texture2DGradEXT", 2, "textureGradOffset", 2);

    // Pattern of a read call.
    private static final Pattern CALL;
    // Pattern of a GLSL comment.
    private static final Pattern COMMENT = Pattern.compile("//[^\\n]*|/\\*(?s:.*?)\\*/");

    // Builds the call pattern from every read name
    static {
        List<String> names = new ArrayList<>(IMPLICIT.keySet());
        names.addAll(LOD.keySet());
        names.addAll(GRAD.keySet());
        CALL = Pattern.compile("\\b(" + String.join("|", names) + ")\\s*\\(");
    }

    // Prevents instantiation
    private AtlasSampling() {
    }

    // Returns whether a fragment stage reads the block atlas.
    static boolean samplesAtlas(String fragment) {
        return !insertions(fragment).isEmpty();
    }

    // Returns the fragment stage with every atlas read adjusted.
    static String rewrite(String fragment) {
        TreeMap<Integer, String> insertions = insertions(fragment);
        if (insertions.isEmpty()) {
            return fragment;
        }
        // Splices every insertion in one forward pass
        StringBuilder out = new StringBuilder(fragment.length() + insertions.size() * 24);
        int copied = 0;
        for (Map.Entry<Integer, String> insertion : insertions.entrySet()) {
            int at = insertion.getKey();
            out.append(fragment, copied, at).append(insertion.getValue());
            copied = at;
        }
        return out.append(fragment, copied, fragment.length()).toString();
    }

    // Returns the texts to insert by position for every atlas read.
    private static TreeMap<Integer, String> insertions(String fragment) {
        String code = blankComments(fragment);
        TreeMap<Integer, String> insertions = new TreeMap<>();
        Matcher call = CALL.matcher(code);
        while (call.find()) {
            List<int[]> args = arguments(code, call.end());
            if (args == null || args.isEmpty() || !ATLAS.contains(code.substring(args.getFirst()[0], args.getFirst()[1]))) {
                continue;
            }
            String name = call.group(1);
            Integer base = IMPLICIT.get(name);
            if (base != null) {
                if (args.size() == base) {
                    insertions.put(args.getLast()[1], ", UMBRA_ATLAS_BIAS");
                } else if (args.size() == base + 1) {
                    wrap(insertions, args.getLast(), ") + UMBRA_ATLAS_BIAS");
                }
                continue;
            }
            int first = LOD.containsKey(name) ? LOD.get(name) : GRAD.get(name);
            int count = LOD.containsKey(name) ? 1 : 2;
            for (int i = first; i < first + count && i < args.size(); i++) {
                wrap(insertions, args.get(i), ") * " + MIPPED);
            }
        }
        return insertions;
    }

    // Adds parentheses around an argument followed by a text.
    private static void wrap(TreeMap<Integer, String> insertions, int[] arg, String after) {
        insertions.put(arg[0], "(");
        insertions.put(arg[1], after);
    }

    // Returns the trimmed argument spans of a call or null when it never closes.
    private static List<int[]> arguments(String code, int open) {
        List<int[]> args = new ArrayList<>();
        int depth = 0;
        int start = open;
        for (int i = open; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '(' || c == '[') {
                depth++;
            } else if ((c == ')' || c == ']') && depth > 0) {
                depth--;
            } else if (c == ',' && depth == 0 || c == ')') {
                int[] arg = trim(code, start, i);
                if (arg[0] < arg[1]) {
                    args.add(arg);
                }
                if (c == ')') {
                    return args;
                }
                start = i + 1;
            }
        }
        return null;
    }

    // Returns a span without its surrounding whitespace.
    private static int[] trim(String code, int start, int end) {
        while (start < end && Character.isWhitespace(code.charAt(start))) {
            start++;
        }
        while (end > start && Character.isWhitespace(code.charAt(end - 1))) {
            end--;
        }
        return new int[]{start, end};
    }

    // Returns the source with comments replaced by spaces and the line breaks kept.
    private static String blankComments(String source) {
        StringBuilder out = new StringBuilder(source);
        Matcher comment = COMMENT.matcher(source);
        while (comment.find()) {
            for (int i = comment.start(); i < comment.end(); i++) {
                if (out.charAt(i) != '\n') {
                    out.setCharAt(i, ' ');
                }
            }
        }
        return out.toString();
    }
}
