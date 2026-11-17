package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.pipeline.shadow.ShadowColorSettings;
import com.bdmajora.impetus.umbra.shaderpack.texture.TextureStage;
import com.bdmajora.impetus.umbra.targets.UmbraRenderTargets;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

// Texture unit layout of the pack samplers.
public final class SamplerLayout {
    // Depth tex 0 unit.
    public static final int DEPTH_TEX_0_UNIT = 16;
    // Depth tex 1 unit.
    public static final int DEPTH_TEX_1_UNIT = 17;
    // Depth tex 2 unit.
    public static final int DEPTH_TEX_2_UNIT = 18;
    // Shadow tex 0 unit.
    public static final int SHADOW_TEX_0_UNIT = 19;
    // Shadow tex 1 unit.
    public static final int SHADOW_TEX_1_UNIT = 20;
    // Shadow color 0 unit.
    public static final int SHADOW_COLOR_0_UNIT = 21;
    // Shadow color 1 unit.
    public static final int SHADOW_COLOR_1_UNIT = 22;
    // Noise tex unit.
    public static final int NOISE_TEX_UNIT = 23;
    // Shadow tex 0 hw unit.
    public static final int SHADOW_TEX_0_HW_UNIT = 24;
    // Shadow tex 1 hw unit.
    public static final int SHADOW_TEX_1_HW_UNIT = 25;
    // Mipmap scratch unit.
    public static final int MIPMAP_SCRATCH_UNIT = 32;
    // Depth copy scratch unit.
    public static final int DEPTH_COPY_SCRATCH_UNIT = 33;
    // Overlay tex unit.
    public static final int OVERLAY_TEX_UNIT = 34;
    // Dh depth tex 0 unit.
    public static final int DH_DEPTH_TEX_0_UNIT = 35;
    // Dh depth tex 1 unit.
    public static final int DH_DEPTH_TEX_1_UNIT = 36;
    // Shadow color 2 unit.
    public static final int SHADOW_COLOR_2_UNIT = 37;
    // Gbuffer shadow tex 0 unit.
    public static final int GBUFFER_SHADOW_TEX_0_UNIT = 4;
    // Gbuffer shadow tex 1 unit.
    public static final int GBUFFER_SHADOW_TEX_1_UNIT = 5;
    // Gbuffer depth tex 0 unit.
    public static final int GBUFFER_DEPTH_TEX_0_UNIT = 6;
    // Gbuffer overlay unit.
    public static final int GBUFFER_OVERLAY_UNIT = 11;
    // Gbuffer depth tex 1 unit.
    public static final int GBUFFER_DEPTH_TEX_1_UNIT = 12;
    // Gbuffer shadow color 0 unit.
    public static final int GBUFFER_SHADOW_COLOR_0_UNIT = 13;
    // Gbuffer shadow color 1 unit.
    public static final int GBUFFER_SHADOW_COLOR_1_UNIT = 14;
    // Gbuffer noise tex unit.
    public static final int GBUFFER_NOISE_TEX_UNIT = 15;
    // First gaux target.
    public static final int GAUX_FIRST = 4;
    // Last gaux target.
    public static final int GAUX_LAST = 7;
    // Gbuffer attachment limit.
    public static final int GBUFFER_ATTACHMENT_LIMIT = UmbraRenderTargets.MAX_COLOR_BUFFERS;
    // Custom tex first unit.
    public static final int CUSTOM_TEX_FIRST_UNIT = SHADOW_TEX_0_HW_UNIT;
    // Custom tex last unit.
    public static final int CUSTOM_TEX_LAST_UNIT = MIPMAP_SCRATCH_UNIT - 1;
    // Units bound for every frame.
    static final int[] FRAME_UNITS = {SHADOW_COLOR_0_UNIT, SHADOW_COLOR_1_UNIT, SHADOW_TEX_0_UNIT, SHADOW_TEX_1_UNIT,
            SHADOW_TEX_0_HW_UNIT, SHADOW_TEX_1_HW_UNIT, NOISE_TEX_UNIT, GBUFFER_DEPTH_TEX_0_UNIT,
            GBUFFER_DEPTH_TEX_1_UNIT, GBUFFER_SHADOW_COLOR_0_UNIT, GBUFFER_SHADOW_COLOR_1_UNIT,
            GBUFFER_SHADOW_TEX_0_UNIT, GBUFFER_SHADOW_TEX_1_UNIT, GBUFFER_NOISE_TEX_UNIT,
            DH_DEPTH_TEX_0_UNIT, DH_DEPTH_TEX_1_UNIT};

