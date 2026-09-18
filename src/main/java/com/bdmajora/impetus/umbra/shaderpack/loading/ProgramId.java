package com.bdmajora.impetus.umbra.shaderpack.loading;

import com.bdmajora.impetus.umbra.gl.blending.BlendMode;
import com.bdmajora.impetus.lwjgl.GL11;

import java.util.Locale;

// Every program a 1.12.2 pack may declare (gbuffers_*, shadow*, final); numbered families live in ProgramArrayId, and an id only names a triple that may exist
public enum ProgramId {
    // --- "Basic"/sky/textured family ---
    Basic("gbuffers_basic"),
    Line("gbuffers_line", Basic),
    Textured("gbuffers_textured", Basic),
    TexturedLit("gbuffers_textured_lit", Textured),
    SkyBasic("gbuffers_skybasic", Basic),
    SkyTextured("gbuffers_skytextured", Textured),
    Clouds("gbuffers_clouds", Textured),

    // --- Terrain family ---
    Terrain("gbuffers_terrain", TexturedLit),
    TerrainSolid("gbuffers_terrain_solid", Terrain),
    TerrainCutout("gbuffers_terrain_cutout", Terrain),
    // Impetus draws cutout and cutout-mipped as one pass, so this id satisfies both conventions: OptiFine packs name it gbuffers_terrain_cutout_mip, Umbra-era packs only ship gbuffers_terrain_cutout
    TerrainCutoutMip("gbuffers_terrain_cutout_mip", TerrainCutout),
    DamagedBlock("gbuffers_damagedblock", Terrain),
    Block("gbuffers_block", Terrain),
    // The translucent half of the block-entity program, matching Iris's ProgramId.BlockTrans
    BlockTrans("gbuffers_block_translucent", Block),
    BeaconBeam("gbuffers_beaconbeam", Textured),
    Item("gbuffers_item", TexturedLit),

    // --- Entity family ---
    Entities("gbuffers_entities", TexturedLit),
    EntitiesTrans("gbuffers_entities_translucent", Entities),
    EntitiesGlowing("gbuffers_entities_glowing", Entities),
    // Lightning bolts, split from the entity program so a pack can shade emissive geometry differently from a mob
    Lightning("gbuffers_lightning", Entities),
    Particles("gbuffers_particles", TexturedLit),
    ParticlesTrans("gbuffers_particles_translucent", Particles),
    ArmorGlint("gbuffers_armor_glint", Textured),
    // The "eyes" overlay layers (spider, enderman, dragon) with a DEFAULT premultiplied-additive blend standing in for vanilla's ONE, ONE, since packs commonly ship gbuffers_spidereyes without a blend directive and the eyes would render opaque black
    SpiderEyes("gbuffers_spidereyes", Textured,
            new BlendMode(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE)),
    Hand("gbuffers_hand", TexturedLit),
    Weather("gbuffers_weather", TexturedLit),

    // --- Water / translucent family ---
    Water("gbuffers_water", Terrain),
    HandWater("gbuffers_hand_water", Hand),

    // Shadow family (Umbra's ProgramGroup.Shadow, OptiFine's shadow_solid/shadow_cutout too); every one falls back to plain `shadow`, so packs declaring none behave as before, while packs that DO ship them previously had those files ignored
    Shadow("shadow"),
    ShadowSolid("shadow_solid", Shadow),
    ShadowCutout("shadow_cutout", Shadow),
    ShadowWater("shadow_water", Shadow),
    ShadowEntities("shadow_entities", Shadow),
    // Falls back to shadow_entities rather than plain shadow like Iris, so a pack overriding entity shadows gets it applied to lightning too
    ShadowLightning("shadow_lightning", ShadowEntities),
    ShadowBlock("shadow_block", Shadow),
    // --- Distant Horizons LOD family (Iris's ProgramGroup.Dh): dh_terrain shades opaque LODs, dh_water the deferred translucent ones, dh_shadow casts them into the shadow map and dh_generic draws DH's beacon beams and clouds; water and generic fall back to terrain, the others have no fallback so a pack without them compiles no LOD override
    DhTerrain("dh_terrain"),
    DhWater("dh_water", DhTerrain),
    DhShadow("dh_shadow"),
    DhGeneric("dh_generic", DhTerrain),

    // --- Single composite/deferred/final entries (numbered variants come from ProgramArrayId) ---
    Final("final");

    private final String sourceName;
    private final ProgramId fallback;
    private final BlendMode defaultBlendMode;

    ProgramId(String sourceName) {
        this(sourceName, null, null);
    }

    ProgramId(String sourceName, ProgramId fallback) {
        this(sourceName, fallback, null);
    }

    ProgramId(String sourceName, ProgramId fallback, BlendMode defaultBlendMode) {
        this.sourceName = sourceName;
        this.fallback = fallback;
        this.defaultBlendMode = defaultBlendMode;
    }

    // The base file name inside shaders/ without extension (gbuffers_terrain), which the loader looks for as .vsh, .gsh and .fsh
    public String getSourceName() {
        return this.sourceName;
    }

    // The program to use when this one is absent, or null at the chain's end; OptiFine's fallback chain (terrain -> textured_lit -> textured -> basic) is what lets a three-file pack shade the world
    public ProgramId getFallback() {
        return this.fallback;
    }

    // The blend mode this program gets when the pack declared no blend.<program>, or null; only meaningful for a DIRECTLY declared program, since a fallback lands on another program's source with its own directives (Iris's rule)
    public BlendMode getDefaultBlendMode() {
        return this.defaultBlendMode;
    }

    // File base name to id; null when not a known program
    public static ProgramId bySourceName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (ProgramId id : values()) {
            if (id.sourceName.equals(lower)) {
                return id;
            }
        }
        return null;
    }
}
