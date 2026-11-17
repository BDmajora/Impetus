package com.bdmajora.extras.gui;

import com.bdmajora.extras.ExtrasConfig;
import com.bdmajora.extras.client.AdaptiveSync;
import com.bdmajora.impetus.api.options.OptionIdentifier;
import com.bdmajora.impetus.api.options.structure.Option;
import com.bdmajora.impetus.api.options.structure.OptionFlag;
import com.bdmajora.impetus.api.options.structure.OptionGroup;
import com.bdmajora.impetus.api.options.structure.OptionImpact;
import com.bdmajora.impetus.api.options.structure.OptionImpl;
import com.bdmajora.impetus.api.options.structure.OptionPage;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.impl.gui.ModuleOptions;
import com.google.common.collect.ImmutableList;
import net.minecraft.client.Minecraft;

import java.util.Locale;
import java.util.function.BooleanSupplier;

// Builder of the Extras options on the Details and Interface and Performance pages.
public final class ExtrasOptionPages {
    // Mod id of the options.
    private static final String MOD_ID = "extras";
    // Mod id of the pages.
    private static final String PAGE_MOD_ID = "impetus";
    // Prefix of the option language keys.
    private static final String LANG = "impetus.options.extras.";
    // Prefix of the group language keys.
    private static final String GROUPS = "impetus.options.extras.group.";

    // Option builders of the module.
    private static final ModuleOptions<ExtrasConfig> OPTIONS = new ModuleOptions<>(MOD_ID, "extras/", LANG, new ExtrasOptionsStorage());

    // Prevents instantiation
    private ExtrasOptionPages() {
    }

    // Returns the Details page with animations particles sky weather and world objects.
    public static OptionPage detailsPage() {
        return new OptionPage(OptionIdentifier.create(PAGE_MOD_ID, "details"),
                TextComponent.translatable("impetus.options.pages.details"),
                ImmutableList.of(animationsAndParticles(), sky(), weather(), world()));
    }

    // Returns the Interface page with the overlay tooltips notifications and camera groups.
    public static OptionPage interfacePage(Option<?>... notifications) {
        OptionGroup.Builder alerts = OptionGroup.createBuilder()
                .setId(OPTIONS.group("notifications"))
                .setName(groupName("notifications"))
                .add(OPTIONS.toggle("toasts.all", (config, value) -> config.extra.toasts = value,
                        config -> config.extra.toasts, null, null, null));
        for (Option<?> option : notifications) {
            alerts.add(option);
        }
        return new OptionPage(OptionIdentifier.create(PAGE_MOD_ID, "interface"),
                TextComponent.translatable("impetus.options.pages.interface"),
                ImmutableList.of(overlay(), tooltips(), alerts.build(), camera()));
    }

    // Returns the translated name of a group.
    private static TextComponent groupName(String id) {
        return TextComponent.translatable(GROUPS + id);
    }

    // Returns the group of the animation and particle switches.
    private static OptionGroup animationsAndParticles() {
        OptionImpl<ExtrasConfig, Boolean> particles = OPTIONS.toggle("particles.all",
                (config, value) -> config.particle.all = value,
                config -> config.particle.all, OptionImpact.HIGH, null, null);
        BooleanSupplier particlesOn = particles::getValue;

        return OptionGroup.createBuilder()
                .setId(OPTIONS.group("animations"))
                .setName(groupName("animations"))
                .add(OPTIONS.toggle("animations.all", (config, value) -> config.animation.all = value,
                        config -> config.animation.all, OptionImpact.MEDIUM, OptionFlag.REQUIRES_ASSET_RELOAD, null))
                .add(particles)
                .add(OPTIONS.toggle("particles.rain_splash", (config, value) -> config.particle.rainSplash = value,
                        config -> config.particle.rainSplash, null, null, particlesOn))
                .add(OPTIONS.toggle("particles.block_break", (config, value) -> config.particle.blockBreak = value,
                        config -> config.particle.blockBreak, null, null, particlesOn))
                .add(OPTIONS.toggle("particles.block_breaking", (config, value) -> config.particle.blockBreaking = value,
                        config -> config.particle.blockBreaking, null, null, particlesOn))
                .build();
    }