    // Names of the legacy color targets.
    private static final String[] LEGACY = UmbraRenderTargets.LEGACY_COLOR_TARGETS;
    private static final Map<String, Integer> COLOR_TARGETS_BY_NAME;
    private static final Map<String, Integer> FULLSCREEN;
    private static final Map<String, Integer> GBUFFER;
    private static final Map<TextureStage, Map<String, Integer>> BY_STAGE;
    // Units of the gbuffer color targets.
    private static final int[] GBUFFER_COLOR_UNITS = new int[UmbraRenderTargets.MAX_COLOR_BUFFERS];

    static {
        Map<String, Integer> names = new LinkedHashMap<>();
        Map<String, Integer> fullscreen = new LinkedHashMap<>();
        Map<String, Integer> gbuffer = new LinkedHashMap<>();
        for (int i = 0; i < UmbraRenderTargets.MAX_COLOR_BUFFERS; i++) {
            names.put("colortex" + i, i);
            fullscreen.put("colortex" + i, i);
            if (i < LEGACY.length) {
                names.put(LEGACY[i], i);
                fullscreen.put(LEGACY[i], i);
            }
            GBUFFER_COLOR_UNITS[i] = i >= GAUX_FIRST && i <= GAUX_LAST ? i + 3 : -1;
        }
        for (int i = GAUX_FIRST; i <= GAUX_LAST; i++) {
            gbuffer.put("colortex" + i, GBUFFER_COLOR_UNITS[i]);
            gbuffer.put(LEGACY[i], GBUFFER_COLOR_UNITS[i]);
        }
        String[] shared = {"depthtex0", "gdepthtex", "dhDepthTex", "dhDepthTex0", "depthtex1", "dhDepthTex1", "depthtex2",
                "shadowcolor1", "shadowcolor0", "shadowcolor", "shadowtex0", "shadowtex0DH", "shadow", "watershadow",
                "shadowtex1", "shadowtex1DH", "shadowtex0HW", "shadowtex1HW", "noisetex"};
        int[] sharedUnits = {DEPTH_TEX_0_UNIT, DEPTH_TEX_0_UNIT, DH_DEPTH_TEX_0_UNIT, DH_DEPTH_TEX_0_UNIT, DEPTH_TEX_1_UNIT,
                DH_DEPTH_TEX_1_UNIT, DEPTH_TEX_2_UNIT, SHADOW_COLOR_1_UNIT, SHADOW_COLOR_0_UNIT, SHADOW_COLOR_0_UNIT,
                SHADOW_TEX_0_UNIT, SHADOW_TEX_0_UNIT, SHADOW_TEX_0_UNIT, SHADOW_TEX_0_UNIT, SHADOW_TEX_1_UNIT,
                SHADOW_TEX_1_UNIT, SHADOW_TEX_0_HW_UNIT, SHADOW_TEX_1_HW_UNIT, NOISE_TEX_UNIT};
        for (int i = 0; i < shared.length; i++) {
            fullscreen.put(shared[i], sharedUnits[i]);
            gbuffer.put(shared[i], sharedUnits[i]);
        }
        for (int i = ShadowColorSettings.OPTIFINE_BUFFERS; i < ShadowColorSettings.IRIS_BUFFERS; i++) {
            fullscreen.put("shadowcolor" + i, shadowColorUnit(i));
            gbuffer.put("shadowcolor" + i, shadowColorUnit(i));
        }
        String[] pbr = {"normals", "texNorm", "specular", "texSpecular"};
        int[] pbrUnits = {2, 2, 3, 3};
        for (int i = 0; i < pbr.length; i++) {
            fullscreen.put(pbr[i], pbrUnits[i]);
            gbuffer.put(pbr[i], pbrUnits[i]);
        }
        String[] gbufferOnly = {"depthtex0", "gdepthtex", "depthtex1", "shadowcolor1", "shadowcolor0", "shadowcolor",
                "iris_overlay", "shadowtex0", "shadowtex0DH", "shadow", "watershadow", "shadowtex1", "shadowtex1DH",
                "noisetex"};
        int[] gbufferOnlyUnits = {GBUFFER_DEPTH_TEX_0_UNIT, GBUFFER_DEPTH_TEX_0_UNIT, GBUFFER_DEPTH_TEX_1_UNIT,
                GBUFFER_SHADOW_COLOR_1_UNIT, GBUFFER_SHADOW_COLOR_0_UNIT, GBUFFER_SHADOW_COLOR_0_UNIT, GBUFFER_OVERLAY_UNIT,
                GBUFFER_SHADOW_TEX_0_UNIT, GBUFFER_SHADOW_TEX_0_UNIT, GBUFFER_SHADOW_TEX_0_UNIT, GBUFFER_SHADOW_TEX_0_UNIT,
                GBUFFER_SHADOW_TEX_1_UNIT, GBUFFER_SHADOW_TEX_1_UNIT, GBUFFER_NOISE_TEX_UNIT};
        for (int i = 0; i < gbufferOnly.length; i++) {
            gbuffer.put(gbufferOnly[i], gbufferOnlyUnits[i]);
        }
        COLOR_TARGETS_BY_NAME = Collections.unmodifiableMap(names);
        FULLSCREEN = Collections.unmodifiableMap(fullscreen);
        GBUFFER = Collections.unmodifiableMap(gbuffer);
        Map<TextureStage, Map<String, Integer>> byStage = new EnumMap<>(TextureStage.class);
        for (TextureStage stage : TextureStage.values()) {
            byStage.put(stage, stage == TextureStage.GBUFFERS_AND_SHADOW ? GBUFFER : FULLSCREEN);
        }
        BY_STAGE = Collections.unmodifiableMap(byStage);
    }

