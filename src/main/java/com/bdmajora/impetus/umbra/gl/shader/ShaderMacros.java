package com.bdmajora.impetus.umbra.gl.shader;

import com.bdmajora.impetus.umbra.compat.dh.DhCompat;
import com.bdmajora.impetus.umbra.features.FeatureFlags;
import com.bdmajora.impetus.umbra.pbr.TextureFormatLoader;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

// Standard macros defined for pack shaders.
public final class ShaderMacros {
    // Game version number.
    public static final int MC_VERSION = 11202;

    // Forced legacy programs.
    private static final Set<String> FORCED_LEGACY_PROGRAMS = parseLegacyPrograms();

    // Pack legacy programs.
    private static volatile Set<String> packLegacyPrograms = Collections.emptySet();

    // Stores the pack legacy programs.
    public static void setPackLegacyPrograms(Set<String> names) {
        Set<String> lowered = new HashSet<>();
        for (String name : names) {
            lowered.add(name.toLowerCase(Locale.ROOT));
        }
        packLegacyPrograms = lowered;
    }

    // Prevents instantiation
    private ShaderMacros() {
    }

    // Returns the programs named in the legacy property.
    private static Set<String> parseLegacyPrograms() {
        String value = System.getProperty("impetus.umbra.legacyPrograms", "");
        Set<String> names = new HashSet<>();
        for (String name : value.split(",")) {
            String trimmed = name.trim();
            if (!trimmed.isEmpty()) {
                names.add(trimmed.toLowerCase(Locale.ROOT));
            }
        }
        return names;
    }

    // Returns the macros a program compiles against.
    public static Map<String, String> forProgram(Map<String, String> macros, String programName) {
        if (programName == null) {
            return macros;
        }
        String key = programName.toLowerCase(Locale.ROOT);
        if (!FORCED_LEGACY_PROGRAMS.contains(key) && !packLegacyPrograms.contains(key)) {
            return macros;
        }
        Map<String, String> scoped = new LinkedHashMap<>(macros);
        scoped.remove("IS_IRIS");
        scoped.remove("IRIS_VERSION");
        return scoped;
    }

    // Returns the standard macros.
    public static Map<String, String> standard() {
        Map<String, String> macros = new LinkedHashMap<>();
        macros.put("MC_VERSION", Integer.toString(MC_VERSION));
        macros.put(osMacro(), "");
        macros.put("MC_RENDER_QUALITY", "1.0");
        macros.put("MC_SHADOW_QUALITY", "1.0");
        macros.put("MC_HAND_DEPTH", "0.125");
        macros.put("MC_NORMAL_MAP", "");
        macros.put("MC_SPECULAR_MAP", "");
        TextureFormatLoader.addFormatMacros(macros);
        FeatureFlags.addUsableDefines(macros);
        macros.put("IS_IRIS", "");
        if (DhCompat.hasRenderingEnabled()) {
            macros.put("DISTANT_HORIZONS", "");
            macros.put("DISTANT_HORIZONS_TEXTURES", "");
        }
        String[] dhMaterials = {"UNKNOWN", "LEAVES", "STONE", "WOOD", "METAL", "DIRT", "LAVA", "DEEPSLATE", "SNOW",
                "SAND", "TERRACOTTA", "NETHER_STONE", "WATER", "GRASS", "AIR", "ILLUMINATED"};
        for (int i = 0; i < dhMaterials.length; i++) {
            macros.put("DH_BLOCK_" + dhMaterials[i], Integer.toString(i));
        }
        macros.put("IRIS_VERSION", "10805");
        macros.put("MC_RENDER_STAGE_NONE", "0");
        macros.put("MC_RENDER_STAGE_SKY", "1");
        macros.put("MC_RENDER_STAGE_SUNSET", "2");
        macros.put("MC_RENDER_STAGE_CUSTOM_SKY", "3");
        macros.put("MC_RENDER_STAGE_SUN", "4");
        macros.put("MC_RENDER_STAGE_MOON", "5");
        macros.put("MC_RENDER_STAGE_STARS", "6");
        macros.put("MC_RENDER_STAGE_VOID", "7");
        macros.put("MC_RENDER_STAGE_TERRAIN_SOLID", "8");
        macros.put("MC_RENDER_STAGE_TERRAIN_CUTOUT_MIPPED", "9");
        macros.put("MC_RENDER_STAGE_TERRAIN_CUTOUT", "10");
        macros.put("MC_RENDER_STAGE_ENTITIES", "11");
        macros.put("MC_RENDER_STAGE_BLOCK_ENTITIES", "12");
        macros.put("MC_RENDER_STAGE_DESTROY", "13");
        macros.put("MC_RENDER_STAGE_OUTLINE", "14");
        macros.put("MC_RENDER_STAGE_DEBUG", "15");
        macros.put("MC_RENDER_STAGE_HAND_SOLID", "16");
        macros.put("MC_RENDER_STAGE_TERRAIN_TRANSLUCENT", "17");
        macros.put("MC_RENDER_STAGE_TRIPWIRE", "18");
        macros.put("MC_RENDER_STAGE_PARTICLES", "19");
        macros.put("MC_RENDER_STAGE_CLOUDS", "20");
        macros.put("MC_RENDER_STAGE_RAIN_SNOW", "21");
        macros.put("MC_RENDER_STAGE_WORLD_BORDER", "22");
        macros.put("MC_RENDER_STAGE_HAND_TRANSLUCENT", "23");
        return macros;
    }

