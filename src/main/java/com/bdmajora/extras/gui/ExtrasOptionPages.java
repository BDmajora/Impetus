package com.bdmajora.extras.gui;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.ExtrasConfig;
import com.bdmajora.extras.async.ParallelProcessor;
import com.bdmajora.extras.client.AdaptiveSync;
import com.bdmajora.extras.client.particle.ParticleClassRegistry;
import com.bdmajora.impetus.api.options.OptionIdentifier;
import com.bdmajora.impetus.api.options.control.ControlValueFormatter;
import com.bdmajora.impetus.api.options.control.CyclingControl;
import com.bdmajora.impetus.api.options.control.SliderControl;
import com.bdmajora.impetus.api.options.control.TickBoxControl;
import com.bdmajora.impetus.api.options.structure.OptionFlag;
import com.bdmajora.impetus.api.options.structure.OptionGroup;
import com.bdmajora.impetus.api.options.structure.OptionImpact;
import com.bdmajora.impetus.api.options.structure.OptionImpl;
import com.bdmajora.impetus.api.options.structure.OptionPage;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
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

// The Extras page: Sodium Extra plus the finer OptiFine switches as one tab; sub-options use setEnabledPredicate so a master switch greys out its children
public final class ExtrasOptionPages {
    private static final String MOD_ID = "impetus";
    private static final String LANG = "impetus.options.extras.";

    private static final ExtrasOptionsStorage STORAGE = new ExtrasOptionsStorage();

    private ExtrasOptionPages() {
    }

    // Builds the page; the particle master is created first so its class toggles gate on the pending value
    public static OptionPage extras() {
        List<OptionGroup> groups = new ArrayList<>();

        // Built here rather than in particles() so the per-class toggles gate on the same instance and grey out immediately like every other sub-option, not after Apply
        OptionImpl<ExtrasConfig, Boolean> particlesMaster = toggle("particles.all",
                (config, value) -> config.particle.all = value,
                config -> config.particle.all,
                OptionImpact.HIGH, null, null);

        groups.add(animations());
        groups.add(particles(particlesMaster));
        addParticleClassGroups(groups, particlesMaster::getValue);
        groups.add(details());
        groups.add(fog());
        groups.add(cloudsAndWeather());
        groups.add(entityRendering());
        groups.add(renderBudget());
        groups.add(gpuBooster());
        groups.add(parallelTicking());
        groups.add(bakedBlockEntities());
        groups.add(network());
        groups.add(threadScheduling());
        groups.add(occlusionCulling());
        groups.add(hud());
        groups.add(textAndModels());
        groups.add(leaves());
        groups.add(loading());
        groups.add(overlay());
        groups.add(toasts());
        groups.add(qualityOfLife());

        return new OptionPage(
                OptionIdentifier.create(MOD_ID, "extras"),
                TextComponent.translatable("impetus.options.pages.extras"),
                ImmutableList.copyOf(groups));
    }

    // Groups

