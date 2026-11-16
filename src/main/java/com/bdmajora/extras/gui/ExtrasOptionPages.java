package com.bdmajora.extras.gui;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.ExtrasConfig;
import com.bdmajora.extras.async.ParallelProcessor;
import com.bdmajora.extras.client.AdaptiveSync;
import com.bdmajora.extras.client.particle.ParticleClassRegistry;
import com.bdmajora.impetus.api.options.OptionIdentifier;
import com.bdmajora.impetus.api.options.control.ControlValueFormatter;
import com.bdmajora.impetus.api.options.control.TickBoxControl;
import com.bdmajora.impetus.api.options.structure.OptionFlag;
import com.bdmajora.impetus.api.options.structure.OptionGroup;
import com.bdmajora.impetus.api.options.structure.OptionImpact;
import com.bdmajora.impetus.api.options.structure.OptionImpl;
import com.bdmajora.impetus.api.options.structure.OptionPage;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.impl.gui.ModuleOptions;
import com.google.common.collect.ImmutableList;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

// Builder of the Extras pages of the video options screen.
public final class ExtrasOptionPages {
    // Mod id.
    private static final String MOD_ID = "extras";
    // Prefix of the option language keys.
    private static final String LANG = "impetus.options.extras.";
    // Prefix of the page language keys.
    private static final String PAGES = "impetus.options.pages.extras.";
    // Prefix of the group language keys.
    private static final String GROUPS = "impetus.options.extras.group.";

    // Option builders of the module.
    private static final ModuleOptions<ExtrasConfig> OPTIONS = new ModuleOptions<>(MOD_ID, "extras/", LANG, new ExtrasOptionsStorage());

    // Prevents instantiation
    private ExtrasOptionPages() {
    }

    // Returns the pages.
    public static List<OptionPage> pages() {
        OptionImpl<ExtrasConfig, Boolean> particlesMaster = OPTIONS.toggle("particles.all",
                (config, value) -> config.particle.all = value,
                config -> config.particle.all,
                OptionImpact.HIGH, null, null);

        List<OptionGroup> particleClasses = new ArrayList<>();
        addParticleClassGroups(particleClasses, particlesMaster::getValue);

        List<OptionPage> pages = new ArrayList<>();
        pages.add(page("animations", animations(), details(), leaves()));
        pages.add(page("particles", particles(particlesMaster)));
        pages.add(new OptionPage(OptionIdentifier.create(MOD_ID, "page.particle_types"),
                TextComponent.translatable(PAGES + "particle_types"), ImmutableList.copyOf(particleClasses)));
        pages.add(page("sky", fog(), cloudsAndWeather()));
        pages.add(page("entities", entityRendering(), bakedBlockEntities(), occlusionCulling()));
        pages.add(page("budget", renderBudget(), gpuBooster()));
        pages.add(page("system", parallelTicking(), threadScheduling(), loading(), network()));
        pages.add(page("interface", hud(), textAndModels(), overlay(), toasts()));
        pages.add(page("quality_of_life", qualityOfLife(), zoom()));
        return pages;
    }

    // Returns the page.
    private static OptionPage page(String id, OptionGroup... groups) {
        return new OptionPage(OptionIdentifier.create(MOD_ID, "page." + id), TextComponent.translatable(PAGES + id), ImmutableList.copyOf(groups));
    }

    // Returns the translated name of a group.
    private static TextComponent groupName(String id) {
        return TextComponent.translatable(GROUPS + id);
    }

    // Returns the animation group.
    private static OptionGroup animations() {
        OptionImpl<ExtrasConfig, Boolean> master = OPTIONS.toggle("animations.all",
                (config, value) -> config.animation.all = value,
                config -> config.animation.all,
                OptionImpact.MEDIUM, OptionFlag.REQUIRES_ASSET_RELOAD, null);
        BooleanSupplier enabled = master::getValue;

        return OptionGroup.createBuilder()
                .setId(OPTIONS.group("animations"))
                .setName(groupName("animations"))
                .add(master)
                .add(animationToggle("animations.water", enabled,
                        (config, value) -> config.animation.water = value, config -> config.animation.water))
                .add(animationToggle("animations.lava", enabled,
                        (config, value) -> config.animation.lava = value, config -> config.animation.lava))
                .add(animationToggle("animations.fire", enabled,
                        (config, value) -> config.animation.fire = value, config -> config.animation.fire))
                .add(animationToggle("animations.portal", enabled,
                        (config, value) -> config.animation.portal = value, config -> config.animation.portal))
                .add(animationToggle("animations.redstone", enabled,
                        (config, value) -> config.animation.redstone = value, config -> config.animation.redstone))
                .add(animationToggle("animations.explosion", enabled,
                        (config, value) -> config.animation.explosion = value, config -> config.animation.explosion))
                .add(animationToggle("animations.flame", enabled,
                        (config, value) -> config.animation.flame = value, config -> config.animation.flame))
                .add(animationToggle("animations.smoke", enabled,
                        (config, value) -> config.animation.smoke = value, config -> config.animation.smoke))
                .add(animationToggle("animations.sculk_sensor", enabled,
                        (config, value) -> config.animation.sculkSensor = value, config -> config.animation.sculkSensor))
                .add(animationToggle("animations.block", enabled,
                        (config, value) -> config.animation.blockAnimations = value,
                        config -> config.animation.blockAnimations))
                .build();
    }