    // Returns a copy with the gl info.
    public static void withGlInfo(Map<String, String> macros, int glslVersion, int glVersion, String vendor, String renderer) {
        macros.put("MC_GL_VERSION", Integer.toString(glVersion));
        macros.put("MC_GLSL_VERSION", Integer.toString(glslVersion));
        withGpuIdentity(macros, vendor, renderer);
    }

    // Returns a copy with the gpu identity.
    public static void withGpuIdentity(Map<String, String> macros, String vendor, String renderer) {
        String vendorMacro = vendorMacro(vendor);
        if (vendorMacro != null) {
            macros.put(vendorMacro, "");
        }
        String rendererMacro = rendererMacro(renderer);
        if (rendererMacro != null) {
            macros.put(rendererMacro, "");
        }
    }

    // Adds define lines after the version line.
    public static String injectDefines(String source, Map<String, String> macros) {
        StringBuilder defines = new StringBuilder();
        for (Map.Entry<String, String> macro : macros.entrySet()) {
            defines.append("#define ").append(macro.getKey());
            if (!macro.getValue().isEmpty()) {
                defines.append(' ').append(macro.getValue());
            }
            defines.append('\n');
        }
        String[] lines = source.split("\n", -1);
        int versionIndex = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].trim().startsWith("#version")) {
                versionIndex = i;
                break;
            }
        }
        if (versionIndex < 0) {
            return defines + source;
        }
        StringBuilder out = new StringBuilder(source.length() + defines.length());
        out.append(lines[versionIndex]).append('\n').append(defines);
        for (int i = 0; i < lines.length; i++) {
            if (i == versionIndex || lines[i].trim().startsWith("#version")) {
                continue;
            }
            out.append(lines[i]);
            if (i + 1 < lines.length) {
                out.append('\n');
            }
        }
        return out.toString();
    }

    // Returns the macro of the operating system.
    private static String osMacro() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("mac") || os.contains("darwin")) {
            return "MC_OS_MAC";
        } else if (os.contains("win")) {
            return "MC_OS_WINDOWS";
        } else if (os.contains("nux") || os.contains("nix")) {
            return "MC_OS_LINUX";
        }
        return "MC_OS_OTHER";
    }

    // Returns the macro of a GPU vendor.
    private static String vendorMacro(String vendor) {
        if (vendor == null) {
            return "MC_GL_VENDOR_OTHER";
        }
        String v = vendor.toLowerCase(Locale.ROOT);
        if (v.startsWith("ati")) {
            return "MC_GL_VENDOR_ATI";
        } else if (v.startsWith("intel")) {
            return "MC_GL_VENDOR_INTEL";
        } else if (v.startsWith("nvidia")) {
            return "MC_GL_VENDOR_NVIDIA";
        } else if (v.startsWith("amd")) {
            return "MC_GL_VENDOR_AMD";
        } else if (v.startsWith("x.org")) {
            return "MC_GL_VENDOR_XORG";
        }
        return "MC_GL_VENDOR_OTHER";
    }

    // Returns the macro of a renderer name.
    private static String rendererMacro(String renderer) {
        if (renderer == null) {
            return "MC_GL_RENDERER_OTHER";
        }
        String r = renderer.toLowerCase(Locale.ROOT);
        if (r.startsWith("amd") || r.startsWith("ati") || r.startsWith("radeon")) {
            return "MC_GL_RENDERER_RADEON";
        } else if (r.startsWith("gallium")) {
            return "MC_GL_RENDERER_GALLIUM";
        } else if (r.startsWith("intel")) {
            return "MC_GL_RENDERER_INTEL";
        } else if (r.startsWith("geforce") || r.startsWith("nvidia")) {
            return "MC_GL_RENDERER_GEFORCE";
        } else if (r.startsWith("quadro") || r.startsWith("nvs")) {
            return "MC_GL_RENDERER_QUADRO";
        } else if (r.startsWith("mesa")) {
            return "MC_GL_RENDERER_MESA";
        } else if (r.startsWith("apple")) {
            return "MC_GL_RENDERER_APPLE";
        }
        return "MC_GL_RENDERER_OTHER";
    }
}