    // Prevents instantiation
    private SamplerLayout() {
    }

    // Returns the unit of a shadow color.
    public static int shadowColorUnit(int index) {
        return SHADOW_COLOR_2_UNIT + index - ShadowColorSettings.OPTIFINE_BUFFERS;
    }

    // Returns the unit of a gbuffer color target.
    public static int gbufferColorUnit(int index) {
        return index >= 0 && index < GBUFFER_COLOR_UNITS.length ? GBUFFER_COLOR_UNITS[index] : -1;
    }

    // Returns the units of a fullscreen pass by sampler name.
    public static Map<String, Integer> fullscreen() {
        return FULLSCREEN;
    }

    // Returns the units of a gbuffer pass by sampler name.
    public static Map<String, Integer> gbuffer() {
        return GBUFFER;
    }

    // Returns the sampler units of one texture stage by sampler name.
    public static Map<String, Integer> forStage(TextureStage stage) {
        return stage == TextureStage.GBUFFERS_AND_SHADOW ? GBUFFER : FULLSCREEN;
    }

    // Returns the sampler units of every texture stage.
    public static Map<TextureStage, Map<String, Integer>> byStage() {
        return BY_STAGE;
    }

    // Returns the color target indices by name.
    public static Map<String, Integer> colorTargetsByName() {
        return COLOR_TARGETS_BY_NAME;
    }

    // Returns whether a layout is the gbuffer layout.
    public static boolean isGbufferLayout(Map<String, Integer> units) {
        return units.getOrDefault("depthtex0", -1) == GBUFFER_DEPTH_TEX_0_UNIT;
    }
}
