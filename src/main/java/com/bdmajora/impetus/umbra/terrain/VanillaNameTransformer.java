package com.bdmajora.impetus.umbra.terrain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Rewriter of vanilla attribute names to engine names.
public final class VanillaNameTransformer {
    private static final Map<String, String> REPLACEMENTS = new LinkedHashMap<>();

    static {
        REPLACEMENTS.put("vaPosition", "gl_Vertex.xyz");
        REPLACEMENTS.put("vaNormal", "gl_Normal");
        REPLACEMENTS.put("vaColor", "gl_Color");
        REPLACEMENTS.put("vaUV0", "gl_MultiTexCoord0.xy");
        REPLACEMENTS.put("vaUV1", "@(0.0, 10.0)");
        REPLACEMENTS.put("vaUV2", "@(gl_MultiTexCoord1.xy)");

        REPLACEMENTS.put("modelViewMatrixInverse", "gl_ModelViewMatrixInverse");
        REPLACEMENTS.put("modelViewMatrix", "gl_ModelViewMatrix");
        REPLACEMENTS.put("projectionMatrixInverse", "gl_ProjectionMatrixInverse");
        REPLACEMENTS.put("projectionMatrix", "gl_ProjectionMatrix");
        REPLACEMENTS.put("normalMatrix", "gl_NormalMatrix");
        REPLACEMENTS.put("textureMatrix", "gl_TextureMatrix[0]");

        REPLACEMENTS.put("chunkOffset", "vec3(0.0)");
    }

    // Pattern of a declaration.
    private static final Pattern DECLARATION = Pattern.compile(
            "(?m)^([\\t ]*(?:flat[\\t ]+|smooth[\\t ]+|noperspective[\\t ]+|centroid[\\t ]+)*"
                    + "(?:attribute|in|uniform)[\\t ]+)(\\w+)([\\t ]+)"
                    + "([A-Za-z_]\\w*(?:[\\t ]*,[\\t ]*[A-Za-z_]\\w*)*)[\\t ]*;[^\\n]*$");

    // Typed placeholder.
    private static final Pattern TYPED_PLACEHOLDER = Pattern.compile("@\\(");

    // Pattern of a GLSL type.
    private static final String GLSL_TYPE =
            "(?:bool|int|uint|float|double|[bidu]?vec[234]|d?mat[234](?:x[234])?"
                    + "|[iu]?sampler[123]D(?:Array|Rect)?(?:Shadow)?|[iu]?samplerCube(?:Shadow)?)";

    // Prevents instantiation
    private VanillaNameTransformer() {
    }

    // Returns whether a source declares a name.
    private static boolean declaresLocally(String source, String name) {
        return Pattern.compile("(?<![A-Za-z0-9_])" + GLSL_TYPE + "\\s+" + Pattern.quote(name) + "\\s*(?=[,)=;\\[])")
                .matcher(source)
                .find();
    }

    // Returns whether a source uses modern attribute names.
    public static boolean isModernNamed(String source) {
        if (source == null) {
            return false;
        }
        for (String name : REPLACEMENTS.keySet()) {
            if (source.contains(name)) {
                return true;
            }
        }
        return false;
    }

    // Returns the rewritten source.
    public static String transform(String source) {
        if (!isModernNamed(source)) {
            return source;
        }

        Map<String, String> declaredTypes = new LinkedHashMap<>();
        Matcher declarations = DECLARATION.matcher(source);
        StringBuffer stripped = new StringBuffer(source.length());
        while (declarations.find()) {
            String type = declarations.group(2);
            List<String> kept = new ArrayList<>();
            boolean sawModern = false;
            for (String declarator : declarations.group(4).split(",")) {
                String name = declarator.trim();
                if (REPLACEMENTS.containsKey(name)) {
                    declaredTypes.put(name, type);
                    sawModern = true;
                } else {
                    kept.add(name);
                }
            }
            if (!sawModern) {
                declarations.appendReplacement(stripped, Matcher.quoteReplacement(declarations.group()));
            } else {
                declarations.appendReplacement(stripped, kept.isEmpty() ? ""
                        : Matcher.quoteReplacement(declarations.group(1) + type + declarations.group(3)
                                + String.join(", ", kept) + ";"));
            }
        }
        declarations.appendTail(stripped);
        String result = stripped.toString();

        for (Map.Entry<String, String> entry : REPLACEMENTS.entrySet()) {
            if (!declaredTypes.containsKey(entry.getKey()) && declaresLocally(result, entry.getKey())) {
                continue;
            }
            String replacement = entry.getValue();
            if (TYPED_PLACEHOLDER.matcher(replacement).find()) {
                String declared = declaredTypes.get(entry.getKey());
                String type = "ivec2".equals(declared) ? "ivec2" : "vec2";
                replacement = replacement.replace("@(", type + "(");
            }
            result = result.replaceAll("(?<![A-Za-z0-9_])" + Pattern.quote(entry.getKey()) + "(?![A-Za-z0-9_])",
                    Matcher.quoteReplacement(replacement));
        }
        return result;
    }
}