    // Returns the particle group.
    private static OptionGroup particles(OptionImpl<ExtrasConfig, Boolean> master) {
        BooleanSupplier enabled = master::getValue;

        OptionImpl<ExtrasConfig, Boolean> parallel = OPTIONS.toggle("particles.parallel",
                (config, value) -> config.particle.parallelTick = value,
                config -> config.particle.parallelTick, OptionImpact.HIGH, null, enabled);
        BooleanSupplier parallelOn = () -> enabled.getAsBoolean() && parallel.getValue();

        return OptionGroup.createBuilder()
                .setId(OPTIONS.group("particles"))
                .setName(groupName("particles"))
                .add(master)
                .add(OPTIONS.toggle("particles.rain_splash", (config, value) -> config.particle.rainSplash = value,
                        config -> config.particle.rainSplash, null, null, enabled))
                .add(OPTIONS.toggle("particles.block_break", (config, value) -> config.particle.blockBreak = value,
                        config -> config.particle.blockBreak, null, null, enabled))
                .add(OPTIONS.toggle("particles.block_breaking", (config, value) -> config.particle.blockBreaking = value,
                        config -> config.particle.blockBreaking, null, null, enabled))
                .add(OPTIONS.toggle("particles.void", (config, value) -> config.particle.voidParticles = value,
                        config -> config.particle.voidParticles, null, null, enabled))
                .add(OPTIONS.toggle("particles.water", (config, value) -> config.particle.waterParticles = value,
                        config -> config.particle.waterParticles, null, null, enabled))
                .add(OPTIONS.toggle("particles.portal", (config, value) -> config.particle.portalParticles = value,
                        config -> config.particle.portalParticles, null, null, enabled))
                .add(OPTIONS.toggle("particles.potion", (config, value) -> config.particle.potionParticles = value,
                        config -> config.particle.potionParticles, null, null, enabled))
                .add(OPTIONS.toggle("particles.dripping", (config, value) -> config.particle.drippingWaterLava = value,
                        config -> config.particle.drippingWaterLava, null, null, enabled))
                .add(OPTIONS.toggle("particles.firework", (config, value) -> config.particle.fireworkParticles = value,
                        config -> config.particle.fireworkParticles, null, null, enabled))
                .add(parallel)
                .add(OPTIONS.toggle("particles.parallel_modded", (config, value) -> config.particle.parallelModded = value,
                        config -> config.particle.parallelModded, OptionImpact.VARIES, null, parallelOn))
                .add(OPTIONS.toggle("particles.light_cache", (config, value) -> config.particle.lightCache = value,
                        config -> config.particle.lightCache, OptionImpact.MEDIUM, null, enabled))
                .add(OPTIONS.toggle("particles.cull_offscreen", (config, value) -> config.particle.cullOffscreen = value,
                        config -> config.particle.cullOffscreen, OptionImpact.MEDIUM, null, enabled))
                .add(OPTIONS.toggle("particles.collision_cache", (config, value) -> config.particle.collisionCache = value,
                        config -> config.particle.collisionCache, OptionImpact.MEDIUM, null, enabled))
                .build();
    }

    // Returns the detail group.
    private static OptionGroup details() {
        OptionImpl<ExtrasConfig, Boolean> stars = OPTIONS.toggle("details.stars",
                (config, value) -> config.detail.stars = value,
                config -> config.detail.stars,
                null, OptionFlag.REQUIRES_RENDERER_RELOAD, null);
        BooleanSupplier starsOn = stars::getValue;

        return OptionGroup.createBuilder()
                .setId(OPTIONS.group("details"))
                .setName(groupName("details"))
                .add(OPTIONS.toggle("details.sky", (config, value) -> config.detail.sky = value,
                        config -> config.detail.sky, null, OptionFlag.REQUIRES_RENDERER_RELOAD, null))
                .add(stars)
                .add(OPTIONS.slider("details.total_stars",
                        ExtrasConfig.DetailSettings.STARS_MIN, ExtrasConfig.DetailSettings.STARS_MAX, 500,
                        ControlValueFormatter.number(),
                        (config, value) -> config.detail.totalStars = value,
                        config -> config.detail.totalStars,
                        OptionImpact.MEDIUM, OptionFlag.REQUIRES_RENDERER_RELOAD, starsOn))
                .add(OPTIONS.toggle("details.sun", (config, value) -> config.detail.sun = value,
                        config -> config.detail.sun, null, null, null))
                .add(OPTIONS.toggle("details.moon", (config, value) -> config.detail.moon = value,
                        config -> config.detail.moon, null, null, null))
                .add(OPTIONS.toggle("details.rain_snow", (config, value) -> config.detail.rainSnow = value,
                        config -> config.detail.rainSnow, OptionImpact.LOW, null, null))
                .add(OPTIONS.toggle("details.biome_colors", (config, value) -> config.detail.biomeColors = value,
                        config -> config.detail.biomeColors, OptionImpact.MEDIUM,
                        OptionFlag.REQUIRES_RENDERER_RELOAD, null))
                .add(OPTIONS.toggle("details.sky_colors", (config, value) -> config.detail.skyColors = value,
                        config -> config.detail.skyColors, null, null, null))
                .add(OPTIONS.toggle("details.swamp_colors", (config, value) -> config.detail.swampColors = value,
                        config -> config.detail.swampColors, null, OptionFlag.REQUIRES_RENDERER_RELOAD, null))
                .add(OPTIONS.toggle("details.void_fog", (config, value) -> config.detail.voidFog = value,
                        config -> config.detail.voidFog, null, null, null))
                .add(OPTIONS.toggle("details.capes", (config, value) -> config.detail.showCapes = value,
                        config -> config.detail.showCapes, null, null, null))
                .add(OPTIONS.toggle("details.held_item_tooltips", (config, value) -> config.detail.heldItemTooltips = value,
                        config -> config.detail.heldItemTooltips, null, null, null))
                .build();
    }

    // Returns the fog group.
    private static OptionGroup fog() {
        OptionImpl<ExtrasConfig, Boolean> master = OPTIONS.toggle("render.fog",
                (config, value) -> config.render.fog = value,
                config -> config.render.fog, OptionImpact.LOW, null, null);
        BooleanSupplier enabled = master::getValue;

        return OptionGroup.createBuilder()
                .setId(OPTIONS.group("fog"))
                .setName(groupName("fog"))
                .add(master)
                .add(OPTIONS.slider("render.fog_start", 0, 200, 10, ControlValueFormatter.percentage(),
                        (config, value) -> config.render.fogStart = value,
                        config -> config.render.fogStart, null, null, enabled))
                .add(OPTIONS.slider("render.fog_distance", 0, 32, 1,
                        ControlValueFormatter.translateQuantityOrDisabled(LANG + "render.fog_distance.value", LANG + "render.fog_distance.default"),
                        (config, value) -> config.render.fogDistance = value,
                        config -> config.render.fogDistance, OptionImpact.MEDIUM, null, enabled))
                .add(OPTIONS.cycling("render.fog_shape", ExtrasConfig.FogShape.class, ExtrasConfig.FogShape.values(),
                        (config, value) -> config.render.fogShape = value,
                        config -> config.render.fogShape, enabled))
                .build();
    }