    // Returns the group of the sky switches.
    private static OptionGroup sky() {
        return OptionGroup.createBuilder()
                .setId(OPTIONS.group("sky"))
                .setName(groupName("sky"))
                .add(OPTIONS.toggle("details.sky", (config, value) -> config.detail.sky = value,
                        config -> config.detail.sky, null, OptionFlag.REQUIRES_RENDERER_RELOAD, null))
                .add(OPTIONS.toggle("details.stars", (config, value) -> config.detail.stars = value,
                        config -> config.detail.stars, null, OptionFlag.REQUIRES_RENDERER_RELOAD, null))
                .add(OPTIONS.toggle("details.sun", (config, value) -> config.detail.sun = value,
                        config -> config.detail.sun, null, null, null))
                .add(OPTIONS.toggle("details.moon", (config, value) -> config.detail.moon = value,
                        config -> config.detail.moon, null, null, null))
                .add(OPTIONS.toggle("details.sky_colors", (config, value) -> config.detail.skyColors = value,
                        config -> config.detail.skyColors, null, null, null))
                .add(OPTIONS.toggle("render.fog", (config, value) -> config.render.fog = value,
                        config -> config.render.fog, OptionImpact.LOW, null, null))
                .build();
    }

    // Returns the group of the weather and time switches.
    private static OptionGroup weather() {
        return OptionGroup.createBuilder()
                .setId(OPTIONS.group("weather_time"))
                .setName(groupName("weather_time"))
                .add(OPTIONS.toggle("details.rain_snow", (config, value) -> config.detail.rainSnow = value,
                        config -> config.detail.rainSnow, OptionImpact.LOW, null, null))
                .add(OPTIONS.cycling("time", ExtrasConfig.TimeOverride.class, ExtrasConfig.TimeOverride.values(),
                        (config, value) -> config.extra.timeOverride = value,
                        config -> config.extra.timeOverride, null))
                .add(OPTIONS.cycling("weather", ExtrasConfig.WeatherOverride.class, ExtrasConfig.WeatherOverride.values(),
                        (config, value) -> config.extra.weatherOverride = value,
                        config -> config.extra.weatherOverride, null))
                .build();
    }

    // Returns the group of the world object switches.
    private static OptionGroup world() {
        return OptionGroup.createBuilder()
                .setId(OPTIONS.group("world"))
                .setName(groupName("world"))
                .add(OPTIONS.toggle("details.biome_colors", (config, value) -> config.detail.biomeColors = value,
                        config -> config.detail.biomeColors, OptionImpact.MEDIUM, OptionFlag.REQUIRES_RENDERER_RELOAD, null))
                .add(OPTIONS.toggle("render.item_frames", (config, value) -> config.render.itemFrames = value,
                        config -> config.render.itemFrames, OptionImpact.MEDIUM, null, null))
                .add(OPTIONS.toggle("render.armor_stands", (config, value) -> config.render.armorStands = value,
                        config -> config.render.armorStands, OptionImpact.LOW, null, null))
                .add(OPTIONS.toggle("render.paintings", (config, value) -> config.render.paintings = value,
                        config -> config.render.paintings, null, null, null))
                .add(OPTIONS.toggle("render.beacons", (config, value) -> config.render.beacons = value,
                        config -> config.render.beacons, OptionImpact.LOW, null, null))
                .add(OPTIONS.toggle("render.dropped_items", (config, value) -> config.render.droppedItemsFancy = value,
                        config -> config.render.droppedItemsFancy, OptionImpact.MEDIUM, null, null))
                .add(OPTIONS.toggle("render.player_name_tag", (config, value) -> config.render.playerNameTag = value,
                        config -> config.render.playerNameTag, null, null, null))
                .add(OPTIONS.toggle("details.capes", (config, value) -> config.detail.showCapes = value,
                        config -> config.detail.showCapes, null, null, null))
                .build();
    }

    // Returns the group of the on-screen overlay switches.
    private static OptionGroup overlay() {
        return OptionGroup.createBuilder()
                .setId(OPTIONS.group("overlay"))
                .setName(groupName("overlay"))
                .add(OPTIONS.toggle("overlay.fps", (config, value) -> config.extra.showFps = value,
                        config -> config.extra.showFps, null, null, null))
                .add(OPTIONS.toggle("overlay.coords", (config, value) -> config.extra.showCoords = value,
                        config -> config.extra.showCoords, null, null, null))
                .add(OPTIONS.cycling("overlay.corner", ExtrasConfig.OverlayCorner.class, ExtrasConfig.OverlayCorner.values(),
                        (config, value) -> config.extra.overlayCorner = value,
                        config -> config.extra.overlayCorner, null))
                .add(OPTIONS.cycling("overlay.text_contrast", ExtrasConfig.TextContrast.class, ExtrasConfig.TextContrast.values(),
                        (config, value) -> config.extra.textContrast = value,
                        config -> config.extra.textContrast, null))
                .add(OPTIONS.toggle("steady_debug_hud", (config, value) -> config.extra.steadyDebugHud = value,
                        config -> config.extra.steadyDebugHud, OptionImpact.LOW, null, null))
                .build();
    }

