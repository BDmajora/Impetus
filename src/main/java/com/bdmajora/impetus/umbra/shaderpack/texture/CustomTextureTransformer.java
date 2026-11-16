package com.bdmajora.impetus.umbra.shaderpack.texture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Rewriter of sampler names for custom textures.
public final class CustomTextureTransformer {

    // Pattern of a uniform declaration.
    private static final Pattern UNIFORM_DECLARATION =
            Pattern.compile("(?m)^[\\t ]*uniform[\\t ]+(\\w+)[\\t ]+([^;{}()]+);");

    // Active patches.
    private static volatile List<CustomTexturePatch> activePatches = Collections.emptyList();

    // Prevents instantiation
    private CustomTextureTransformer() {
    }

    // Stores the active patches.
    public static void setActivePatches(List<CustomTexturePatch> patches) {
        activePatches = patches == null ? Collections.<CustomTexturePatch>emptyList() : new ArrayList<>(patches);
    }

    // Returns the active patches.
    public static List<CustomTexturePatch> getActivePatches() {
        return activePatches;
    }

    // Rewrites a source with the active patches.
    public static String transform(String programName, String source, TextureStage stage) {
        return transform(programName, source, stage, activePatches);
    }

    // Rewrites a source with the given patches.
    public static String transform(String programName, String source, TextureStage stage,
                                   List<CustomTexturePatch> patches) {
        if (source == null || patches.isEmpty()) {
            return source;
        }

        String declarationScan = null;
        String result = source;
        for (CustomTexturePatch patch : patches) {
            if (patch.getStage() != stage) {
                continue;
            }
            if (declarationScan == null) {
                declarationScan = stripComments(result);
            }
            String declaredType = findSamplerDeclarationType(declarationScan, patch.getSamplerName());
            if (declaredType == null || !typeMatches(patch.getTextureType(), declaredType)) {
                continue;
            }
            result = renameIdentifier(result, patch.getSamplerName(), patch.getNewSamplerName());
            declarationScan = stripComments(result);
        }
        return result;
    }

    // Returns the declared type of a sampler or null.
    private static String findSamplerDeclarationType(String source, String samplerName) {
        Matcher matcher = UNIFORM_DECLARATION.matcher(source);
        while (matcher.find()) {
            String type = matcher.group(1);
            if (!type.toLowerCase(Locale.ROOT).contains("sampler")) {
                continue;
            }
            for (String declared : matcher.group(2).split(",")) {
                String name = declared.trim();
                int bracket = name.indexOf('[');
                if (bracket >= 0) {
                    name = name.substring(0, bracket).trim();
                }
                if (name.equals(samplerName)) {
                    return type;
                }
            }
        }
        return null;
    }

    // Returns whether a declared type fits a texture type.
    private static boolean typeMatches(String textureType, String declaredType) {
        Set<String> accepted = acceptedSamplerTypes(textureType);
        return accepted.contains(declaredType.toLowerCase(Locale.ROOT));
    }

    // Returns the sampler types that fit a texture type.
    private static Set<String> acceptedSamplerTypes(String textureType) {
        String suffix;
        if ("TEXTURE_1D".equalsIgnoreCase(textureType)) {
            suffix = "sampler1d";
        } else if ("TEXTURE_2D".equalsIgnoreCase(textureType)) {
            suffix = "sampler2d";
        } else if ("TEXTURE_3D".equalsIgnoreCase(textureType)) {
            suffix = "sampler3d";
        } else if ("TEXTURE_RECTANGLE".equalsIgnoreCase(textureType)) {
            suffix = "sampler2drect";
        } else {
            return Collections.emptySet();
        }
        Set<String> accepted = new HashSet<>();
        accepted.add(suffix);
        accepted.add("i" + suffix);
        accepted.add("u" + suffix);
        return accepted;
    }

    // Renames every use of an identifier.
    private static String renameIdentifier(String source, String from, String to) {
        return source.replaceAll("\\b" + Pattern.quote(from) + "\\b", Matcher.quoteReplacement(to));
    }

    // Returns a source without comments.
    private static String stripComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//.*$", "");
    }
}