    // Returns the cloud and weather group.
    private static OptionGroup cloudsAndWeather() {
        return OptionGroup.createBuilder()
                .setId(OPTIONS.group("clouds"))
                .setName(groupName("clouds"))
                .add(OPTIONS.slider("render.cloud_scale",
                        ExtrasConfig.RenderSettings.CLOUD_SCALE_MIN,
                        ExtrasConfig.RenderSettings.CLOUD_SCALE_MAX, 1,
                        value -> TextComponent.literal(String.format(Locale.ROOT, "%.2fx",
                                (float) value / ExtrasConfig.RenderSettings.CLOUD_SCALE_VANILLA)),
                        (config, value) -> config.render.cloudScale = value,
                        config -> config.render.cloudScale, null, null, null))
                .add(OPTIONS.cycling("render.cloud_translucency", ExtrasConfig.CloudTranslucency.class,
                        ExtrasConfig.CloudTranslucency.values(),
                        (config, value) -> config.render.cloudTranslucency = value,
                        config -> config.render.cloudTranslucency, null))
                .add(OPTIONS.cycling("time", ExtrasConfig.TimeOverride.class, ExtrasConfig.TimeOverride.values(),
                        (config, value) -> config.extra.timeOverride = value,
                        config -> config.extra.timeOverride, null))
                .add(OPTIONS.cycling("weather", ExtrasConfig.WeatherOverride.class, ExtrasConfig.WeatherOverride.values(),
                        (config, value) -> config.extra.weatherOverride = value,
                        config -> config.extra.weatherOverride, null))
                .build();
    }

    // Entities the rendering.
    private static OptionGroup entityRendering() {
        OptionImpl<ExtrasConfig, Boolean> itemFrames = OPTIONS.toggle("render.item_frames",
                (config, value) -> config.render.itemFrames = value,
                config -> config.render.itemFrames, OptionImpact.MEDIUM, null, null);
        BooleanSupplier itemFramesOn = itemFrames::getValue;

        OptionImpl<ExtrasConfig, Boolean> beacons = OPTIONS.toggle("render.beacons",
                (config, value) -> config.render.beacons = value,
                config -> config.render.beacons, OptionImpact.LOW, null, null);
        BooleanSupplier beaconsOn = beacons::getValue;

        return OptionGroup.createBuilder()
                .setId(OPTIONS.group("entities"))
                .setName(groupName("entities"))
                .add(itemFrames)
                .add(OPTIONS.slider("render.item_frame_lod", 0, 256, 8,
                        ControlValueFormatter.translateQuantityOrDisabled(LANG + "render.item_frame_lod.value", LANG + "render.item_frame_lod.off"),
                        (config, value) -> config.render.itemFrameLodDistance = value,
                        config -> config.render.itemFrameLodDistance, OptionImpact.LOW, null, itemFramesOn))
                .add(OPTIONS.toggle("render.item_frame_name_tag", (config, value) -> config.render.itemFrameNameTag = value,
                        config -> config.render.itemFrameNameTag, null, null, itemFramesOn))
                .add(OPTIONS.toggle("render.armor_stands", (config, value) -> config.render.armorStands = value,
                        config -> config.render.armorStands, OptionImpact.LOW, null, null))
                .add(OPTIONS.toggle("render.paintings", (config, value) -> config.render.paintings = value,
                        config -> config.render.paintings, null, null, null))
                .add(OPTIONS.toggle("render.player_name_tag", (config, value) -> config.render.playerNameTag = value,
                        config -> config.render.playerNameTag, null, null, null))
                .add(OPTIONS.toggle("render.dropped_items", (config, value) -> config.render.droppedItemsFancy = value,
                        config -> config.render.droppedItemsFancy, OptionImpact.MEDIUM, null, null))
                .add(OPTIONS.toggle("render.item_model_flicker_fix", (config, value) -> config.render.itemModelFlickerFix = value,
                        config -> config.render.itemModelFlickerFix, null, null, null))
                .add(beacons)
                .add(OPTIONS.toggle("render.limit_beacon_beam", (config, value) -> config.render.limitBeaconBeamHeight = value,
                        config -> config.render.limitBeaconBeamHeight, null, null, beaconsOn))
                .add(OPTIONS.toggle("render.enchanting_books", (config, value) -> config.render.enchantingTableBooks = value,
                        config -> config.render.enchantingTableBooks, null, null, null))
                .add(OPTIONS.toggle("render.pistons", (config, value) -> config.render.pistons = value,
                        config -> config.render.pistons, OptionImpact.LOW, null, null))
                .build();
    }

