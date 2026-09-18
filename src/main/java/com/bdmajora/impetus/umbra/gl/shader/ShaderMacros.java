package com.bdmajora.impetus.umbra.gl.shader;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

// OptiFine MC_* preprocessor macros injected into every stage; GL-dependent vendor/renderer macros go through withGlInfo on the render thread, standard() holds the rest
public final class ShaderMacros {
    // Minecraft version encoded the OptiFine way: 1.12.2 -> 11202
    public static final int MC_VERSION = 11202;

    // Programs forced to compile as plain OptiFine 1.12.2 (IS_IRIS/IRIS_VERSION withheld) so they take their authored path; only the manual -Dimpetus.umbra.legacyPrograms override, empty by default, since setPackLegacyPrograms detects pairings like Sildur's gbuffers_water/composite1 automatically
    private static final java.util.Set<String> FORCED_LEGACY_PROGRAMS = parseLegacyPrograms();

    // Programs the loaded pack itself marked as having an authored pre-Umbra path (see ShaderPack.detectLegacyPrograms); replaced on every pack load
    private static volatile java.util.Set<String> packLegacyPrograms = java.util.Collections.emptySet();

    // Installs the pack-detected legacy program list, once per pack load before any program compiles so the terrain and composite paths see the same set
    public static void setPackLegacyPrograms(java.util.Set<String> names) {
        java.util.Set<String> lowered = new java.util.HashSet<>();
        for (String name : names) {
            lowered.add(name.toLowerCase(Locale.ROOT));
        }
        packLegacyPrograms = lowered;
    }

    private ShaderMacros() {
    }

    // Program names the pack marks as legacy, which get the 120-era macro set
    private static java.util.Set<String> parseLegacyPrograms() {
        String value = System.getProperty("impetus.umbra.legacyPrograms", "");
        java.util.Set<String> names = new java.util.HashSet<>();
        for (String name : value.split(",")) {
            String trimmed = name.trim();
            if (!trimmed.isEmpty()) {
                names.add(trimmed.toLowerCase(Locale.ROOT));
            }
        }
        return names;
    }

    // Macro set programName compiles against, identical to the input unless it is a legacy program (Umbra identity macros withheld); callers MUST use the same map for DrawBuffers.parseActive and injectDefines, since mixing points a gl_FragData write at an unbound slot
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

