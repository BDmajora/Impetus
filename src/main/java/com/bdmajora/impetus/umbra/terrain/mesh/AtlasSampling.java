package com.bdmajora.impetus.umbra.terrain.mesh;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// The raster path draws cutout blocks with the atlas's mipmaps off, but the mesh backend draws every opaque quad in one program; so a ported fragment stage's atlas lookups are told per quad: implicit-LOD calls get a bias that drops to the base level, explicit LODs and gradients are scaled to zero, both keyed on the quad's flat umbra_Mipped (1 mipped, 0 not)
final class AtlasSampling {
    // The flat input the mesh stage feeds from the material's mipmap bit
    static final String MIPPED = "umbra_Mipped";
    // Nvidium's own terrain uses the same -8: past the atlas's four levels, so an unmipped quad samples level 0 wherever it is
    static final String BIAS_DEFINE = "#define UMBRA_ATLAS_BIAS ((" + MIPPED + " - 1.0) * 8.0)\n";

    // Every name Umbra binds the block atlas under in a terrain program
    private static final Set<String> ATLAS = Set.of("tex", "texture", "gtexture", "gcolor");

    // Arguments before an optional bias, for the lookups the hardware picks a level for
    private static final Map<String, Integer> IMPLICIT = Map.of("texture", 2, "texture2D", 2, "textureProj", 2, "texture2DProj", 2,
            "textureOffset", 3, "textureProjOffset", 3);
    // Where the level sits in a lookup that names it
    private static final Map<String, Integer> LOD = Map.of("textureLod", 2, "texture2DLod", 2, "texture2DLodEXT", 2, "textureLodOffset", 2);
    // Where the two gradients sit in a lookup that passes them
    private static final Map<String, Integer> GRAD = Map.of("textureGrad", 2, "texture2DGradARB", 2, "texture2DGradEXT", 2, "textureGradOffset", 2);

    private static final Pattern CALL;
    private static final Pattern COMMENT = Pattern.compile("//[^\\n]*|/\\*(?s:.*?)\\*/");

    static {
        List<String> names = new ArrayList<>(IMPLICIT.keySet());
        names.addAll(LOD.keySet());
        names.addAll(GRAD.keySet());
        CALL = Pattern.compile("\\b(" + String.join("|", names) + ")\\s*\\(");
    }

    private AtlasSampling() {
    }

    // Whether the fragment stage samples the atlas anywhere
    static boolean samplesAtlas(String fragment) {
        return !insertions(fragment).isEmpty();
    }

    // The fragment stage with every atlas lookup keyed on umbra_Mipped; UMBRA_ATLAS_BIAS and the input itself are the caller's to declare
    static String rewrite(String fragment) {
        StringBuilder out = new StringBuilder(fragment);
        // Back to front, so each insertion leaves the positions before it valid
        for (Map.Entry<Integer, String> insertion : insertions(fragment).descendingMap().entrySet()) {
            out.insert(insertion.getKey(), insertion.getValue());
        }
        return out.toString();
    }

    // Text to insert by position for every atlas lookup, nested ones included; comments are blanked first so neither their calls nor their brackets count
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

    // Brackets an argument and appends to it; no two insertions share a position, since an argument is never empty and a nested call's arguments sit strictly inside its brackets
    private static void wrap(TreeMap<Integer, String> insertions, int[] arg, String after) {
        insertions.put(arg[0], "(");
        insertions.put(arg[1], after);
    }

    // The trimmed [start, end) of each top-level argument of the call whose '(' ends just before open, or null when the brackets never close
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

    private static int[] trim(String code, int start, int end) {
        while (start < end && Character.isWhitespace(code.charAt(start))) {
            start++;
        }
        while (end > start && Character.isWhitespace(code.charAt(end - 1))) {
            end--;
        }
        return new int[]{start, end};
    }

    // The source with each comment's characters turned to spaces, newlines kept, so positions still line up
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