    // Returns the render budget group.
    private static OptionGroup renderBudget() {
        OptionImpl<ExtrasConfig, Boolean> master = OPTIONS.toggle("budget.enabled",
                (config, value) -> config.renderBudget.enabled = value,
                config -> config.renderBudget.enabled, OptionImpact.VARIES, null, null);
        BooleanSupplier enabled = master::getValue;

        OptionImpl<ExtrasConfig, Boolean> entities = OPTIONS.toggle("budget.entities",
                (config, value) -> config.renderBudget.smartEntityCulling = value,
                config -> config.renderBudget.smartEntityCulling, OptionImpact.MEDIUM, null, enabled);
        BooleanSupplier entitiesOn = () -> enabled.getAsBoolean() && entities.getValue();

        OptionImpl<ExtrasConfig, Boolean> blockEntities = OPTIONS.toggle("budget.block_entities",
                (config, value) -> config.renderBudget.blockEntities = value,
                config -> config.renderBudget.blockEntities, OptionImpact.MEDIUM, null, enabled);
        BooleanSupplier blockEntitiesOn = () -> enabled.getAsBoolean() && blockEntities.getValue();

        return OptionGroup.createBuilder()
                .setId(OPTIONS.group("render_budget"))
                .setName(groupName("render_budget"))
                .add(master)
                .add(OPTIONS.cycling("budget.profile", ExtrasConfig.BudgetProfile.class, ExtrasConfig.BudgetProfile.values(),
                        (config, value) -> config.renderBudget.profile = value,
                        config -> config.renderBudget.profile, enabled))
                .add(OPTIONS.toggle("budget.adaptive", (config, value) -> config.renderBudget.adaptive = value,
                        config -> config.renderBudget.adaptive, null, null, enabled))
                .add(OPTIONS.slider("budget.particles",
                        ExtrasConfig.RenderBudgetSettings.PARTICLE_BUDGET_MIN,
                        ExtrasConfig.RenderBudgetSettings.PARTICLE_BUDGET_MAX, 5,
                        value -> value >= ExtrasConfig.RenderBudgetSettings.PARTICLE_BUDGET_MAX
                                ? TextComponent.translatable("options.off")
                                : TextComponent.literal(value + "%"),
                        (config, value) -> config.renderBudget.particleBudget = value,
                        config -> config.renderBudget.particleBudget, OptionImpact.MEDIUM, null, enabled))
                .add(entities)
                .add(OPTIONS.slider("budget.entity_distance",
                        ExtrasConfig.RenderBudgetSettings.ENTITY_DISTANCE_MIN,
                        ExtrasConfig.RenderBudgetSettings.ENTITY_DISTANCE_MAX, 8,
                        value -> TextComponent.literal(value + " blocks"),
                        (config, value) -> config.renderBudget.entityCullDistance = value,
                        config -> config.renderBudget.entityCullDistance, null, null, entitiesOn))
                .add(blockEntities)
                .add(OPTIONS.slider("budget.block_entity_distance",
                        ExtrasConfig.RenderBudgetSettings.BLOCK_ENTITY_DISTANCE_MIN,
                        ExtrasConfig.RenderBudgetSettings.BLOCK_ENTITY_DISTANCE_MAX, 8,
                        value -> TextComponent.literal(value + " blocks"),
                        (config, value) -> config.renderBudget.blockEntityDistance = value,
                        config -> config.renderBudget.blockEntityDistance, null, null, blockEntitiesOn))
                .add(OPTIONS.toggle("budget.item_frames", (config, value) -> config.renderBudget.itemFrames = value,
                        config -> config.renderBudget.itemFrames, OptionImpact.LOW, null, blockEntitiesOn))
                .add(OPTIONS.toggle("budget.overlay", (config, value) -> config.renderBudget.overlay = value,
                        config -> config.renderBudget.overlay, null, null, null))
                .build();
    }

    // Returns the GPU booster group.
    private static OptionGroup gpuBooster() {
        OptionImpl<ExtrasConfig, Boolean> master = OPTIONS.toggle("booster.enabled",
                (config, value) -> config.gpuBooster.enabled = value,
                config -> config.gpuBooster.enabled, OptionImpact.VARIES, null, null);
        BooleanSupplier enabled = master::getValue;

        return OptionGroup.createBuilder()
                .setId(OPTIONS.group("gpu_booster"))
                .setName(groupName("gpu_booster"))
                .add(master)
                .add(OPTIONS.toggle("booster.fast_random", (config, value) -> config.gpuBooster.fastRandom = value,
                        config -> config.gpuBooster.fastRandom, OptionImpact.MEDIUM, null, enabled))
                .add(OPTIONS.toggle("booster.fast_math", (config, value) -> config.gpuBooster.fastMath = value,
                        config -> config.gpuBooster.fastMath, OptionImpact.LOW, null, enabled))
                .add(OPTIONS.toggle("booster.stream_uploads", (config, value) -> config.gpuBooster.streamUploads = value,
                        config -> config.gpuBooster.streamUploads, OptionImpact.VARIES, null, enabled))
                .add(OPTIONS.toggle("booster.vertex_format_cache", (config, value) -> config.gpuBooster.vertexFormatCache = value,
                        config -> config.gpuBooster.vertexFormatCache, OptionImpact.MEDIUM, null, enabled))
                .build();
    }

    // Returns the parallel ticking group.
    private static OptionGroup parallelTicking() {
        OptionImpl<ExtrasConfig, Boolean> master = OPTIONS.toggle("parallel.enabled",
                (config, value) -> config.async.enabled = value,
                config -> config.async.enabled, OptionImpact.HIGH, OptionFlag.REQUIRES_GAME_RESTART, null);
        BooleanSupplier enabled = master::getValue;

        OptionImpl<ExtrasConfig, Boolean> entities = OPTIONS.toggle("parallel.entities",
                (config, value) -> config.async.entities = value,
                config -> config.async.entities, OptionImpact.HIGH, null, enabled);
        BooleanSupplier entitiesOn = () -> enabled.getAsBoolean() && entities.getValue();

        return OptionGroup.createBuilder()
                .setId(OPTIONS.group("parallel_ticking"))
                .setName(groupName("parallel_ticking"))
                .add(master)
                .add(OPTIONS.slider("parallel.threads", 0, Math.min(ExtrasConfig.AsyncSettings.THREADS_MAX, ParallelProcessor.maxThreads()), 1,
                        value -> value == 0
                                ? TextComponent.literal("Auto (" + ParallelProcessor.desiredThreads() + ")")
                                : TextComponent.literal(value + (value == 1 ? " thread" : " threads")),
                        (config, value) -> config.async.threads = value,
                        config -> config.async.threads, OptionImpact.MEDIUM, null, enabled))
                .add(entities)
                .add(OPTIONS.toggle("parallel.modded_entities", (config, value) -> config.async.moddedEntities = value,
                        config -> config.async.moddedEntities, OptionImpact.VARIES, null, entitiesOn))
                .add(OPTIONS.toggle("parallel.random_ticks", (config, value) -> config.async.randomTicks = value,
                        config -> config.async.randomTicks, OptionImpact.MEDIUM, null, enabled))
                .add(OPTIONS.toggle("parallel.spawning", (config, value) -> config.async.spawning = value,
                        config -> config.async.spawning, OptionImpact.LOW, null, enabled))
                .build();
    }

