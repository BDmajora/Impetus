package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.pipeline.shadow.ShadowColorSettings;
import com.bdmajora.impetus.umbra.shaderpack.texture.TextureStage;
import com.bdmajora.impetus.umbra.targets.UmbraRenderTargets;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

// Texture units for this fixed-unit 1.12 bridge: fullscreen programs keep colortexN -> unit N, gbuffers programs use OptiFine's 1.12 aux slots for gaux1..4/colortex4..7
public final class SamplerLayout {
    public static final int DEPTH_TEX_0_UNIT = 16;
    public static final int DEPTH_TEX_1_UNIT = 17;
    public static final int DEPTH_TEX_2_UNIT = 18;
    public static final int SHADOW_TEX_0_UNIT = 19;
    public static final int SHADOW_TEX_1_UNIT = 20;
    public static final int SHADOW_COLOR_0_UNIT = 21;
    public static final int SHADOW_COLOR_1_UNIT = 22;
    public static final int NOISE_TEX_UNIT = 23;
    public static final int SHADOW_TEX_0_HW_UNIT = 24;
    public static final int SHADOW_TEX_1_HW_UNIT = 25;
    // Scratch unit for mipmap generation and reset, ABOVE every sampler allocation; raw binds on unit 0 desync GlStateManager's cache so bindColorSamplers' "already bound" checks skip the real rebind (BSL deferred1 read the black colortex6 and the screen went black)
    public static final int MIPMAP_SCRATCH_UNIT = 32;
    // High texture unit used transiently for depth-copy binds so no vanilla-tracked unit is disturbed
    public static final int DEPTH_COPY_SCRATCH_UNIT = 33;
    // The fullscreen-stage bind of the 1x1 iris_overlay dummy, ABOVE the sampleable range on purpose since no composite/deferred/final program declares it and a low unit only starved the pack's samplers
    public static final int OVERLAY_TEX_UNIT = 34;
    // Distant Horizons' LOD depth (dhDepthTex0/1), above every fixed and pack-allocated unit and shared by both layouts; any unit below GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS (48 minimum on 3.3) is addressable, and without DH they alias depthtex0/1
    public static final int DH_DEPTH_TEX_0_UNIT = 35;
    public static final int DH_DEPTH_TEX_1_UNIT = 36;
    // shadowcolor2..7, which only exist under HIGHER_SHADOWCOLOR; shared by both layouts above the DH units, all still under the 48 combined units GL 3.3 guarantees
    public static final int SHADOW_COLOR_2_UNIT = 37;
    // OptiFine 1.12 gbuffers-stage units from Shaders.useProgram(): texture/lightmap/normals/specular 0..3, shadow maps 4/5, depthtex0 6, gaux1..4 7..10, depthtex1 12, shadowcolor0/1 13/14, noisetex 15
    public static final int GBUFFER_SHADOW_TEX_0_UNIT = 4;
    public static final int GBUFFER_SHADOW_TEX_1_UNIT = 5;
    public static final int GBUFFER_DEPTH_TEX_0_UNIT = 6;
    // iris_overlay on the gbuffer stage; 1.12.2 draws the hurt flash fixed-function, so a fully transparent 1x1 sits on unit 11, the one gap in OptiFine's 1.12 layout
    public static final int GBUFFER_OVERLAY_UNIT = 11;
    public static final int GBUFFER_DEPTH_TEX_1_UNIT = 12;
    public static final int GBUFFER_SHADOW_COLOR_0_UNIT = 13;
    public static final int GBUFFER_SHADOW_COLOR_1_UNIT = 14;
    public static final int GBUFFER_NOISE_TEX_UNIT = 15;
    // The gaux1..4 / colortex4..7 range the gbuffer stage samples on units 7..10
    public static final int GAUX_FIRST = 4;
    public static final int GAUX_LAST = 7;
    // Highest logical colortex index gbuffer stages may address; FBO attachment points are packed
    public static final int GBUFFER_ATTACHMENT_LIMIT = UmbraRenderTargets.MAX_COLOR_BUFFERS;
    // Lowest unit the pack's custom textures and images may use; the shadowtex*HW units are only real under SEPARATE_HARDWARE_SAMPLERS, otherwise they go to the pack (Complementary needs seven custom units and lost two at 26)
    public static final int CUSTOM_TEX_FIRST_UNIT = SHADOW_TEX_0_HW_UNIT;
    // Highest unit a custom texture may take, below the fixed scratch/overlay/DH/shadowcolor units; a driver reporting more than 32 units used to hand the pack units 32+ that those binds then overwrote
    public static final int CUSTOM_TEX_LAST_UNIT = MIPMAP_SCRATCH_UNIT - 1;
    // Every high unit the pipeline binds for the frame and unbinds at the end, in restore order
    static final int[] FRAME_UNITS = {SHADOW_COLOR_0_UNIT, SHADOW_COLOR_1_UNIT, SHADOW_TEX_0_UNIT, SHADOW_TEX_1_UNIT,
            SHADOW_TEX_0_HW_UNIT, SHADOW_TEX_1_HW_UNIT, NOISE_TEX_UNIT, GBUFFER_DEPTH_TEX_0_UNIT,
            GBUFFER_DEPTH_TEX_1_UNIT, GBUFFER_SHADOW_COLOR_0_UNIT, GBUFFER_SHADOW_COLOR_1_UNIT,
            GBUFFER_SHADOW_TEX_0_UNIT, GBUFFER_SHADOW_TEX_1_UNIT, GBUFFER_NOISE_TEX_UNIT,
            DH_DEPTH_TEX_0_UNIT, DH_DEPTH_TEX_1_UNIT};

