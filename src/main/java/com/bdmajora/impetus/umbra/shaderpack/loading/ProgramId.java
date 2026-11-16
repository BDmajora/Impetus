package com.bdmajora.impetus.umbra.shaderpack.loading;

import com.bdmajora.impetus.umbra.gl.blending.BlendMode;
import org.lwjgl.opengl.GL11;

import java.util.Locale;

// Choices of the program id setting.
public enum ProgramId {
    // Program of untextured geometry.
    Basic("gbuffers_basic"),
    // Program of lines.
    Line("gbuffers_line", Basic),
    // Program of textured geometry.
    Textured("gbuffers_textured", Basic),
    // Program of textured and lit geometry.
    TexturedLit("gbuffers_textured_lit", Textured),
    // Skies the basic.
    SkyBasic("gbuffers_skybasic", Basic),
    // Skies the textured.
    SkyTextured("gbuffers_skytextured", Textured),
    // Program of clouds.
    Clouds("gbuffers_clouds", Textured),

    // Program of terrain.
    Terrain("gbuffers_terrain", TexturedLit),
    // Program of solid terrain.
    TerrainSolid("gbuffers_terrain_solid", Terrain),
    // Program of cutout terrain.
    TerrainCutout("gbuffers_terrain_cutout", Terrain),
    // Program of mipped cutout terrain.
    TerrainCutoutMip("gbuffers_terrain_cutout_mip", TerrainCutout),
    // Program of block breaking overlays.
    DamagedBlock("gbuffers_damagedblock", Terrain),
    // Program of block entities.
    Block("gbuffers_block", Terrain),
    // Program of translucent block entities.
    BlockTrans("gbuffers_block_translucent", Block),
    // Program of beacon beams.
    BeaconBeam("gbuffers_beaconbeam", Textured),
    // Program of items.
    Item("gbuffers_item", TexturedLit),

    // Program of entities.
    Entities("gbuffers_entities", TexturedLit),
    // Program of translucent entities.
    EntitiesTrans("gbuffers_entities_translucent", Entities),
    // Program of glowing entities.
    EntitiesGlowing("gbuffers_entities_glowing", Entities),
    // Program of lightning.
    Lightning("gbuffers_lightning", Entities),
    // Program of particles.
    Particles("gbuffers_particles", TexturedLit),
    // Program of translucent particles.
    ParticlesTrans("gbuffers_particles_translucent", Particles),
    // Program of armor glint.
    ArmorGlint("gbuffers_armor_glint", Textured),
    // Program of glowing eyes.
    SpiderEyes("gbuffers_spidereyes", Textured,
            new BlendMode(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE)),
    // Program of the hand.
    Hand("gbuffers_hand", TexturedLit),
    // Program of rain and snow.
    Weather("gbuffers_weather", TexturedLit),

    // Program of water.
    Water("gbuffers_water", Terrain),
    // Program of translucent items in the hand.
    HandWater("gbuffers_hand_water", Hand),

    // Program of the shadow pass.
    Shadow("shadow"),
    // Shadow program of solid terrain.
    ShadowSolid("shadow_solid", Shadow),
    // Shadow program of cutout terrain.
    ShadowCutout("shadow_cutout", Shadow),
    // Shadow program of water.
    ShadowWater("shadow_water", Shadow),
    // Shadow program of entities.
    ShadowEntities("shadow_entities", Shadow),
    // Shadow program of lightning.
    ShadowLightning("shadow_lightning", ShadowEntities),
    // Shadow program of block entities.
    ShadowBlock("shadow_block", Shadow),
    // Program of Distant Horizons terrain.
    DhTerrain("dh_terrain"),
    // Program of Distant Horizons water.
    DhWater("dh_water", DhTerrain),
    // Program of Distant Horizons shadows.
    DhShadow("dh_shadow"),
    // Program of generic Distant Horizons geometry.
    DhGeneric("dh_generic", DhTerrain),

    // Program of the final pass.
    Final("final");

    // Every program id.
    public static final ProgramId[] VALUES = values();

    // File name of the program.
    private final String sourceName;
    // Program used when this one is missing.
    private final ProgramId fallback;
    // Blend mode used without a pack override.
    private final BlendMode defaultBlendMode;

    // Creates a program id from its source name.
    ProgramId(String sourceName) {
        this(sourceName, null, null);
    }

    // Creates a program id from its source name and fallback.
    ProgramId(String sourceName, ProgramId fallback) {
        this(sourceName, fallback, null);
    }

    // Creates a program id from its source name and fallback and default blend mode.
    ProgramId(String sourceName, ProgramId fallback, BlendMode defaultBlendMode) {
        this.sourceName = sourceName;
        this.fallback = fallback;
        this.defaultBlendMode = defaultBlendMode;
    }

    // Returns the source name.
    public String getSourceName() {
        return this.sourceName;
    }

    // Returns the fallback.
    public ProgramId getFallback() {
        return this.fallback;
    }

    // Returns the default blend mode.
    public BlendMode getDefaultBlendMode() {
        return this.defaultBlendMode;
    }

    // Bies the source name.
    public static ProgramId bySourceName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (ProgramId id : VALUES) {
            if (id.sourceName.equals(lower)) {
                return id;
            }
        }
        return null;
    }
}