    private static OptionGroup animations() {
        OptionImpl<ExtrasConfig, Boolean> master = toggle("animations.all",
                (config, value) -> config.animation.all = value,
                config -> config.animation.all,
                OptionImpact.MEDIUM, OptionFlag.REQUIRES_ASSET_RELOAD, null);
        BooleanSupplier enabled = master::getValue;

        return OptionGroup.createBuilder()
                .setId(group("animations"))
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

    // Master switch plus one toggle per discovered particle class, grouped by owning mod
    private static OptionGroup particles(OptionImpl<ExtrasConfig, Boolean> master) {
        BooleanSupplier enabled = master::getValue;

        OptionImpl<ExtrasConfig, Boolean> parallel = toggle("particles.parallel",
                (config, value) -> config.particle.parallelTick = value,
                config -> config.particle.parallelTick, OptionImpact.HIGH, null, enabled);
        BooleanSupplier parallelOn = () -> enabled.getAsBoolean() && parallel.getValue();

        return OptionGroup.createBuilder()
                .setId(group("particles"))
                .add(master)
                .add(toggle("particles.rain_splash", (config, value) -> config.particle.rainSplash = value,
                        config -> config.particle.rainSplash, null, null, enabled))
                .add(toggle("particles.block_break", (config, value) -> config.particle.blockBreak = value,
                        config -> config.particle.blockBreak, null, null, enabled))
                .add(toggle("particles.block_breaking", (config, value) -> config.particle.blockBreaking = value,
                        config -> config.particle.blockBreaking, null, null, enabled))
                .add(toggle("particles.void", (config, value) -> config.particle.voidParticles = value,
                        config -> config.particle.voidParticles, null, null, enabled))
                .add(toggle("particles.water", (config, value) -> config.particle.waterParticles = value,
                        config -> config.particle.waterParticles, null, null, enabled))
                .add(toggle("particles.portal", (config, value) -> config.particle.portalParticles = value,
                        config -> config.particle.portalParticles, null, null, enabled))
                .add(toggle("particles.potion", (config, value) -> config.particle.potionParticles = value,
                        config -> config.particle.potionParticles, null, null, enabled))
                .add(toggle("particles.dripping", (config, value) -> config.particle.drippingWaterLava = value,
                        config -> config.particle.drippingWaterLava, null, null, enabled))
                .add(toggle("particles.firework", (config, value) -> config.particle.fireworkParticles = value,
                        config -> config.particle.fireworkParticles, null, null, enabled))
                .add(parallel)
                .add(toggle("particles.parallel_modded", (config, value) -> config.particle.parallelModded = value,
                        config -> config.particle.parallelModded, OptionImpact.VARIES, null, parallelOn))
                .add(toggle("particles.light_cache", (config, value) -> config.particle.lightCache = value,
                        config -> config.particle.lightCache, OptionImpact.MEDIUM, null, enabled))
                .add(toggle("particles.cull_offscreen", (config, value) -> config.particle.cullOffscreen = value,
                        config -> config.particle.cullOffscreen, OptionImpact.MEDIUM, null, enabled))
                .add(toggle("particles.collision_cache", (config, value) -> config.particle.collisionCache = value,
                        config -> config.particle.collisionCache, OptionImpact.MEDIUM, null, enabled))
                .build();
    }

    // Sky and world detail toggles; sub-options gate on their parent
    private static OptionGroup details() {
        OptionImpl<ExtrasConfig, Boolean> stars = toggle("details.stars",
                (config, value) -> config.detail.stars = value,
                config -> config.detail.stars,
                null, OptionFlag.REQUIRES_RENDERER_RELOAD, null);
        BooleanSupplier starsOn = stars::getValue;

        return OptionGroup.createBuilder()
                .setId(group("details"))
                .add(toggle("details.sky", (config, value) -> config.detail.sky = value,
                        config -> config.detail.sky, null, OptionFlag.REQUIRES_RENDERER_RELOAD, null))
                .add(stars)
                .add(slider("details.total_stars",
                        ExtrasConfig.DetailSettings.STARS_MIN, ExtrasConfig.DetailSettings.STARS_MAX, 500,
                        ControlValueFormatter.number(),
                        (config, value) -> config.detail.totalStars = value,
                        config -> config.detail.totalStars,
                        OptionImpact.MEDIUM, OptionFlag.REQUIRES_RENDERER_RELOAD, starsOn))
                .add(toggle("details.sun", (config, value) -> config.detail.sun = value,
                        config -> config.detail.sun, null, null, null))
                .add(toggle("details.moon", (config, value) -> config.detail.moon = value,
                        config -> config.detail.moon, null, null, null))
                .add(toggle("details.rain_snow", (config, value) -> config.detail.rainSnow = value,
                        config -> config.detail.rainSnow, OptionImpact.LOW, null, null))
                .add(toggle("details.biome_colors", (config, value) -> config.detail.biomeColors = value,
                        config -> config.detail.biomeColors, OptionImpact.MEDIUM,
                        OptionFlag.REQUIRES_RENDERER_RELOAD, null))
                .add(toggle("details.sky_colors", (config, value) -> config.detail.skyColors = value,
                        config -> config.detail.skyColors, null, null, null))
                .add(toggle("details.swamp_colors", (config, value) -> config.detail.swampColors = value,
                        config -> config.detail.swampColors, null, OptionFlag.REQUIRES_RENDERER_RELOAD, null))
                .add(toggle("details.void_fog", (config, value) -> config.detail.voidFog = value,
                        config -> config.detail.voidFog, null, null, null))
                .add(toggle("details.capes", (config, value) -> config.detail.showCapes = value,
                        config -> config.detail.showCapes, null, null, null))
                .add(toggle("details.held_item_tooltips", (config, value) -> config.detail.heldItemTooltips = value,
                        config -> config.detail.heldItemTooltips, null, null, null))
                .build();
    }

    // Fog master plus the per-type toggles it governs
    private static OptionGroup fog() {
        OptionImpl<ExtrasConfig, Boolean> master = toggle("render.fog",
                (config, value) -> config.render.fog = value,
                config -> config.render.fog, OptionImpact.LOW, null, null);
        BooleanSupplier enabled = master::getValue;

        return OptionGroup.createBuilder()
                .setId(group("fog"))
                .add(master)
                .add(slider("render.fog_start", 0, 200, 10, ControlValueFormatter.percentage(),
                        (config, value) -> config.render.fogStart = value,
                        config -> config.render.fogStart, null, null, enabled))
                .add(slider("render.fog_distance", 0, 32, 1,
                        ControlValueFormatter.quantityOrDisabled("chunks", "Default"),
                        (config, value) -> config.render.fogDistance = value,
                        config -> config.render.fogDistance, OptionImpact.MEDIUM, null, enabled))
                .add(cycling("render.fog_shape", ExtrasConfig.FogShape.class, ExtrasConfig.FogShape.values(),
                        (config, value) -> config.render.fogShape = value,
                        config -> config.render.fogShape, enabled))
                .build();
    }

    // Cloud scale, height and translucency, plus weather rendering
    private static OptionGroup cloudsAndWeather() {
        return OptionGroup.createBuilder()
                .setId(group("clouds"))
                .add(slider("render.cloud_scale",
                        ExtrasConfig.RenderSettings.CLOUD_SCALE_MIN,
                        ExtrasConfig.RenderSettings.CLOUD_SCALE_MAX, 1,
                        value -> TextComponent.literal(String.format(Locale.ROOT, "%.2fx",
                                (float) value / ExtrasConfig.RenderSettings.CLOUD_SCALE_VANILLA)),
                        (config, value) -> config.render.cloudScale = value,
                        config -> config.render.cloudScale, null, null, null))
                .add(cycling("render.cloud_translucency", ExtrasConfig.CloudTranslucency.class,
                        ExtrasConfig.CloudTranslucency.values(),
                        (config, value) -> config.render.cloudTranslucency = value,
                        config -> config.render.cloudTranslucency, null))
                .add(cycling("time", ExtrasConfig.TimeOverride.class, ExtrasConfig.TimeOverride.values(),
                        (config, value) -> config.extra.timeOverride = value,
                        config -> config.extra.timeOverride, null))
                .add(cycling("weather", ExtrasConfig.WeatherOverride.class, ExtrasConfig.WeatherOverride.values(),
                        (config, value) -> config.extra.weatherOverride = value,
                        config -> config.extra.weatherOverride, null))
                .build();
    }

    // Item frame, name tag and armour stand toggles; the frame sub-options gate on frames
    private static OptionGroup entityRendering() {
        OptionImpl<ExtrasConfig, Boolean> itemFrames = toggle("render.item_frames",
                (config, value) -> config.render.itemFrames = value,
                config -> config.render.itemFrames, OptionImpact.MEDIUM, null, null);
        BooleanSupplier itemFramesOn = itemFrames::getValue;

        OptionImpl<ExtrasConfig, Boolean> beacons = toggle("render.beacons",
                (config, value) -> config.render.beacons = value,
                config -> config.render.beacons, OptionImpact.LOW, null, null);
        BooleanSupplier beaconsOn = beacons::getValue;

        return OptionGroup.createBuilder()
                .setId(group("entities"))
                .add(itemFrames)
                .add(slider("render.item_frame_lod", 0, 256, 8,
                        ControlValueFormatter.quantityOrDisabled("blocks", "Off"),
                        (config, value) -> config.render.itemFrameLodDistance = value,
                        config -> config.render.itemFrameLodDistance, OptionImpact.LOW, null, itemFramesOn))
                .add(toggle("render.item_frame_name_tag", (config, value) -> config.render.itemFrameNameTag = value,
                        config -> config.render.itemFrameNameTag, null, null, itemFramesOn))
                .add(toggle("render.armor_stands", (config, value) -> config.render.armorStands = value,
                        config -> config.render.armorStands, OptionImpact.LOW, null, null))
                .add(toggle("render.paintings", (config, value) -> config.render.paintings = value,
                        config -> config.render.paintings, null, null, null))
                .add(toggle("render.player_name_tag", (config, value) -> config.render.playerNameTag = value,
                        config -> config.render.playerNameTag, null, null, null))
                .add(toggle("render.dropped_items", (config, value) -> config.render.droppedItemsFancy = value,
                        config -> config.render.droppedItemsFancy, OptionImpact.MEDIUM, null, null))
                .add(beacons)
                .add(toggle("render.limit_beacon_beam", (config, value) -> config.render.limitBeaconBeamHeight = value,
                        config -> config.render.limitBeaconBeamHeight, null, null, beaconsOn))
                .add(toggle("render.enchanting_books", (config, value) -> config.render.enchantingTableBooks = value,
                        config -> config.render.enchantingTableBooks, null, null, null))
                .add(toggle("render.pistons", (config, value) -> config.render.pistons = value,
                        config -> config.render.pistons, OptionImpact.LOW, null, null))
                .build();
    }

    // Adaptive render budgeting (GpuShift's design); everything gates on the master, and the particle slider reads Off at 100% since that hands particles back entirely
    private static OptionGroup renderBudget() {
        OptionImpl<ExtrasConfig, Boolean> master = toggle("budget.enabled",
                (config, value) -> config.renderBudget.enabled = value,
                config -> config.renderBudget.enabled, OptionImpact.VARIES, null, null);
        BooleanSupplier enabled = master::getValue;

        OptionImpl<ExtrasConfig, Boolean> entities = toggle("budget.entities",
                (config, value) -> config.renderBudget.smartEntityCulling = value,
                config -> config.renderBudget.smartEntityCulling, OptionImpact.MEDIUM, null, enabled);
        BooleanSupplier entitiesOn = () -> enabled.getAsBoolean() && entities.getValue();

        OptionImpl<ExtrasConfig, Boolean> blockEntities = toggle("budget.block_entities",
                (config, value) -> config.renderBudget.blockEntities = value,
                config -> config.renderBudget.blockEntities, OptionImpact.MEDIUM, null, enabled);
        BooleanSupplier blockEntitiesOn = () -> enabled.getAsBoolean() && blockEntities.getValue();

        return OptionGroup.createBuilder()
                .setId(group("render_budget"))
                .add(master)
                .add(cycling("budget.profile", ExtrasConfig.BudgetProfile.class, ExtrasConfig.BudgetProfile.values(),
                        (config, value) -> config.renderBudget.profile = value,
                        config -> config.renderBudget.profile, enabled))
                .add(toggle("budget.adaptive", (config, value) -> config.renderBudget.adaptive = value,
                        config -> config.renderBudget.adaptive, null, null, enabled))
                .add(slider("budget.particles",
                        ExtrasConfig.RenderBudgetSettings.PARTICLE_BUDGET_MIN,
                        ExtrasConfig.RenderBudgetSettings.PARTICLE_BUDGET_MAX, 5,
                        value -> value >= ExtrasConfig.RenderBudgetSettings.PARTICLE_BUDGET_MAX
                                ? TextComponent.translatable("options.off")
                                : TextComponent.literal(value + "%"),
                        (config, value) -> config.renderBudget.particleBudget = value,
                        config -> config.renderBudget.particleBudget, OptionImpact.MEDIUM, null, enabled))
                .add(entities)
                .add(slider("budget.entity_distance",
                        ExtrasConfig.RenderBudgetSettings.ENTITY_DISTANCE_MIN,
                        ExtrasConfig.RenderBudgetSettings.ENTITY_DISTANCE_MAX, 8,
                        value -> TextComponent.literal(value + " blocks"),
                        (config, value) -> config.renderBudget.entityCullDistance = value,
                        config -> config.renderBudget.entityCullDistance, null, null, entitiesOn))
                .add(blockEntities)
                .add(slider("budget.block_entity_distance",
                        ExtrasConfig.RenderBudgetSettings.BLOCK_ENTITY_DISTANCE_MIN,
                        ExtrasConfig.RenderBudgetSettings.BLOCK_ENTITY_DISTANCE_MAX, 8,
                        value -> TextComponent.literal(value + " blocks"),
                        (config, value) -> config.renderBudget.blockEntityDistance = value,
                        config -> config.renderBudget.blockEntityDistance, null, null, blockEntitiesOn))
                .add(toggle("budget.item_frames", (config, value) -> config.renderBudget.itemFrames = value,
                        config -> config.renderBudget.itemFrames, OptionImpact.LOW, null, blockEntitiesOn))
                .add(toggle("budget.overlay", (config, value) -> config.renderBudget.overlay = value,
                        config -> config.renderBudget.overlay, null, null, null))
                .build();
    }

    // GPU Booster, the companion to the render budget: where the budget decides what not to draw, this makes what is drawn cheaper to hand over; everything gates on the master
    private static OptionGroup gpuBooster() {
        OptionImpl<ExtrasConfig, Boolean> master = toggle("booster.enabled",
                (config, value) -> config.gpuBooster.enabled = value,
                config -> config.gpuBooster.enabled, OptionImpact.VARIES, null, null);
        BooleanSupplier enabled = master::getValue;

        return OptionGroup.createBuilder()
                .setId(group("gpu_booster"))
                .add(master)
                .add(toggle("booster.fast_random", (config, value) -> config.gpuBooster.fastRandom = value,
                        config -> config.gpuBooster.fastRandom, OptionImpact.MEDIUM, null, enabled))
                .add(toggle("booster.fast_math", (config, value) -> config.gpuBooster.fastMath = value,
                        config -> config.gpuBooster.fastMath, OptionImpact.LOW, null, enabled))
                .add(toggle("booster.stream_uploads", (config, value) -> config.gpuBooster.streamUploads = value,
                        config -> config.gpuBooster.streamUploads, OptionImpact.VARIES, null, enabled))
                .build();
    }

    // Parallel server ticking (Async's design); the master needs a relaunch since it decides what a world is built with, the rest is live and gates on it
    private static OptionGroup parallelTicking() {
        OptionImpl<ExtrasConfig, Boolean> master = toggle("parallel.enabled",
                (config, value) -> config.async.enabled = value,
                config -> config.async.enabled, OptionImpact.HIGH, OptionFlag.REQUIRES_GAME_RESTART, null);
        BooleanSupplier enabled = master::getValue;

        OptionImpl<ExtrasConfig, Boolean> entities = toggle("parallel.entities",
                (config, value) -> config.async.entities = value,
                config -> config.async.entities, OptionImpact.HIGH, null, enabled);
        BooleanSupplier entitiesOn = () -> enabled.getAsBoolean() && entities.getValue();

        return OptionGroup.createBuilder()
                .setId(group("parallel_ticking"))
                .add(master)
                .add(slider("parallel.threads", 0, Math.min(ExtrasConfig.AsyncSettings.THREADS_MAX, ParallelProcessor.maxThreads()), 1,
                        value -> value == 0
                                ? TextComponent.literal("Auto (" + ParallelProcessor.desiredThreads() + ")")
                                : TextComponent.literal(value + (value == 1 ? " thread" : " threads")),
                        (config, value) -> config.async.threads = value,
                        config -> config.async.threads, OptionImpact.MEDIUM, null, enabled))
                .add(entities)
                .add(toggle("parallel.modded_entities", (config, value) -> config.async.moddedEntities = value,
                        config -> config.async.moddedEntities, OptionImpact.VARIES, null, entitiesOn))
                .add(toggle("parallel.random_ticks", (config, value) -> config.async.randomTicks = value,
                        config -> config.async.randomTicks, OptionImpact.MEDIUM, null, enabled))
                .add(toggle("parallel.spawning", (config, value) -> config.async.spawning = value,
                        config -> config.async.spawning, OptionImpact.LOW, null, enabled))
                .build();
    }

    // Block entities drawn as terrain (Enhanced Block Entities' design); every switch reloads the renderer since the mesh has to pick the blocks up or drop them
    private static OptionGroup bakedBlockEntities() {
        OptionImpl<ExtrasConfig, Boolean> master = toggle("baked.enabled",
                (config, value) -> config.bakedEntities.enabled = value,
                config -> config.bakedEntities.enabled, OptionImpact.HIGH, OptionFlag.REQUIRES_RENDERER_RELOAD, null);
        BooleanSupplier enabled = master::getValue;

        return OptionGroup.createBuilder()
                .setId(group("baked_block_entities"))
                .add(master)
                .add(toggle("baked.chests", (config, value) -> config.bakedEntities.chests = value,
                        config -> config.bakedEntities.chests, OptionImpact.HIGH, OptionFlag.REQUIRES_RENDERER_RELOAD, enabled))
                .add(toggle("baked.ender_chests", (config, value) -> config.bakedEntities.enderChests = value,
                        config -> config.bakedEntities.enderChests, OptionImpact.LOW, OptionFlag.REQUIRES_RENDERER_RELOAD, enabled))
                .add(toggle("baked.signs", (config, value) -> config.bakedEntities.signs = value,
                        config -> config.bakedEntities.signs, OptionImpact.MEDIUM, OptionFlag.REQUIRES_RENDERER_RELOAD, enabled))
                .add(toggle("baked.beds", (config, value) -> config.bakedEntities.beds = value,
                        config -> config.bakedEntities.beds, OptionImpact.LOW, OptionFlag.REQUIRES_RENDERER_RELOAD, enabled))
                .add(toggle("baked.shulker_boxes", (config, value) -> config.bakedEntities.shulkerBoxes = value,
                        config -> config.bakedEntities.shulkerBoxes, OptionImpact.MEDIUM, OptionFlag.REQUIRES_RENDERER_RELOAD, enabled))
                .build();
    }

    // Wire limits and timeouts (Packet Fixer's set); all live, read per packet
    private static OptionGroup network() {
        return OptionGroup.createBuilder()
                .setId(group("network"))
                .add(toggle("network.large_packets", (config, value) -> config.network.largePackets = value,
                        config -> config.network.largePackets, null, null, null))
                .add(toggle("network.flush_consolidation", (config, value) -> config.network.flushConsolidation = value,
                        config -> config.network.flushConsolidation, OptionImpact.MEDIUM, null, null))
                .add(toggle("network.fast_varints", (config, value) -> config.network.fastVarInts = value,
                        config -> config.network.fastVarInts, OptionImpact.LOW, null, null))
                .add(toggle("network.pooled_compression", (config, value) -> config.network.pooledCompression = value,
                        config -> config.network.pooledCompression, OptionImpact.LOW, null, null))
                .add(slider("network.read_timeout",
                        ExtrasConfig.NetworkSettings.TIMEOUT_MIN, ExtrasConfig.NetworkSettings.TIMEOUT_MAX, 5,
                        value -> TextComponent.literal(value + "s"),
                        (config, value) -> config.network.readTimeoutSeconds = value,
                        config -> config.network.readTimeoutSeconds, null, null, null))
                .add(slider("network.login_timeout",
                        ExtrasConfig.NetworkSettings.TIMEOUT_MIN, ExtrasConfig.NetworkSettings.TIMEOUT_MAX, 5,
                        value -> TextComponent.literal(value + "s"),
                        (config, value) -> config.network.loginTimeoutSeconds = value,
                        config -> config.network.loginTimeoutSeconds, null, null, null))
                .add(slider("network.keep_alive_timeout",
                        ExtrasConfig.NetworkSettings.TIMEOUT_MIN, ExtrasConfig.NetworkSettings.TIMEOUT_MAX, 5,
                        value -> TextComponent.literal(value + "s"),
                        (config, value) -> config.network.keepAliveTimeoutSeconds = value,
                        config -> config.network.keepAliveTimeoutSeconds, null, null, null))
                .build();
    }

    // Thread priorities and the render loop's yield (StutterFix's set); opt-in, every default is the game's own behaviour
    private static OptionGroup threadScheduling() {
        return OptionGroup.createBuilder()
                .setId(group("thread_scheduling"))
                .add(slider("threads.render_priority",
                        ExtrasConfig.ThreadSettings.PRIORITY_MIN, ExtrasConfig.ThreadSettings.PRIORITY_MAX, 1,
                        ExtrasOptionPages::priorityLabel,
                        (config, value) -> config.threads.renderThreadPriority = value,
                        config -> config.threads.renderThreadPriority, OptionImpact.VARIES, null, null))
                .add(slider("threads.server_priority",
                        ExtrasConfig.ThreadSettings.PRIORITY_MIN, ExtrasConfig.ThreadSettings.PRIORITY_MAX, 1,
                        ExtrasOptionPages::priorityLabel,
                        (config, value) -> config.threads.serverThreadPriority = value,
                        config -> config.threads.serverThreadPriority, OptionImpact.VARIES, null, null))
                .add(slider("threads.chunk_builder_priority",
                        ExtrasConfig.ThreadSettings.PRIORITY_MIN, ExtrasConfig.ThreadSettings.PRIORITY_MAX, 1,
                        ExtrasOptionPages::priorityLabel,
                        (config, value) -> config.threads.chunkBuilderPriority = value,
                        config -> config.threads.chunkBuilderPriority, OptionImpact.VARIES, OptionFlag.REQUIRES_RENDERER_RELOAD, null))
                .add(toggle("threads.remove_yield", (config, value) -> config.threads.removeRenderYield = value,
                        config -> config.threads.removeRenderYield, OptionImpact.VARIES, null, null))
                .build();
    }

    // Ray-cast occlusion of entities and block entities (tr7zw's / Meldexun's Entity Culling); the size caps and slack gate on the master
    private static OptionGroup occlusionCulling() {
        OptionImpl<ExtrasConfig, Boolean> master = toggle("occlusion.enabled",
                (config, value) -> config.occlusion.enabled = value,
                config -> config.occlusion.enabled, OptionImpact.HIGH, null, null);
        BooleanSupplier enabled = master::getValue;
        return OptionGroup.createBuilder()
                .setId(group("occlusion_culling"))
                .add(master)
                .add(toggle("occlusion.entities", (config, value) -> config.occlusion.entities = value,
                        config -> config.occlusion.entities, OptionImpact.HIGH, null, enabled))
                .add(toggle("occlusion.block_entities", (config, value) -> config.occlusion.blockEntities = value,
                        config -> config.occlusion.blockEntities, OptionImpact.MEDIUM, null, enabled))
                .add(slider("occlusion.max_entity_size", 1, 32, 1,
                        value -> TextComponent.literal(value + " blocks"),
                        (config, value) -> config.occlusion.maxEntitySize = value,
                        config -> config.occlusion.maxEntitySize, null, null, enabled))
                .add(slider("occlusion.max_block_entity_size", 1, 32, 1,
                        value -> TextComponent.literal(value + " blocks"),
                        (config, value) -> config.occlusion.maxBlockEntitySize = value,
                        config -> config.occlusion.maxBlockEntitySize, null, null, enabled))
                .add(slider("occlusion.raycast_slack", 0, 300, 25,
                        value -> TextComponent.literal(String.format("%.2f blocks", value / 100.0D)),
                        (config, value) -> config.occlusion.raycastSlack = value,
                        config -> config.occlusion.raycastSlack, null, null, enabled))
                .build();
    }

    // HUD framebuffer caching (Exordium / Gnetum family)
    private static OptionGroup hud() {
        OptionImpl<ExtrasConfig, Boolean> master = toggle("hud.cache",
                (config, value) -> config.hud.cacheEnabled = value,
                config -> config.hud.cacheEnabled, OptionImpact.MEDIUM, null, null);
        return OptionGroup.createBuilder()
                .setId(group("hud"))
                .add(master)
                .add(slider("hud.cache_fps", ExtrasConfig.HudSettings.CACHE_FPS_MIN, ExtrasConfig.HudSettings.CACHE_FPS_MAX, 5,
                        value -> TextComponent.literal(value + " fps"),
                        (config, value) -> config.hud.cacheFps = value,
                        config -> config.hud.cacheFps, null, null, master::getValue))
                .build();
    }

    // Glyph batching (ImmediatelyFast), lightmap caching (BadOptimizations) and model matrices (Valkyrie)
    private static OptionGroup textAndModels() {
        return OptionGroup.createBuilder()
                .setId(group("text_and_models"))
                .add(toggle("text.batch_glyphs", (config, value) -> config.text.batchGlyphs = value,
                        config -> config.text.batchGlyphs, OptionImpact.MEDIUM, null, null))
                .add(toggle("client_tick.lightmap_caching", (config, value) -> config.clientTick.lightmapCaching = value,
                        config -> config.clientTick.lightmapCaching, OptionImpact.LOW, null, null))
                .add(toggle("entity_models.matrix_transforms", (config, value) -> config.entityModels.matrixTransforms = value,
                        config -> config.entityModels.matrixTransforms, OptionImpact.MEDIUM, null, null))
                .build();
    }

    // Fancy-leaf face culling (More Culling / Cull Less Leaves); a change needs the chunk meshes rebuilt
    private static OptionGroup leaves() {
        OptionImpl<ExtrasConfig, ExtrasConfig.LeafCulling> mode = OptionImpl.createBuilder(ExtrasConfig.LeafCulling.class, STORAGE)
                .setId(option("leaves.culling_mode", ExtrasConfig.LeafCulling.class))
                .setName(TextComponent.translatable(LANG + "leaves.culling_mode.name"))
                .setTooltip(TextComponent.translatable(LANG + "leaves.culling_mode.tooltip"))
                .setControl(opt -> new CyclingControl<>(opt, ExtrasConfig.LeafCulling.values(), localizedNames(ExtrasConfig.LeafCulling.values())))
                .setBinding((config, value) -> config.leaves.cullingMode = value, config -> config.leaves.cullingMode)
                .setImpact(OptionImpact.LOW)
                .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                .build();
        return OptionGroup.createBuilder()
                .setId(group("leaves"))
                .add(mode)
                .add(slider("leaves.culling_depth", ExtrasConfig.LeafSettings.DEPTH_MIN, ExtrasConfig.LeafSettings.DEPTH_MAX, 1,
                        value -> TextComponent.literal(value + " blocks"),
                        (config, value) -> config.leaves.cullingDepth = value,
                        config -> config.leaves.cullingDepth, null, OptionFlag.REQUIRES_RENDERER_RELOAD,
                        () -> mode.getValue() == ExtrasConfig.LeafCulling.DEPTH))
                .build();
    }

    // World-load and misc hitches (VanillaFix, Chibi, Universal Tweaks)
    private static OptionGroup loading() {
        return OptionGroup.createBuilder()
                .setId(group("loading"))
                .add(toggle("loading.skip_world_load_gc", (config, value) -> config.loading.skipWorldLoadGc = value,
                        config -> config.loading.skipWorldLoadGc, OptionImpact.MEDIUM, null, null))
                .add(toggle("loading.smooth_dimension_change", (config, value) -> config.loading.smoothDimensionChange = value,
                        config -> config.loading.smoothDimensionChange, null, null, null))
                .add(toggle("loading.release_screenshot_buffers", (config, value) -> config.loading.releaseScreenshotBuffers = value,
                        config -> config.loading.releaseScreenshotBuffers, null, null, null))
                .add(toggle("loading.async_screenshots", (config, value) -> config.loading.asyncScreenshots = value,
                        config -> config.loading.asyncScreenshots, null, null, null))
                .add(toggle("loading.driver_atlas_limit", (config, value) -> config.loading.driverAtlasLimit = value,
                        config -> config.loading.driverAtlasLimit, null, OptionFlag.REQUIRES_GAME_RESTART, null))
                .build();
    }

    // Java's 1-10 scale with the three named points called out
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

    // FPS counter and coordinates HUD; layout options gate on the counter being shown
    private static OptionGroup overlay() {
        OptionImpl<ExtrasConfig, Boolean> showFps = toggle("overlay.fps",
                (config, value) -> config.extra.showFps = value,
                config -> config.extra.showFps, null, null, null);
        BooleanSupplier fpsOn = showFps::getValue;

        OptionImpl<ExtrasConfig, Boolean> showCoords = toggle("overlay.coords",
                (config, value) -> config.extra.showCoords = value,
                config -> config.extra.showCoords, null, null, null);
        BooleanSupplier coordsOn = showCoords::getValue;

        return OptionGroup.createBuilder()
                .setId(group("overlay"))
                .add(showFps)
                .add(toggle("overlay.fps_extended", (config, value) -> config.extra.showFpsExtended = value,
                        config -> config.extra.showFpsExtended, null, null, fpsOn))
                .add(showCoords)
                .add(toggle("overlay.ignore_reduced_debug_info",
                        (config, value) -> config.extra.ignoreReducedDebugInfo = value,
                        config -> config.extra.ignoreReducedDebugInfo, null, null, coordsOn))
                .add(cycling("overlay.corner", ExtrasConfig.OverlayCorner.class,
                        ExtrasConfig.OverlayCorner.values(),
                        (config, value) -> config.extra.overlayCorner = value,
                        config -> config.extra.overlayCorner, null))
                .add(cycling("overlay.text_contrast", ExtrasConfig.TextContrast.class,
                        ExtrasConfig.TextContrast.values(),
                        (config, value) -> config.extra.textContrast = value,
                        config -> config.extra.textContrast, null))
                .build();
    }

    // Toast master plus per-category toggles it governs
    private static OptionGroup toasts() {
        OptionImpl<ExtrasConfig, Boolean> master = toggle("toasts.all",
                (config, value) -> config.extra.toasts = value,
                config -> config.extra.toasts, null, null, null);
        BooleanSupplier enabled = master::getValue;

        return OptionGroup.createBuilder()
                .setId(group("toasts"))
                .add(master)
                .add(toggle("toasts.advancement", (config, value) -> config.extra.toastAdvancement = value,
                        config -> config.extra.toastAdvancement, null, null, enabled))
                .add(toggle("toasts.recipe", (config, value) -> config.extra.toastRecipe = value,
                        config -> config.extra.toastRecipe, null, null, enabled))
                .add(toggle("toasts.tutorial", (config, value) -> config.extra.toastTutorial = value,
                        config -> config.extra.toastTutorial, null, null, enabled))
                .add(toggle("toasts.system", (config, value) -> config.extra.toastSystem = value,
                        config -> config.extra.toastSystem, null, null, enabled))
                .build();
    }

    // Miscellaneous switches with no natural home elsewhere
    private static OptionGroup qualityOfLife() {
        OptionImpl<ExtrasConfig, Boolean> steadyHud = toggle("steady_debug_hud",
                (config, value) -> config.extra.steadyDebugHud = value,
                config -> config.extra.steadyDebugHud, OptionImpact.LOW, null, null);
        BooleanSupplier steadyHudOn = steadyHud::getValue;

        OptionImpl<ExtrasConfig, Boolean> panini = toggle("panini",
                (config, value) -> config.extra.paniniProjection = value,
                config -> config.extra.paniniProjection, OptionImpact.MEDIUM, null, null);
        BooleanSupplier paniniOn = panini::getValue;

        OptionGroup.Builder builder = OptionGroup.createBuilder()
                .setId(group("misc"))
                .add(verticalSync())
                .add(steadyHud)
                .add(slider("steady_debug_hud_refresh",
                        ExtrasConfig.ExtraSettings.STEADY_HUD_REFRESH_MIN,
                        ExtrasConfig.ExtraSettings.STEADY_HUD_REFRESH_MAX, 1,
                        value -> TextComponent.literal(value + (value == 1 ? " tick" : " ticks")),
                        (config, value) -> config.extra.steadyDebugHudRefreshInterval = value,
                        config -> config.extra.steadyDebugHudRefreshInterval, null, null, steadyHudOn))
                .add(panini)
                .add(slider("panini_strength", 0, 100, 5, ControlValueFormatter.percentage(),
                        (config, value) -> config.extra.paniniProjectionStrength = value,
                        config -> config.extra.paniniProjectionStrength, null, null, paniniOn))
                .add(advancedItemTooltips())
                .add(toggle("mod_name_tooltip", (config, value) -> config.extra.modNameTooltip = value,
                        config -> config.extra.modNameTooltip, null, null, null))
                .add(toggle("light_updates", (config, value) -> config.render.lightUpdates = value,
                        config -> config.render.lightUpdates, OptionImpact.HIGH, null, null))
                .add(toggle("prevent_shaders", (config, value) -> config.render.preventShaders = value,
                        config -> config.render.preventShaders, null, null, null))
                .add(toggle("profile_entity_rendering",
                        (config, value) -> config.render.profileEntityRendering = value,
                        config -> config.render.profileEntityRendering, null, null, null))
                .add(slider("autosave_interval",
                        ExtrasConfig.ExtraSettings.AUTOSAVE_MIN_TICKS,
                        ExtrasConfig.ExtraSettings.AUTOSAVE_MAX_TICKS, 300,
                        value -> value == 0
                                ? TextComponent.translatable("options.off")
                                : TextComponent.literal(value / 20 + "s"),
                        (config, value) -> config.extra.autosaveInterval = value,
                        config -> config.extra.autosaveInterval, null, null, null));

        return builder.build();
    }

    // Options that do not bind to the Extras config

    // Vertical sync folding adaptive sync in with vanilla's on/off; values resolve at page build, since offering ADAPTIVE without driver tear control would silently do nothing
    private static OptionImpl<ExtrasConfig, ExtrasConfig.VerticalSync> verticalSync() {
        ExtrasConfig.VerticalSync[] available = AdaptiveSync.isSupported()
                ? ExtrasConfig.VerticalSync.values()
                : new ExtrasConfig.VerticalSync[] {
                        ExtrasConfig.VerticalSync.OFF, ExtrasConfig.VerticalSync.ON };

        return OptionImpl.createBuilder(ExtrasConfig.VerticalSync.class, STORAGE)
                .setId(option("vertical_sync", ExtrasConfig.VerticalSync.class))
                .setName(TextComponent.translatable(LANG + "vertical_sync.name"))
                .setTooltip(TextComponent.translatable(LANG + "vertical_sync.tooltip"))
                .setControl(opt -> new CyclingControl<>(opt, available, localizedNames(available)))
                .setBinding((config, value) -> AdaptiveSync.apply(value), config -> AdaptiveSync.current())
                .setImpact(OptionImpact.VARIES)
                .build();
    }

    // Vanilla's advanced tooltips, otherwise homeless but for F3+H; bound straight to the vanilla setting so the two cannot disagree
    private static OptionImpl<ExtrasConfig, Boolean> advancedItemTooltips() {
        return OptionImpl.createBuilder(boolean.class, STORAGE)
                .setId(option("advanced_item_tooltips", boolean.class))
                .setName(TextComponent.translatable(LANG + "advanced_item_tooltips.name"))
                .setTooltip(TextComponent.translatable(LANG + "advanced_item_tooltips.tooltip"))
                .setControl(TickBoxControl::new)
                .setBinding(
                        (config, value) -> {
                            Minecraft.getMinecraft().gameSettings.advancedItemTooltips = value;
                            Minecraft.getMinecraft().gameSettings.saveOptions();
                        },
                        config -> Minecraft.getMinecraft().gameSettings.advancedItemTooltips)
                .build();
    }

    // Per-class particle toggles

    // One toggle per discovered particle class grouped by owning mod; everything is runtime-discovered (see ParticleClassRegistry), so a scan failure costs only its own group
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
                        .setId(group("particles." + modId));

                // Vanilla particles drop the mod suffix since "(minecraft)" down the whole list says nothing; a modded id stays as the only marker of origin
                boolean showModId = !"minecraft".equals(modId);

                for (Map.Entry<String, String> classEntry : classes) {
                    String fullName = classEntry.getKey();
                    String simpleName = classEntry.getValue();
                    String label = showModId ? simpleName + " (" + modId + ")" : simpleName;

                    builder.add(OptionImpl.createBuilder(boolean.class, STORAGE)
                            .setId(option("particles.class." + fullName, boolean.class))
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

    // Builders

    // A texture-animation sub-switch: always asset-reloading, always gated on the master
    private static OptionImpl<ExtrasConfig, Boolean> animationToggle(
            String key, BooleanSupplier enabled,
            BiConsumer<ExtrasConfig, Boolean> setter, Function<ExtrasConfig, Boolean> getter) {
        return toggle(key, setter, getter, null, OptionFlag.REQUIRES_ASSET_RELOAD, enabled);
    }

    private static OptionImpl<ExtrasConfig, Boolean> toggle(
            String key,
            BiConsumer<ExtrasConfig, Boolean> setter, Function<ExtrasConfig, Boolean> getter,
            OptionImpact impact, OptionFlag flag, BooleanSupplier enabled) {
        OptionImpl.Builder<ExtrasConfig, Boolean> builder = OptionImpl.createBuilder(boolean.class, STORAGE)
                .setId(option(key, boolean.class))
                .setName(TextComponent.translatable(LANG + key + ".name"))
                .setTooltip(TextComponent.translatable(LANG + key + ".tooltip"))
                .setControl(TickBoxControl::new)
                .setBinding(setter, getter);

        if (impact != null) {
            builder.setImpact(impact);
        }
        if (flag != null) {
            builder.setFlags(flag);
        }
        if (enabled != null) {
            builder.setEnabledPredicate(enabled);
        }

        return builder.build();
    }

    private static OptionImpl<ExtrasConfig, Integer> slider(
            String key, int min, int max, int step, ControlValueFormatter formatter,
            BiConsumer<ExtrasConfig, Integer> setter, Function<ExtrasConfig, Integer> getter,
            OptionImpact impact, OptionFlag flag, BooleanSupplier enabled) {
        OptionImpl.Builder<ExtrasConfig, Integer> builder = OptionImpl.createBuilder(int.class, STORAGE)
                .setId(option(key, int.class))
                .setName(TextComponent.translatable(LANG + key + ".name"))
                .setTooltip(TextComponent.translatable(LANG + key + ".tooltip"))
                .setControl(opt -> new SliderControl(opt, min, max, step, formatter))
                .setBinding(setter, getter);

        if (impact != null) {
            builder.setImpact(impact);
        }
        if (flag != null) {
            builder.setFlags(flag);
        }
        if (enabled != null) {
            builder.setEnabledPredicate(enabled);
        }

        return builder.build();
    }

    private static <T extends Enum<T> & ExtrasConfig.Localized> OptionImpl<ExtrasConfig, T> cycling(
            String key, Class<T> type, T[] values,
            BiConsumer<ExtrasConfig, T> setter, Function<ExtrasConfig, T> getter,
            BooleanSupplier enabled) {
        OptionImpl.Builder<ExtrasConfig, T> builder = OptionImpl.createBuilder(type, STORAGE)
                .setId(option(key, type))
                .setName(TextComponent.translatable(LANG + key + ".name"))
                .setTooltip(TextComponent.translatable(LANG + key + ".tooltip"))
                .setControl(opt -> new CyclingControl<>(opt, values, localizedNames(values)))
                .setBinding(setter, getter);

        if (enabled != null) {
            builder.setEnabledPredicate(enabled);
        }

        return builder.build();
    }

    // Display names for an enum cycler, resolved through the lang keys
    private static TextComponent[] localizedNames(ExtrasConfig.Localized[] values) {
        TextComponent[] names = new TextComponent[values.length];
        for (int i = 0; i < values.length; i++) {
            names[i] = TextComponent.translatable(values[i].translationKey());
        }
        return names;
    }

    // Group id under the extras namespace
    private static OptionIdentifier<Void> group(String path) {
        return OptionIdentifier.create(MOD_ID, "extras/" + path);
    }

    // Option id under the extras namespace
    private static <T> OptionIdentifier<T> option(String path, Class<T> type) {
        return OptionIdentifier.create(MOD_ID, "extras/" + path, type);
    }
}