    private static final String[] LEGACY = UmbraRenderTargets.LEGACY_COLOR_TARGETS;
    // Sampler name -> logical colortex index, independent from the texture unit chosen for a stage
    private static final Map<String, Integer> COLOR_TARGETS_BY_NAME;
    // Sampler name -> texture unit for deferred/composite/final programs
    private static final Map<String, Integer> FULLSCREEN;
    // Sampler name -> texture unit for gbuffers/shadow-stage programs
    private static final Map<String, Integer> GBUFFER;
    private static final Map<TextureStage, Map<String, Integer>> BY_STAGE;
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
            // OptiFine 1.12 gbuffers stage: gaux1..4 live on units 7..10, and the colortex4..7 aliases share them
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
        // Shadow samplers are parked on unused units so a shadow-reading pack samples nothing instead of colortex0 (sampler uniforms default to unit 0)
        for (int i = 0; i < shared.length; i++) {
            fullscreen.put(shared[i], sharedUnits[i]);
            gbuffer.put(shared[i], sharedUnits[i]);
        }
        for (int i = ShadowColorSettings.OPTIFINE_BUFFERS; i < ShadowColorSettings.IRIS_BUFFERS; i++) {
            fullscreen.put("shadowcolor" + i, shadowColorUnit(i));
            gbuffer.put("shadowcolor" + i, shadowColorUnit(i));
        }
        // OptiFine gbuffer-stage PBR samplers; during fullscreen passes these units are also colortex2/3, so the mapping stays correct for packs leaving the aliases in shared includes
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

    private SamplerLayout() {
    }

    // The shared unit of shadowcolor2..7
    public static int shadowColorUnit(int index) {
        return SHADOW_COLOR_2_UNIT + index - ShadowColorSettings.OPTIFINE_BUFFERS;
    }

    // The gbuffer-stage unit of a colortex, or -1 for the ones the gbuffer stage cannot sample
    public static int gbufferColorUnit(int index) {
        return index >= 0 && index < GBUFFER_COLOR_UNITS.length ? GBUFFER_COLOR_UNITS[index] : -1;
    }

    // The fullscreen layout, read-only
    public static Map<String, Integer> fullscreen() {
        return FULLSCREEN;
    }

    // The gbuffer and shadow layout, read-only
    public static Map<String, Integer> gbuffer() {
        return GBUFFER;
    }

    // Layout for one stage; composite and final share, gbuffer differs
    public static Map<String, Integer> forStage(TextureStage stage) {
        return stage == TextureStage.GBUFFERS_AND_SHADOW ? GBUFFER : FULLSCREEN;
    }

    // Every stage's layout, built once
    public static Map<TextureStage, Map<String, Integer>> byStage() {
        return BY_STAGE;
    }

    // Sampler name -> colortex index, both spellings
    public static Map<String, Integer> colorTargetsByName() {
        return COLOR_TARGETS_BY_NAME;
    }

    // Distinguishes the two fixed layouts by a marker name, so a copy of either still answers
    public static boolean isGbufferLayout(Map<String, Integer> units) {
        return units.getOrDefault("depthtex0", -1) == GBUFFER_DEPTH_TEX_0_UNIT;
    }
}