    // Macros that need no GL context (Minecraft version, host OS, baseline quality knobs)
    public static Map<String, String> standard() {
        Map<String, String> macros = new LinkedHashMap<>();
        macros.put("MC_VERSION", Integer.toString(MC_VERSION));
        macros.put(osMacro(), "");
        macros.put("MC_RENDER_QUALITY", "1.0");
        macros.put("MC_SHADOW_QUALITY", "1.0");
        macros.put("MC_HAND_DEPTH", "0.125");
        // PBR sampler availability (OptiFine's default-on normal/specular feature); the `normals`/`specular` samplers are always bound to either the stitched companion atlases or the neutral 1x1 defaults
        macros.put("MC_NORMAL_MAP", "");
        macros.put("MC_SPECULAR_MAP", "");
        // Resource-pack-declared PBR texture format (optifine/texture.properties `format=`), e.g. MC_TEXTURE_FORMAT_LAB_PBR + _1_3; Umbra parity
        com.bdmajora.impetus.umbra.pbr.TextureFormatLoader.addFormatMacros(macros);
        // One IRIS_FEATURE_<NAME> define per implemented Iris extension; Complementary's colored lighting gates on IRIS_FEATURE_CUSTOM_IMAGES and its shadowcomp pass never declares voxel_sampler without it
        com.bdmajora.impetus.umbra.features.FeatureFlags.addUsableDefines(macros);
        // IS_IRIS, IRIS_VERSION and the IRIS_FEATURE_* prefix are the pack-facing contract, NOT our naming: renaming them drops every pack onto its OptiFine path (Complementary's "not supported on Optifine" screen); IS_IRIS gates Iris-exclusive uniform DECLARATIONS, all of which CommonUniforms uploads at MC_VERSION 11202
        macros.put("IS_IRIS", "");
        // Distant Horizons presence, the same two defines Iris's StandardMacros emits: packs gate their dh_* programs and the dhDepthTex reads on DISTANT_HORIZONS, and DISTANT_HORIZONS_TEXTURES tells them dh_sampleTexture() exists; only while DH is installed AND rendering, since a pack compiled with them expects real LOD depth
        if (com.bdmajora.impetus.umbra.compat.dh.DhCompat.hasRenderingEnabled()) {
            macros.put("DISTANT_HORIZONS", "");
            macros.put("DISTANT_HORIZONS_TEXTURES", "");
        }
        // The DH material ids a dh_* program reads off dhMaterialId (EDhApiBlockMaterial's indices), unconditional like Iris so shared includes compile without DH
        String[] dhMaterials = {"UNKNOWN", "LEAVES", "STONE", "WOOD", "METAL", "DIRT", "LAVA", "DEEPSLATE", "SNOW",
                "SAND", "TERRACOTTA", "NETHER_STONE", "WATER", "GRASS", "AIR", "ILLUMINATED"};
        for (int i = 0; i < dhMaterials.length; i++) {
            macros.put("DH_BLOCK_" + dhMaterials[i], Integer.toString(i));
        }
        // Iris version as major*10000 + minor*100 + bugfix; Complementary takes the precise cameraPositionFract split at >= 10800 (fixing colored-lighting shimmer far from origin), and 10805 is the lowest value enabling that while leaving every legacy `IRIS_VERSION < N` workaround where undefined left it
        macros.put("IRIS_VERSION", "10805");
        // Umbra render-stage constants (WorldRenderingPhase ordinals, exact Umbra order) for the renderStage uniform.
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

    // Adds the GL-context-dependent macros (GLSL version and vendor/renderer family) to an existing macro map
    public static void withGlInfo(Map<String, String> macros, int glslVersion, int glVersion, String vendor, String renderer) {
        macros.put("MC_GL_VERSION", Integer.toString(glVersion));
        macros.put("MC_GLSL_VERSION", Integer.toString(glslVersion));
        withGpuIdentity(macros, vendor, renderer);
    }

    // Adds only the vendor/renderer identity macros packs gate hardware workarounds on; skips MC_GL_VERSION/MC_GLSL_VERSION since programs compile at 120, 330 or 460 depending on path and advertising the real version invites GLSL-120 programs to use syntax they cannot take
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

    // Inserts #define lines right after #version (or at the top); used by the fullscreen/terrain/compute paths that skip ShaderProgramCompiler's define application
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

    // MC_OS_WINDOWS, MC_OS_LINUX or MC_OS_MAC from the JVM's os.name
    private static String osMacro() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return "MC_OS_WINDOWS";
        } else if (os.contains("mac") || os.contains("darwin")) {
            return "MC_OS_MAC";
        } else if (os.contains("nux") || os.contains("nix")) {
            return "MC_OS_LINUX";
        }
        return "MC_OS_OTHER";
    }

    // Matches Umbra's StandardMacros.getVendor() exactly (OptiFine's documented behaviour); prefix tests, not substring, see rendererMacro
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
            // Umbra reports AMD separately from ATI; folding it into ATI only satisfied Complementary's `AMD || ATI` test by luck, and a pack testing MC_GL_VENDOR_AMD alone would take the wrong branch
            return "MC_GL_VENDOR_AMD";
        } else if (v.startsWith("x.org")) {
            return "MC_GL_VENDOR_XORG";
        }
        return "MC_GL_VENDOR_OTHER";
    }

    // Matches Umbra's StandardMacros.getRenderer() exactly including test order; prefix tests, since contains("intel") misfired on "Mesa Intel(R) Arc(tm)" and advertised Arc as an ancient iGPU
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