    // Returns the baked block entity group.
    private static OptionGroup bakedBlockEntities() {
        OptionImpl<ExtrasConfig, Boolean> master = OPTIONS.toggle("baked.enabled",
                (config, value) -> config.bakedEntities.enabled = value,
                config -> config.bakedEntities.enabled, OptionImpact.HIGH, OptionFlag.REQUIRES_RENDERER_RELOAD, null);
        BooleanSupplier enabled = master::getValue;

        return OptionGroup.createBuilder()
                .setId(OPTIONS.group("baked_block_entities"))
                .setName(groupName("baked_block_entities"))
                .add(master)
                .add(OPTIONS.toggle("baked.chests", (config, value) -> config.bakedEntities.chests = value,
                        config -> config.bakedEntities.chests, OptionImpact.HIGH, OptionFlag.REQUIRES_RENDERER_RELOAD, enabled))
                .add(OPTIONS.toggle("baked.ender_chests", (config, value) -> config.bakedEntities.enderChests = value,
                        config -> config.bakedEntities.enderChests, OptionImpact.LOW, OptionFlag.REQUIRES_RENDERER_RELOAD, enabled))
                .add(OPTIONS.toggle("baked.signs", (config, value) -> config.bakedEntities.signs = value,
                        config -> config.bakedEntities.signs, OptionImpact.MEDIUM, OptionFlag.REQUIRES_RENDERER_RELOAD, enabled))
                .add(OPTIONS.toggle("baked.beds", (config, value) -> config.bakedEntities.beds = value,
                        config -> config.bakedEntities.beds, OptionImpact.LOW, OptionFlag.REQUIRES_RENDERER_RELOAD, enabled))
                .add(OPTIONS.toggle("baked.shulker_boxes", (config, value) -> config.bakedEntities.shulkerBoxes = value,
                        config -> config.bakedEntities.shulkerBoxes, OptionImpact.MEDIUM, OptionFlag.REQUIRES_RENDERER_RELOAD, enabled))
                .build();
    }

    // Returns the network group.
    private static OptionGroup network() {
        return OptionGroup.createBuilder()
                .setId(OPTIONS.group("network"))
                .setName(groupName("network"))
                .add(OPTIONS.toggle("network.large_packets", (config, value) -> config.network.largePackets = value,
                        config -> config.network.largePackets, null, null, null))
                .add(OPTIONS.toggle("network.flush_consolidation", (config, value) -> config.network.flushConsolidation = value,
                        config -> config.network.flushConsolidation, OptionImpact.MEDIUM, null, null))
                .add(OPTIONS.toggle("network.fast_varints", (config, value) -> config.network.fastVarInts = value,
                        config -> config.network.fastVarInts, OptionImpact.LOW, null, null))
                .add(OPTIONS.toggle("network.pooled_compression", (config, value) -> config.network.pooledCompression = value,
                        config -> config.network.pooledCompression, OptionImpact.LOW, null, null))
                .add(OPTIONS.slider("network.read_timeout",
                        ExtrasConfig.NetworkSettings.TIMEOUT_MIN, ExtrasConfig.NetworkSettings.TIMEOUT_MAX, 5,
                        value -> TextComponent.literal(value + "s"),
                        (config, value) -> config.network.readTimeoutSeconds = value,
                        config -> config.network.readTimeoutSeconds, null, null, null))
                .add(OPTIONS.slider("network.login_timeout",
                        ExtrasConfig.NetworkSettings.TIMEOUT_MIN, ExtrasConfig.NetworkSettings.TIMEOUT_MAX, 5,
                        value -> TextComponent.literal(value + "s"),
                        (config, value) -> config.network.loginTimeoutSeconds = value,
                        config -> config.network.loginTimeoutSeconds, null, null, null))
                .add(OPTIONS.slider("network.keep_alive_timeout",
                        ExtrasConfig.NetworkSettings.TIMEOUT_MIN, ExtrasConfig.NetworkSettings.TIMEOUT_MAX, 5,
                        value -> TextComponent.literal(value + "s"),
                        (config, value) -> config.network.keepAliveTimeoutSeconds = value,
                        config -> config.network.keepAliveTimeoutSeconds, null, null, null))
                .build();
    }

    // Returns the thread scheduling group.
    private static OptionGroup threadScheduling() {
        return OptionGroup.createBuilder()
                .setId(OPTIONS.group("thread_scheduling"))
                .setName(groupName("thread_scheduling"))
                .add(OPTIONS.slider("threads.render_priority",
                        ExtrasConfig.ThreadSettings.PRIORITY_MIN, ExtrasConfig.ThreadSettings.PRIORITY_MAX, 1,
                        ExtrasOptionPages::priorityLabel,
                        (config, value) -> config.threads.renderThreadPriority = value,
                        config -> config.threads.renderThreadPriority, OptionImpact.VARIES, null, null))
                .add(OPTIONS.slider("threads.server_priority",
                        ExtrasConfig.ThreadSettings.PRIORITY_MIN, ExtrasConfig.ThreadSettings.PRIORITY_MAX, 1,
                        ExtrasOptionPages::priorityLabel,
                        (config, value) -> config.threads.serverThreadPriority = value,
                        config -> config.threads.serverThreadPriority, OptionImpact.VARIES, null, null))
                .add(OPTIONS.slider("threads.chunk_builder_priority",
                        ExtrasConfig.ThreadSettings.PRIORITY_MIN, ExtrasConfig.ThreadSettings.PRIORITY_MAX, 1,
                        ExtrasOptionPages::priorityLabel,
                        (config, value) -> config.threads.chunkBuilderPriority = value,
                        config -> config.threads.chunkBuilderPriority, OptionImpact.VARIES, OptionFlag.REQUIRES_RENDERER_RELOAD, null))
                .add(OPTIONS.toggle("threads.remove_yield", (config, value) -> config.threads.removeRenderYield = value,
                        config -> config.threads.removeRenderYield, OptionImpact.VARIES, null, null))
                .build();
    }

