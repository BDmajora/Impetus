package com.bdmajora.impetus.umbra.pipeline;

import com.bdmajora.impetus.umbra.shaderpack.ShaderProperties;

import java.util.Objects;

// What the shadow pass may draw from the shadowTerrain and shadowEntities and related directives with OptiFine defaults
public final class ShadowContentSettings {
    // Section culling mode of the shadow pass from the shadow.culling directive
    public enum Culling {
        // Mode that culls against the shadow frustum
        ON,
        // Mode that draws every section in range
        OFF,
        // Mode that always draws sections near the camera and culls the rest against the shadow frustum
        REVERSED
    }

    // Switch for terrain shadows
    private final boolean terrain;
    // Switch for translucent terrain shadows
    private final boolean translucent;
    // Switch for shadows of entities other than players
    private final boolean entities;
    // Switch for shadows of every block entity
    private final boolean blockEntities;
    // Switch for shadows of light emitting block entities
    private final boolean lightBlockEntities;
    // Switch for player shadows
    private final boolean player;
    // Section culling mode
    private final Culling culling;

    // Creates settings from each switch and the culling mode
    private ShadowContentSettings(boolean terrain, boolean translucent, boolean entities, boolean blockEntities,
                                  boolean lightBlockEntities, boolean player, Culling culling) {
        this.terrain = terrain;
        this.translucent = translucent;
        this.entities = entities;
        this.blockEntities = blockEntities;
        this.lightBlockEntities = lightBlockEntities;
        this.player = player;
        this.culling = culling;
    }

    // Returns settings that draw everything except the light emitter subset with culling on
    public static ShadowContentSettings defaults() {
        return new ShadowContentSettings(true, true, true, true, false, true, Culling.ON);
    }

    // Reads the shadow content directives and falls back to the defaults for each one that is missing
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

    // Returns OFF for false or off and REVERSED for reversed and ON for anything else including null
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

    // Returns whether terrain casts shadows
    public boolean shouldRenderTerrain() {
        return this.terrain;
    }

    // Returns whether translucent terrain casts shadows
    public boolean shouldRenderTranslucent() {
        return this.translucent;
    }

    // Returns whether entities other than players cast shadows
    public boolean shouldRenderEntities() {
        return this.entities;
    }

    // Returns whether any block entity casts shadows
    public boolean shouldRenderAnyBlockEntities() {
        return this.blockEntities || this.lightBlockEntities;
    }

    // Returns whether every block entity casts shadows
    public boolean shouldRenderBlockEntities() {
        return this.blockEntities;
    }

    // Returns whether only light emitting block entities cast shadows
    public boolean shouldRenderLightBlockEntitiesOnly() {
        return !this.blockEntities && this.lightBlockEntities;
    }

    // Returns whether players cast shadows
    public boolean shouldRenderPlayer() {
        return this.player;
    }

    // Returns the section culling mode
    public Culling getCulling() {
        return this.culling;
    }

    // Returns every switch and the culling mode as one line of text
    @Override
    public String toString() {
        return "terrain=" + this.terrain + ", translucent=" + this.translucent + ", entities=" + this.entities
                + ", blockEntities=" + this.blockEntities + ", lightBlockEntities=" + this.lightBlockEntities
                + ", player=" + this.player + ", culling=" + this.culling;
    }
}
