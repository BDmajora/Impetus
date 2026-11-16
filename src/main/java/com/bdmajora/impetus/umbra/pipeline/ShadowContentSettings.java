package com.bdmajora.impetus.umbra.pipeline;

import com.bdmajora.impetus.umbra.shaderpack.ShaderProperties;

import java.util.Objects;

// Settings of the shadow content group.
public final class ShadowContentSettings {
    // Choices of the culling setting.
    public enum Culling {
        // Value that selects on.
        ON,
        // Value that selects off.
        OFF,
        // Value that selects reversed.
        REVERSED
    }

    // Choice of the terrain option.
    private final boolean terrain;
    // Choice of the translucent option.
    private final boolean translucent;
    // Choice of the entities option.
    private final boolean entities;
    // Choice of the block entities option.
    private final boolean blockEntities;
    // Choice of the light block entities option.
    private final boolean lightBlockEntities;
    // Choice of the player option.
    private final boolean player;
    // Choice of the culling option.
    private final Culling culling;

    // Creates a shadow content settings from its terrain and translucent and entities and block entities.
    private ShadowContentSettings(boolean terrain, boolean translucent, boolean entities, boolean blockEntities,
                                  // Choice of the light block entities option.
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

    // Creates from.
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

    // Returns whether render terrain.
    public boolean shouldRenderTerrain() {
        return this.terrain;
    }

    // Returns whether render translucent.
    public boolean shouldRenderTranslucent() {
        return this.translucent;
    }

    // Returns whether render entities.
    public boolean shouldRenderEntities() {
        return this.entities;
    }

    // Returns whether render any block entities.
    public boolean shouldRenderAnyBlockEntities() {
        return this.blockEntities || this.lightBlockEntities;
    }

    // Returns whether render block entities.
    public boolean shouldRenderBlockEntities() {
        return this.blockEntities;
    }

    // Returns whether render light block entities only.
    public boolean shouldRenderLightBlockEntitiesOnly() {
        return !this.blockEntities && this.lightBlockEntities;
    }

    // Returns whether render player.
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