    // Returns the occlusion culling group.
    private static OptionGroup occlusionCulling() {
        OptionImpl<ExtrasConfig, Boolean> master = OPTIONS.toggle("occlusion.enabled",
                (config, value) -> config.occlusion.enabled = value,
                config -> config.occlusion.enabled, OptionImpact.HIGH, null, null);
        BooleanSupplier enabled = master::getValue;
        return OptionGroup.createBuilder()
                .setId(OPTIONS.group("occlusion_culling"))
                .setName(groupName("occlusion_culling"))
                .add(master)
                .add(OPTIONS.toggle("occlusion.entities", (config, value) -> config.occlusion.entities = value,
                        config -> config.occlusion.entities, OptionImpact.HIGH, null, enabled))
                .add(OPTIONS.toggle("occlusion.block_entities", (config, value) -> config.occlusion.blockEntities = value,
                        config -> config.occlusion.blockEntities, OptionImpact.MEDIUM, null, enabled))
                .add(OPTIONS.slider("occlusion.max_entity_size", 1, 32, 1,
                        value -> TextComponent.literal(value + " blocks"),
                        (config, value) -> config.occlusion.maxEntitySize = value,
                        config -> config.occlusion.maxEntitySize, null, null, enabled))
                .add(OPTIONS.slider("occlusion.max_block_entity_size", 1, 32, 1,
                        value -> TextComponent.literal(value + " blocks"),
                        (config, value) -> config.occlusion.maxBlockEntitySize = value,
                        config -> config.occlusion.maxBlockEntitySize, null, null, enabled))
                .add(OPTIONS.slider("occlusion.raycast_slack", 0, 300, 25,
                        value -> TextComponent.literal(String.format("%.2f blocks", value / 100.0D)),
                        (config, value) -> config.occlusion.raycastSlack = value,
                        config -> config.occlusion.raycastSlack, null, null, enabled))
                .build();
    }

    // Returns the HUD group.
    private static OptionGroup hud() {
        OptionImpl<ExtrasConfig, Boolean> master = OPTIONS.toggle("hud.cache",
                (config, value) -> config.hud.cacheEnabled = value,
                config -> config.hud.cacheEnabled, OptionImpact.MEDIUM, null, null);
        return OptionGroup.createBuilder()
                .setId(OPTIONS.group("hud"))
                .setName(groupName("hud"))
                .add(master)
                .add(OPTIONS.slider("hud.cache_fps", ExtrasConfig.HudSettings.CACHE_FPS_MIN, ExtrasConfig.HudSettings.CACHE_FPS_MAX, 5,
                        value -> TextComponent.literal(value + " fps"),
                        (config, value) -> config.hud.cacheFps = value,
                        config -> config.hud.cacheFps, null, null, master::getValue))
                .build();
    }

    // Returns the text and model group.
    private static OptionGroup textAndModels() {
        return OptionGroup.createBuilder()
                .setId(OPTIONS.group("text_and_models"))
                .setName(groupName("text_and_models"))
                .add(OPTIONS.toggle("text.batch_glyphs", (config, value) -> config.text.batchGlyphs = value,
                        config -> config.text.batchGlyphs, OptionImpact.MEDIUM, null, null))
                .add(OPTIONS.toggle("client_tick.lightmap_caching", (config, value) -> config.clientTick.lightmapCaching = value,
                        config -> config.clientTick.lightmapCaching, OptionImpact.LOW, null, null))
                .add(OPTIONS.toggle("entity_models.matrix_transforms", (config, value) -> config.entityModels.matrixTransforms = value,
                        config -> config.entityModels.matrixTransforms, OptionImpact.MEDIUM, null, null))
                .build();
    }

    // Returns the leaves group.
    private static OptionGroup leaves() {
        OptionImpl<ExtrasConfig, ExtrasConfig.LeafCulling> mode = OPTIONS.cycling("leaves.culling_mode",
                ExtrasConfig.LeafCulling.class, ExtrasConfig.LeafCulling.values(),
                (config, value) -> config.leaves.cullingMode = value, config -> config.leaves.cullingMode,
                OptionImpact.LOW, OptionFlag.REQUIRES_RENDERER_RELOAD, null);
        return OptionGroup.createBuilder()
                .setId(OPTIONS.group("leaves"))
                .setName(groupName("leaves"))
                .add(mode)
                .add(OPTIONS.slider("leaves.culling_depth", ExtrasConfig.LeafSettings.DEPTH_MIN, ExtrasConfig.LeafSettings.DEPTH_MAX, 1,
                        value -> TextComponent.literal(value + " blocks"),
                        (config, value) -> config.leaves.cullingDepth = value,
                        config -> config.leaves.cullingDepth, null, OptionFlag.REQUIRES_RENDERER_RELOAD,
                        () -> mode.getValue() == ExtrasConfig.LeafCulling.DEPTH))
                .build();
    }

    // Returns the loading group.
    private static OptionGroup loading() {
        return OptionGroup.createBuilder()
                .setId(OPTIONS.group("loading"))
                .setName(groupName("loading"))
                .add(OPTIONS.toggle("loading.skip_world_load_gc", (config, value) -> config.loading.skipWorldLoadGc = value,
                        config -> config.loading.skipWorldLoadGc, OptionImpact.MEDIUM, null, null))
                .add(OPTIONS.toggle("loading.smooth_dimension_change", (config, value) -> config.loading.smoothDimensionChange = value,
                        config -> config.loading.smoothDimensionChange, null, null, null))
                .add(OPTIONS.toggle("loading.release_screenshot_buffers", (config, value) -> config.loading.releaseScreenshotBuffers = value,
                        config -> config.loading.releaseScreenshotBuffers, null, null, null))
                .add(OPTIONS.toggle("loading.async_screenshots", (config, value) -> config.loading.asyncScreenshots = value,
                        config -> config.loading.asyncScreenshots, null, null, null))
                .add(OPTIONS.toggle("loading.driver_atlas_limit", (config, value) -> config.loading.driverAtlasLimit = value,
                        config -> config.loading.driverAtlasLimit, null, OptionFlag.REQUIRES_GAME_RESTART, null))
                .build();
    }

