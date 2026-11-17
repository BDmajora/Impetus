package com.bdmajora.impetus.umbra.pipeline;

import com.bdmajora.impetus.umbra.shaderpack.ShaderProperties;

import java.util.Objects;

// Settings of the shadow content group.
public final class ShadowContentSettings {
    // Culling of the shadow pass.
    public enum Culling {
        // Keeps only casters that can shade what the camera sees.
        ON,
        // Every loaded section is a caster.
        OFF,
        // Keeps everything in voxel distance plus casters for the view beyond it.
        REVERSED
    }

    // Whether opaque terrain draws into the shadow map.
    private final boolean terrain;
    // Whether translucent terrain draws into the shadow map.
    private final boolean translucent;
    // Whether entities other than players draw into the shadow map.
    private final boolean entities;
    // Whether block entities draw into the shadow map.
    private final boolean blockEntities;
    // Whether light emitting block entities draw into the shadow map.
    private final boolean lightBlockEntities;
    // Whether the player draws into the shadow map.
    private final boolean player;
    // Section culling mode of the shadow pass.
    private final Culling culling;

    // Creates a shadow content settings from its terrain and translucent and entities and block entities.
    private ShadowContentSettings(boolean terrain, boolean translucent, boolean entities, boolean blockEntities,
                                  // Light emitter switch and player switch and culling mode.
                                  boolean lightBlockEntities, boolean player, Culling culling) {
        this.terrain = terrain;
        this.translucent = translucent;
        this.entities = entities;
        this.blockEntities = blockEntities;
        this.lightBlockEntities = lightBlockEntities;
        this.player = player;
        this.culling = culling;
    }

    // Returns the defaults.
    public static ShadowContentSettings defaults() {
        return new ShadowContentSettings(true, true, true, true, false, true, Culling.ON);
    }

    // Reads the shadow content directives of shaders.properties.
    public static ShadowContentSettings from(ShaderProperties properties) {
        Objects.requireNonNull(properties, "properties");
        return new ShadowContentSettings(
                properties.getShadowTerrain().orElse(Boolean.TRUE),
                properties.getShadowTranslucent().orElse(Boolean.TRUE),
                properties.getShadowEntities().orElse(Boolean.TRUE),
                properties.getShadowBlockEntities().orElse(Boolean.TRUE),
                properties.getShadowLightBlockEntities().orElse(Boolean.FALSE),
                properties.getShadowPlayer().orElse(Boolean.TRUE),
                parseCulling(properties.getShadowCulling().orElse(null)));
    }

    // Parses a culling mode name.
    private static Culling parseCulling(String value) {
        if (value == null) {
            return Culling.ON;
        }
        return switch (value) {
            case "false", "off" -> Culling.OFF;
            case "reversed" -> Culling.REVERSED;
            default -> Culling.ON;
        };
    }

    // Returns whether opaque terrain casts shadows.
    public boolean shouldRenderTerrain() {
        return this.terrain;
    }

    // Returns whether translucent terrain casts shadows.
    public boolean shouldRenderTranslucent() {
        return this.translucent;
    }

    // Returns whether entities other than players cast shadows.
    public boolean shouldRenderEntities() {
        return this.entities;
    }

    // Returns whether any block entity casts shadows.
    public boolean shouldRenderAnyBlockEntities() {
        return this.blockEntities || this.lightBlockEntities;
    }

    // Returns whether every block entity casts shadows.
    public boolean shouldRenderBlockEntities() {
        return this.blockEntities;
    }

    // Returns whether only light emitting block entities cast shadows.
    public boolean shouldRenderLightBlockEntitiesOnly() {
        return !this.blockEntities && this.lightBlockEntities;
    }

    // Returns whether the player casts a shadow.
    public boolean shouldRenderPlayer() {
        return this.player;
    }

    // Returns the culling.
    public Culling getCulling() {
        return this.culling;
    }

    // Returns the string.
    @Override
    public String toString() {
        return "terrain=" + this.terrain + ", translucent=" + this.translucent + ", entities=" + this.entities
                + ", blockEntities=" + this.blockEntities + ", lightBlockEntities=" + this.lightBlockEntities
                + ", player=" + this.player + ", culling=" + this.culling;
    }
}
