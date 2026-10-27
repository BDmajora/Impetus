package com.bdmajora.extras.config;

import com.bdmajora.extras.ExtrasConfig.AnimationSettings;
import com.bdmajora.extras.ExtrasConfig.DetailSettings;
import com.bdmajora.extras.ExtrasConfig.LeafSettings;
import com.bdmajora.extras.ExtrasConfig.ParticleSettings;

// Settings of texture animations and particles and world detail and leaf culling
final class VisualSchema {
    // Texture animation switches
    static final SettingGroup<AnimationSettings> ANIMATION = SettingGroup.builder("animation", c -> c.animation, AnimationSettings::new)
            .bool("animation", "Master switch for all texture animations", s -> s.all, (s, v) -> s.all = v)
            .bool("water", "Animate water textures", s -> s.water, (s, v) -> s.water = v)
            .bool("lava", "Animate lava textures", s -> s.lava, (s, v) -> s.lava = v)
            .bool("fire", "Animate fire textures", s -> s.fire, (s, v) -> s.fire = v)
            .bool("portal", "Animate nether portal textures", s -> s.portal, (s, v) -> s.portal = v)
            .bool("blockAnimations", "Animate other block textures", s -> s.blockAnimations, (s, v) -> s.blockAnimations = v)
            .bool("redstone", "Animate redstone textures", s -> s.redstone, (s, v) -> s.redstone = v)
            .bool("explosion", "Animate explosion textures", s -> s.explosion, (s, v) -> s.explosion = v)
            .bool("flame", "Animate flame textures", s -> s.flame, (s, v) -> s.flame = v)
            .bool("smoke", "Animate smoke textures", s -> s.smoke, (s, v) -> s.smoke = v)
            .bool("sculkSensor", "Animate sculk-sensor-style textures (modded only on 1.12.2)",
                    s -> s.sculkSensor, (s, v) -> s.sculkSensor = v)
            .build();

    // Particle switches and particle ticking options
    static final SettingGroup<ParticleSettings> PARTICLE = SettingGroup.builder("particle", c -> c.particle, ParticleSettings::new)
            .bool("particles", "Master switch for all particles", s -> s.all, (s, v) -> s.all = v)
            .bool("rainSplash", "Rain splash particles", s -> s.rainSplash, (s, v) -> s.rainSplash = v)
            .bool("blockBreak", "Block break particles", s -> s.blockBreak, (s, v) -> s.blockBreak = v)
            .bool("blockBreaking", "Block breaking (mining) particles", s -> s.blockBreaking, (s, v) -> s.blockBreaking = v)
            .bool("voidParticles", "Void particles near the world bottom", s -> s.voidParticles, (s, v) -> s.voidParticles = v)
            .bool("waterParticles", "Water drip, splash and suspended particles",
                    s -> s.waterParticles, (s, v) -> s.waterParticles = v)
            .bool("portalParticles", "Nether portal particles", s -> s.portalParticles, (s, v) -> s.portalParticles = v)
            .bool("potionParticles", "Potion effect particles", s -> s.potionParticles, (s, v) -> s.potionParticles = v)
            .bool("drippingWaterLava", "Dripping water and lava particles",
                    s -> s.drippingWaterLava, (s, v) -> s.drippingWaterLava = v)
            .bool("fireworkParticles", "Firework spark particles", s -> s.fireworkParticles, (s, v) -> s.fireworkParticles = v)
            .bool("parallelTick", "Tick particles across a worker pool instead of the client thread",
                    s -> s.parallelTick, (s, v) -> s.parallelTick = v)
            .bool("parallelModded", "Also tick modded particle classes on the pool; off keeps them on the client thread",
                    s -> s.parallelModded, (s, v) -> s.parallelModded = v)
            .bool("lightCache", "Sample each particle's light once per tick instead of once per frame",
                    s -> s.lightCache, (s, v) -> s.lightCache = v)
            .bool("cullOffscreen", "Skip building quads for vanilla particles outside the view frustum",
                    s -> s.cullOffscreen, (s, v) -> s.cullOffscreen = v)
            .bool("collisionCache", "Remember that a particle's block cell has nothing to collide with instead of sweeping it every tick",
                    s -> s.collisionCache, (s, v) -> s.collisionCache = v)
            .build();

    // Sky and weather and colour detail switches with the star count
    static final SettingGroup<DetailSettings> DETAIL = SettingGroup.builder("detail", c -> c.detail, DetailSettings::new)
            .bool("sky", "Render the sky box", s -> s.sky, (s, v) -> s.sky = v)
            .bool("stars", "Render stars", s -> s.stars, (s, v) -> s.stars = v)
            .bool("sun", "Render the sun", s -> s.sun, (s, v) -> s.sun = v)
            .bool("moon", "Render the moon", s -> s.moon, (s, v) -> s.moon = v)
            .bool("rainSnow", "Render falling rain and snow", s -> s.rainSnow, (s, v) -> s.rainSnow = v)
            .bool("biomeColors", "Biome-specific grass, foliage and water tint", s -> s.biomeColors, (s, v) -> s.biomeColors = v)
            .bool("skyColors", "Biome-specific sky colour", s -> s.skyColors, (s, v) -> s.skyColors = v)
            .bool("swampColors", "Swamp's darkened grass and foliage tint", s -> s.swampColors, (s, v) -> s.swampColors = v)
            .bool("voidFog", "Void fog near the world bottom", s -> s.voidFog, (s, v) -> s.voidFog = v)
            .bool("showCapes", "Render player capes", s -> s.showCapes, (s, v) -> s.showCapes = v)
            .bool("heldItemTooltips", "Show the item name popup when switching hotbar slots",
                    s -> s.heldItemTooltips, (s, v) -> s.heldItemTooltips = v)
            .integer("totalStars", DetailSettings.STARS_MIN, DetailSettings.STARS_MAX, "Number of stars to generate",
                    s -> s.totalStars, (s, v) -> s.totalStars = v)
            .build();

    // Fancy leaf face culling mode and depth
    static final SettingGroup<LeafSettings> LEAVES = SettingGroup.builder("leaves", c -> c.leaves, LeafSettings::new)
            .integer("cullingDepth", LeafSettings.DEPTH_MIN, LeafSettings.DEPTH_MAX,
                    "Depth mode: how many solid blocks must lie behind a leaf face before it is dropped",
                    s -> s.cullingDepth, (s, v) -> s.cullingDepth = v)
            .choice("cullingMode", "Fancy leaf face culling (0 = Default, 1 = Check surrounding, 2 = Depth)",
                    s -> s.cullingMode, (s, v) -> s.cullingMode = v)
            .build();

    // Prevents instantiation
    private VisualSchema() {
    }
}