    // Priorities the label.
    private static TextComponent priorityLabel(int value) {
        if (value == Thread.MIN_PRIORITY) {
            return TextComponent.literal(value + " (lowest)");
        }
        if (value == Thread.NORM_PRIORITY) {
            return TextComponent.literal(value + " (normal)");
        }
        if (value == Thread.MAX_PRIORITY) {
            return TextComponent.literal(value + " (highest)");
        }
        return TextComponent.literal(Integer.toString(value));
    }

    // Returns the overlay group.
    private static OptionGroup overlay() {
        OptionImpl<ExtrasConfig, Boolean> showFps = OPTIONS.toggle("overlay.fps",
                (config, value) -> config.extra.showFps = value,
                config -> config.extra.showFps, null, null, null);
        BooleanSupplier fpsOn = showFps::getValue;

        OptionImpl<ExtrasConfig, Boolean> showCoords = OPTIONS.toggle("overlay.coords",
                (config, value) -> config.extra.showCoords = value,
                config -> config.extra.showCoords, null, null, null);
        BooleanSupplier coordsOn = showCoords::getValue;

        return OptionGroup.createBuilder()
                .setId(OPTIONS.group("overlay"))
                .setName(groupName("overlay"))
                .add(showFps)
                .add(OPTIONS.toggle("overlay.fps_extended", (config, value) -> config.extra.showFpsExtended = value,
                        config -> config.extra.showFpsExtended, null, null, fpsOn))
                .add(showCoords)
                .add(OPTIONS.toggle("overlay.ignore_reduced_debug_info",
                        (config, value) -> config.extra.ignoreReducedDebugInfo = value,
                        config -> config.extra.ignoreReducedDebugInfo, null, null, coordsOn))
                .add(OPTIONS.cycling("overlay.corner", ExtrasConfig.OverlayCorner.class,
                        ExtrasConfig.OverlayCorner.values(),
                        (config, value) -> config.extra.overlayCorner = value,
                        config -> config.extra.overlayCorner, null))
                .add(OPTIONS.cycling("overlay.text_contrast", ExtrasConfig.TextContrast.class,
                        ExtrasConfig.TextContrast.values(),
                        (config, value) -> config.extra.textContrast = value,
                        config -> config.extra.textContrast, null))
                .build();
    }

    // Returns the toast group.
    private static OptionGroup toasts() {
        OptionImpl<ExtrasConfig, Boolean> master = OPTIONS.toggle("toasts.all",
                (config, value) -> config.extra.toasts = value,
                config -> config.extra.toasts, null, null, null);
        BooleanSupplier enabled = master::getValue;

        return OptionGroup.createBuilder()
                .setId(OPTIONS.group("toasts"))
                .setName(groupName("toasts"))
                .add(master)
                .add(OPTIONS.toggle("toasts.advancement", (config, value) -> config.extra.toastAdvancement = value,
                        config -> config.extra.toastAdvancement, null, null, enabled))
                .add(OPTIONS.toggle("toasts.recipe", (config, value) -> config.extra.toastRecipe = value,
                        config -> config.extra.toastRecipe, null, null, enabled))
                .add(OPTIONS.toggle("toasts.tutorial", (config, value) -> config.extra.toastTutorial = value,
                        config -> config.extra.toastTutorial, null, null, enabled))
                .add(OPTIONS.toggle("toasts.system", (config, value) -> config.extra.toastSystem = value,
                        config -> config.extra.toastSystem, null, null, enabled))
                .build();
    }

    // Qualities the of life.
    private static OptionGroup qualityOfLife() {
        OptionImpl<ExtrasConfig, Boolean> steadyHud = OPTIONS.toggle("steady_debug_hud",
                (config, value) -> config.extra.steadyDebugHud = value,
                config -> config.extra.steadyDebugHud, OptionImpact.LOW, null, null);
        BooleanSupplier steadyHudOn = steadyHud::getValue;

        OptionImpl<ExtrasConfig, Boolean> panini = OPTIONS.toggle("panini",
                (config, value) -> config.extra.paniniProjection = value,
                config -> config.extra.paniniProjection, OptionImpact.MEDIUM, null, null);
        BooleanSupplier paniniOn = panini::getValue;

        OptionGroup.Builder builder = OptionGroup.createBuilder()
                .setId(OPTIONS.group("misc"))
                .setName(groupName("misc"))
                .add(steadyHud)
                .add(OPTIONS.slider("steady_debug_hud_refresh",
                        ExtrasConfig.ExtraSettings.STEADY_HUD_REFRESH_MIN,
                        ExtrasConfig.ExtraSettings.STEADY_HUD_REFRESH_MAX, 1,
                        value -> TextComponent.literal(value + (value == 1 ? " tick" : " ticks")),
                        (config, value) -> config.extra.steadyDebugHudRefreshInterval = value,
                        config -> config.extra.steadyDebugHudRefreshInterval, null, null, steadyHudOn))
                .add(panini)
                .add(OPTIONS.slider("panini_strength", 0, 100, 5, ControlValueFormatter.percentage(),
                        (config, value) -> config.extra.paniniProjectionStrength = value,
                        config -> config.extra.paniniProjectionStrength, null, null, paniniOn))
                .add(advancedItemTooltips())
                .add(OPTIONS.toggle("mod_name_tooltip", (config, value) -> config.extra.modNameTooltip = value,
                        config -> config.extra.modNameTooltip, null, null, null))
                .add(OPTIONS.toggle("light_updates", (config, value) -> config.render.lightUpdates = value,
                        config -> config.render.lightUpdates, OptionImpact.HIGH, null, null))
                .add(OPTIONS.toggle("prevent_shaders", (config, value) -> config.render.preventShaders = value,
                        config -> config.render.preventShaders, null, null, null))
                .add(OPTIONS.toggle("profile_entity_rendering",
                        (config, value) -> config.render.profileEntityRendering = value,
                        config -> config.render.profileEntityRendering, null, null, null))
                .add(OPTIONS.slider("autosave_interval",
                        ExtrasConfig.ExtraSettings.AUTOSAVE_MIN_TICKS,
                        ExtrasConfig.ExtraSettings.AUTOSAVE_MAX_TICKS, 300,
                        value -> value == 0
                                ? TextComponent.translatable("options.off")
                                : TextComponent.literal(value / 20 + "s"),
                        (config, value) -> config.extra.autosaveInterval = value,
                        config -> config.extra.autosaveInterval, null, null, null));

        return builder.build();
    }