    // Returns the group of the tooltip switches.
    private static OptionGroup tooltips() {
        return OptionGroup.createBuilder()
                .setId(OPTIONS.group("tooltips"))
                .setName(groupName("tooltips"))
                .add(OPTIONS.toggle("advanced_item_tooltips",
                        (config, value) -> {
                            Minecraft.getMinecraft().gameSettings.advancedItemTooltips = value;
                            Minecraft.getMinecraft().gameSettings.saveOptions();
                        },
                        config -> Minecraft.getMinecraft().gameSettings.advancedItemTooltips,
                        null, null, null))
                .add(OPTIONS.toggle("mod_name_tooltip", (config, value) -> config.extra.modNameTooltip = value,
                        config -> config.extra.modNameTooltip, null, null, null))
                .add(OPTIONS.toggle("details.held_item_tooltips", (config, value) -> config.detail.heldItemTooltips = value,
                        config -> config.detail.heldItemTooltips, null, null, null))
                .build();
    }

    // Returns the group of the zoom and camera switches.
    private static OptionGroup camera() {
        OptionImpl<ExtrasConfig, Boolean> zoom = OPTIONS.toggle("zoom.enabled",
                (config, value) -> config.zoom.enabled = value,
                config -> config.zoom.enabled, null, null, null);
        BooleanSupplier zoomOn = zoom::getValue;

        return OptionGroup.createBuilder()
                .setId(OPTIONS.group("camera"))
                .setName(groupName("camera"))
                .add(zoom)
                .add(OPTIONS.slider("zoom.level", ExtrasConfig.ZoomSettings.LEVEL_MIN, ExtrasConfig.ZoomSettings.LEVEL_MAX, 1,
                        value -> TextComponent.literal(String.format(Locale.ROOT, "%.2fx",
                                (float) ExtrasConfig.ZoomSettings.LEVEL_SCALE / value)),
                        (config, value) -> config.zoom.level = value,
                        config -> config.zoom.level, null, null, zoomOn))
                .add(OPTIONS.toggle("zoom.smooth_camera", (config, value) -> config.zoom.smoothCamera = value,
                        config -> config.zoom.smoothCamera, null, null, zoomOn))
                .add(OPTIONS.toggle("zoom.hide_hand", (config, value) -> config.zoom.hideHand = value,
                        config -> config.zoom.hideHand, null, null, zoomOn))
                .add(OPTIONS.toggle("panini", (config, value) -> config.extra.paniniProjection = value,
                        config -> config.extra.paniniProjection, OptionImpact.MEDIUM, null, null))
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

    // Returns the switch that hides entities and block entities behind walls.
    public static OptionImpl<ExtrasConfig, Boolean> occlusionCulling() {
        return OPTIONS.toggle("occlusion.enabled", (config, value) -> config.occlusion.enabled = value,
                config -> config.occlusion.enabled, OptionImpact.HIGH, null, null);
    }

    // Returns the leaf culling mode.
    public static OptionImpl<ExtrasConfig, ExtrasConfig.LeafCulling> leafCulling() {
        return OPTIONS.cycling("leaves.culling_mode", ExtrasConfig.LeafCulling.class, ExtrasConfig.LeafCulling.values(),
                (config, value) -> config.leaves.cullingMode = value, config -> config.leaves.cullingMode,
                OptionImpact.LOW, OptionFlag.REQUIRES_RENDERER_RELOAD, null);
    }

    // Returns the render budget choice of Off or a profile.
    public static OptionImpl<ExtrasConfig, ExtrasConfig.RenderBudgetMode> renderBudget() {
        return OPTIONS.cycling("budget.mode", ExtrasConfig.RenderBudgetMode.class, ExtrasConfig.RenderBudgetMode.values(),
                (config, value) -> value.applyTo(config.renderBudget), config -> ExtrasConfig.RenderBudgetMode.of(config.renderBudget),
                OptionImpact.VARIES, null, null);
    }

    // Returns the switch of the GPU booster.
    public static OptionImpl<ExtrasConfig, Boolean> gpuBooster() {
        return OPTIONS.toggle("booster.enabled", (config, value) -> config.gpuBooster.enabled = value,
                config -> config.gpuBooster.enabled, OptionImpact.VARIES, null, null);
    }

    // Returns the switch of parallel server ticking.
    public static OptionImpl<ExtrasConfig, Boolean> parallelTicking() {
        return OPTIONS.toggle("parallel.enabled", (config, value) -> config.async.enabled = value,
                config -> config.async.enabled, OptionImpact.HIGH, OptionFlag.REQUIRES_GAME_RESTART, null);
    }

    // Returns the switch of the baked block entity models.
    public static OptionImpl<ExtrasConfig, Boolean> bakedBlockEntities() {
        return OPTIONS.toggle("baked.enabled", (config, value) -> config.bakedEntities.enabled = value,
                config -> config.bakedEntities.enabled, OptionImpact.HIGH, OptionFlag.REQUIRES_RENDERER_RELOAD, null);
    }
}