    // Returns the zoom group.
    private static OptionGroup zoom() {
        OptionImpl<ExtrasConfig, Boolean> master = OPTIONS.toggle("zoom.enabled",
                (config, value) -> config.zoom.enabled = value,
                config -> config.zoom.enabled, null, null, null);
        BooleanSupplier enabled = master::getValue;

        return OptionGroup.createBuilder()
                .setId(OPTIONS.group("zoom"))
                .setName(groupName("zoom"))
                .add(master)
                .add(OPTIONS.slider("zoom.level", ExtrasConfig.ZoomSettings.LEVEL_MIN, ExtrasConfig.ZoomSettings.LEVEL_MAX, 1,
                        value -> TextComponent.literal(String.format(Locale.ROOT, "%.2fx",
                                (float) ExtrasConfig.ZoomSettings.LEVEL_SCALE / value)),
                        (config, value) -> config.zoom.level = value,
                        config -> config.zoom.level, null, null, enabled))
                .add(OPTIONS.toggle("zoom.smooth_camera", (config, value) -> config.zoom.smoothCamera = value,
                        config -> config.zoom.smoothCamera, null, null, enabled))
                .add(OPTIONS.toggle("zoom.hide_hand", (config, value) -> config.zoom.hideHand = value,
                        config -> config.zoom.hideHand, null, null, enabled))
                .build();
    }

    // Returns the vertical sync option.
    public static OptionImpl<ExtrasConfig, ExtrasConfig.VerticalSync> verticalSync() {
        ExtrasConfig.VerticalSync[] available = AdaptiveSync.isSupported()
                ? ExtrasConfig.VerticalSync.values()
                : new ExtrasConfig.VerticalSync[] {
                        ExtrasConfig.VerticalSync.OFF, ExtrasConfig.VerticalSync.ON };

        return OPTIONS.cycling("vertical_sync", ExtrasConfig.VerticalSync.class, available,
                (config, value) -> AdaptiveSync.apply(value), config -> AdaptiveSync.current(),
                OptionImpact.VARIES, null, null);
    }

    // Returns the advanced item tooltip option.
    private static OptionImpl<ExtrasConfig, Boolean> advancedItemTooltips() {
        return OPTIONS.toggle("advanced_item_tooltips",
                (config, value) -> {
                    Minecraft.getMinecraft().gameSettings.advancedItemTooltips = value;
                    Minecraft.getMinecraft().gameSettings.saveOptions();
                },
                config -> Minecraft.getMinecraft().gameSettings.advancedItemTooltips,
                null, null, null);
    }

    // Adds one group per particle class list.
    private static void addParticleClassGroups(List<OptionGroup> groups, BooleanSupplier particlesOn) {
        try {
            ParticleClassRegistry registry = ParticleClassRegistry.getInstance();
            registry.scanFactories(Minecraft.getMinecraft().effectRenderer);

            Map<String, String> discovered = registry.getDiscoveredClasses();
            if (discovered.isEmpty()) {
                return;
            }

            Map<String, List<Map.Entry<String, String>>> byMod = new TreeMap<>();
            for (Map.Entry<String, String> entry : discovered.entrySet()) {
                String modId = registry.getModId(entry.getKey());
                byMod.computeIfAbsent(modId == null ? "unknown" : modId, key -> new ArrayList<>()).add(entry);
            }

            for (Map.Entry<String, List<Map.Entry<String, String>>> modEntry : byMod.entrySet()) {
                String modId = modEntry.getKey();
                List<Map.Entry<String, String>> classes = modEntry.getValue();
                classes.sort(Comparator.comparing(Map.Entry::getValue));

                OptionGroup.Builder builder = OptionGroup.createBuilder()
                        .setId(OPTIONS.group("particles." + modId))
                        .setName(TextComponent.literal("minecraft".equals(modId) ? "Minecraft" : modId));

                boolean showModId = !"minecraft".equals(modId);

                for (Map.Entry<String, String> classEntry : classes) {
                    String fullName = classEntry.getKey();
                    String simpleName = classEntry.getValue();
                    String label = showModId ? simpleName + " (" + modId + ")" : simpleName;

                    builder.add(OptionImpl.createBuilder(boolean.class, OPTIONS.storage())
                            .setId(OPTIONS.option("particles.class." + fullName, boolean.class))
                            .setName(TextComponent.literal(label))
                            .setTooltip(TextComponent.literal(fullName))
                            .setControl(TickBoxControl::new)
                            .setBinding(
                                    (config, value) -> registry.setClassEnabled(fullName, value),
                                    config -> !registry.isClassDisabled(fullName))
                            .setEnabledPredicate(particlesOn)
                            .build());
                }

                groups.add(builder.build());
            }
        } catch (Throwable t) {
            Extras.LOGGER.warn("Could not build the per-class particle toggles", t);
        }
    }

    // Returns the toggle of one animation.
    private static OptionImpl<ExtrasConfig, Boolean> animationToggle(
            String key, BooleanSupplier enabled,
            BiConsumer<ExtrasConfig, Boolean> setter, Function<ExtrasConfig, Boolean> getter) {
        return OPTIONS.toggle(key, setter, getter, null, OptionFlag.REQUIRES_ASSET_RELOAD, enabled);
    }
}
