package com.bdmajora.impetus.impl.gui;

import com.google.common.collect.ImmutableList;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.client.settings.GameSettings;
import com.bdmajora.impetus.api.options.control.ControlValueFormatter;
import com.bdmajora.impetus.api.options.control.CyclingControl;
import com.bdmajora.impetus.api.options.control.SliderControl;
import com.bdmajora.impetus.api.options.control.TickBoxControl;
import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.impl.platform.WindowModes;
import com.bdmajora.impetus.ImpetusVintage;
import com.bdmajora.impetus.api.options.structure.OptionFlag;
import com.bdmajora.impetus.api.options.structure.OptionGroup;
import com.bdmajora.impetus.api.options.structure.OptionImpact;
import com.bdmajora.impetus.api.options.structure.OptionImpl;
import com.bdmajora.impetus.api.options.structure.OptionPage;
import com.bdmajora.impetus.api.options.structure.OptionStorage;
import com.bdmajora.impetus.api.options.structure.StandardOptions;
import com.bdmajora.impetus.impl.compat.modernui.MuiGuiScaleHook;
import com.bdmajora.extras.gui.ExtrasOptionPages;
import com.bdmajora.coarctatio.gui.CoarctatioOptionPages;
import com.bdmajora.equilibrium.gui.EquilibriumOptionPages;
import com.bdmajora.fulgor.gui.FulgorOptionPages;
import com.bdmajora.impetus.api.options.OptionIdentifier;
import com.bdmajora.impetus.engine.impl.gui.options.CommonOptionPages;

import java.util.ArrayList;
import java.util.List;

// Builder of the Impetus pages of the video options screen.
public class ImpetusGameOptionPages {
    // Mod id of the Impetus groups.
    private static final String IMPETUS = "impetus";
    // Sodium opts.
    private static final ImpetusGameOptions sodiumOpts = ImpetusVintage.options();
    // Storage of the vanilla settings.
    private static final MinecraftOptionsStorage vanillaOpts = new MinecraftOptionsStorage();

    // Applies the leaves quality.
    private static void applyLeavesQuality(boolean seeThrough) {
        Blocks.LEAVES.setGraphicsLevel(seeThrough);
        Blocks.LEAVES2.setGraphicsLevel(seeThrough);
    }

    // Returns the general page.
    public static OptionPage general() {
        List<OptionGroup> groups = new ArrayList<>();

        groups.add(OptionGroup.createBuilder()
                .setId(StandardOptions.Group.RENDERING)
                .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                        .setId(StandardOptions.Option.RENDER_DISTANCE.cast())
                        .setName(TextComponent.translatable("options.renderDistance"))
                        .setTooltip(TextComponent.translatable("impetus.options.view_distance.tooltip"))
                        .setControl(option -> new SliderControl(option, 2, 32, 1, ControlValueFormatter.translateVariable("options.chunks")))
                        .setBinding((options, value) -> options.renderDistanceChunks = value, options -> options.renderDistanceChunks)
                        .setImpact(OptionImpact.HIGH)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .add(OptionImpl.createBuilder(int.class, sodiumOpts)
                        .setId(StandardOptions.Option.SIMULATION_DISTANCE.cast())
                        .setName(TextComponent.translatable("options.simulationDistance"))
                        .setTooltip(TextComponent.translatable("impetus.options.simulation_distance.tooltip"))
                        .setControl(option -> new SliderControl(option, ImpetusGameOptions.MIN_SIMULATION_DISTANCE,
                                ImpetusGameOptions.MAX_SIMULATION_DISTANCE, 1, ControlValueFormatter.translateVariable("options.chunks")))
                        .setBinding(ImpetusGameOptions::setSimulationDistance, ImpetusGameOptions::getSimulationDistance)
                        .setEnabledPredicate(() -> {
                            Minecraft client = Minecraft.getMinecraft();
                            return client.world == null || client.isIntegratedServerRunning();
                        })
                        .setImpact(OptionImpact.HIGH)
                        .build())
                .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                        .setId(StandardOptions.Option.BRIGHTNESS.cast())
                        .setName(TextComponent.translatable("options.gamma"))
                        .setTooltip(TextComponent.translatable("impetus.options.brightness.tooltip"))
                        .setControl(opt -> new SliderControl(opt, 0, 100, 1, ControlValueFormatter.brightness()))
                        .setBinding(
                                (opts, value) -> opts.setOptionFloatValue(GameSettings.Options.GAMMA, value * 0.01f),
                                opts -> Math.round(opts.gammaSetting * 100.0f))
                        .build())
                .build());

        groups.add(OptionGroup.createBuilder()
                .setId(StandardOptions.Group.WINDOW)
                .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                        .setId(StandardOptions.Option.GUI_SCALE.cast())
                        .setName(TextComponent.translatable("options.guiScale"))
                        .setTooltip(TextComponent.translatable("impetus.options.gui_scale.tooltip"))
                        .setControl(option -> new SliderControl(option, 0, MuiGuiScaleHook.getMaxGuiScale(), 1, ControlValueFormatter.guiScale()))
                        .setBinding((opts, value) -> {
                            opts.guiScale = value;
                            Minecraft mc = Minecraft.getMinecraft();
                            mc.resize(mc.displayWidth, mc.displayHeight);
                        }, opts -> opts.guiScale)
                        .build())
                .add(OptionImpl.createBuilder(ImpetusGameOptions.FullscreenMode.class, sodiumOpts)
                        .setId(StandardOptions.Option.FULLSCREEN.cast())
                        .setName(TextComponent.translatable("impetus.options.fullscreen_mode.name"))
                        .setTooltip(TextComponent.translatable("impetus.options.fullscreen.tooltip"))
                        .setControl(option -> new CyclingControl<>(option, ImpetusGameOptions.FullscreenMode.class))
                        .setImpact(OptionImpact.HIGH)
                        .setBinding((opts, value) -> opts.fullscreenMode = WindowModes.apply(Minecraft.getMinecraft(), value), opts -> opts.fullscreenMode)
                        .build())
                .add(OptionImpl.createBuilder(int.class, sodiumOpts)
                        .setId(StandardOptions.Option.FULLSCREEN_RESOLUTION.cast())
                        .setName(TextComponent.translatable("options.fullscreen.resolution"))
                        .setTooltip(TextComponent.translatable("impetus.options.fullscreen.resolution.tooltip"))
                        .setControl(option -> new SliderControl(option, 0, Math.max(1, FullscreenResolutions.count() - 1), 1,
                                value -> TextComponent.literal(FullscreenResolutions.label(value))))
                        .setBinding((opts, value) -> {
                            opts.fullscreenResolution = value;
                            FullscreenResolutions.apply(value);
                        }, opts -> opts.fullscreenResolution)
                        .build())
                .add(ExtrasOptionPages.verticalSync())
                .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                        .setId(StandardOptions.Option.MAX_FRAMERATE.cast())
                        .setName(TextComponent.translatable("options.framerateLimit"))
                        .setTooltip(TextComponent.translatable("impetus.options.fps_limit.tooltip"))
                        .setControl(option -> new SliderControl(option, 10, 260, 10, ControlValueFormatter.fpsLimit()))
                        .setBinding((opts, value) -> opts.limitFramerate = value, opts -> opts.limitFramerate <= 0 || opts.limitFramerate > 260 ? 260 : opts.limitFramerate)
                        .build())
                .add(CommonOptionPages.inactivityFpsLimit(sodiumOpts))
                .build());

        groups.add(OptionGroup.createBuilder()
                .setId(StandardOptions.Group.INDICATORS)
                .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                        .setId(StandardOptions.Option.ATTACK_INDICATOR.cast())
                        .setName(TextComponent.translatable("options.attackIndicator"))
                        .setTooltip(TextComponent.translatable("impetus.options.attack_indicator.tooltip"))
                        .setControl(opts -> new CyclingControl<>(opts, new Integer[] { 0, 1, 2 }, new TextComponent[] {
                                TextComponent.translatable("options.off"),
                                TextComponent.translatable("options.attack.crosshair"),
                                TextComponent.translatable("options.attack.hotbar") }))
                        .setBinding((opts, value) -> opts.attackIndicator = value, (opts) -> opts.attackIndicator)
                        .build())
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setId(StandardOptions.Option.AUTOSAVE_INDICATOR.cast())
                        .setName(TextComponent.translatable("options.autosaveIndicator"))
                        .setTooltip(TextComponent.translatable("impetus.options.autosave_indicator.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> opts.notifications.autosaveIndicator = value, opts -> opts.notifications.autosaveIndicator)
                        .build())
                .build());

        return new OptionPage(StandardOptions.Pages.GENERAL, TextComponent.translatable("impetus.options.pages.general"), ImmutableList.copyOf(groups));
    }

    // Returns the quality page.
    public static OptionPage quality() {
        List<OptionGroup> groups = new ArrayList<>();

        groups.add(OptionGroup.createBuilder()
                .setId(StandardOptions.Group.GRAPHICS)
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setId(StandardOptions.Option.IMPROVED_TRANSPARENCY.cast())
                        .setName(TextComponent.translatable("options.improvedTransparency"))
                        .setTooltip(TextComponent.translatable("impetus.options.improved_transparency.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> opts.performance.useTranslucentFaceSorting = value,
                                opts -> opts.performance.useTranslucentFaceSorting)
                        .setImpact(OptionImpact.HIGH)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .build());

        groups.add(OptionGroup.createBuilder()
                .setId(StandardOptions.Group.DETAILS)
                .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                        .setId(StandardOptions.Option.CLOUDS.cast())
                        .setName(TextComponent.translatable("options.renderClouds"))
                        .setTooltip(TextComponent.translatable("impetus.options.clouds_quality.tooltip"))
                        .setControl(option -> new CyclingControl<>(option, new Integer[] { 0, 1, 2 }, new TextComponent[] {
                                TextComponent.translatable("options.off"),
                                TextComponent.translatable("options.clouds.fast"),
                                TextComponent.translatable("options.clouds.fancy") }))
                        .setBinding((opts, value) -> opts.clouds = value, opts -> opts.clouds)
                        .setImpact(OptionImpact.LOW)
                        .build())
                .add(OptionImpl.createBuilder(int.class, sodiumOpts)
                        .setId(StandardOptions.Option.CLOUD_DISTANCE.cast())
                        .setName(TextComponent.translatable("options.renderCloudsDistance"))
                        .setTooltip(TextComponent.translatable("impetus.options.cloud_distance.tooltip"))
                        .setControl(option -> new SliderControl(option, 2, 128, 2, ControlValueFormatter.translateVariable("options.chunks")))
                        .setBinding((opts, value) -> opts.quality.cloudDistance = value, opts -> opts.quality.cloudDistance)
                        .setImpact(OptionImpact.LOW)
                        .build())
                .add(OptionImpl.createBuilder(int.class, sodiumOpts)
                        .setId(StandardOptions.Option.WEATHER.cast())
                        .setName(TextComponent.translatable("options.weatherRadius"))
                        .setTooltip(TextComponent.translatable("impetus.options.weather_effect_radius.tooltip"))
                        .setControl(option -> new SliderControl(option, 3, 10, 1, ControlValueFormatter.number()))
                        .setBinding((opts, value) -> opts.quality.weatherEffectRadius = value, opts -> opts.quality.weatherEffectRadius)
                        .setImpact(OptionImpact.LOW)
                        .build())
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setId(StandardOptions.Option.SEE_THROUGH_LEAVES.cast())
                        .setName(TextComponent.translatable("options.cutoutLeaves"))
                        .setTooltip(TextComponent.translatable("impetus.options.see_through_leaves.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> {
                            opts.quality.leavesQuality = value ? ImpetusGameOptions.GraphicsQuality.FANCY : ImpetusGameOptions.GraphicsQuality.FAST;
                            applyLeavesQuality(value);
                        }, opts -> opts.quality.leavesQuality.isFancy(Minecraft.getMinecraft().gameSettings.fancyGraphics))
                        .setImpact(OptionImpact.MEDIUM)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                        .setId(StandardOptions.Option.PARTICLES.cast())
                        .setName(TextComponent.translatable("options.particles"))
                        .setTooltip(TextComponent.translatable("impetus.options.particle_quality.tooltip"))
                        .setControl(option -> new CyclingControl<>(option, new Integer[] { 0, 1, 2 }, new TextComponent[] {
                                TextComponent.translatable("options.particles.all"),
                                TextComponent.translatable("options.particles.decreased"),
                                TextComponent.translatable("options.particles.minimal") }))
                        .setBinding((opts, value) -> opts.particleSetting = value, (opts) -> opts.particleSetting)
                        .setImpact(OptionImpact.MEDIUM)
                        .build())
                .add(OptionImpl.createBuilder(boolean.class, vanillaOpts)
                        .setId(StandardOptions.Option.SMOOTH_LIGHT.cast())
                        .setName(TextComponent.translatable("options.ao"))
                        .setTooltip(TextComponent.translatable("impetus.options.smooth_lighting.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> opts.ambientOcclusion = value ? 2 : 0, opts -> opts.ambientOcclusion > 0)
                        .setImpact(OptionImpact.LOW)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .add(OptionImpl.createBuilder(int.class, sodiumOpts)
                        .setId(StandardOptions.Option.BIOME_BLEND.cast())
                        .setName(TextComponent.translatable("impetus.options.biomeBlendRadius"))
                        .setTooltip(TextComponent.translatable("impetus.options.biome_blend.tooltip"))
                        .setControl(option -> new SliderControl(option, 0, 7, 1, ControlValueFormatter.biomeBlend()))
                        .setBinding((opts, value) -> opts.quality.legacyBiomeBlendRadius = value, opts -> opts.quality.legacyBiomeBlendRadius)
                        .setImpact(OptionImpact.LOW)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .add(OptionImpl.createBuilder(int.class, sodiumOpts)
                        .setId(StandardOptions.Option.ENTITY_DISTANCE.cast())
                        .setName(TextComponent.translatable("options.entityDistanceScaling"))
                        .setTooltip(TextComponent.translatable("impetus.options.entity_distance.tooltip"))
                        .setControl(option -> new SliderControl(option, 50, 500, 25, ControlValueFormatter.percentage()))
                        .setBinding((opts, value) -> opts.quality.entityDistance = value, opts -> opts.quality.entityDistance)
                        .setImpact(OptionImpact.MEDIUM)
                        .build())
                .add(OptionImpl.createBuilder(boolean.class, vanillaOpts)
                        .setId(StandardOptions.Option.ENTITY_SHADOWS.cast())
                        .setName(TextComponent.translatable("options.entityShadows"))
                        .setTooltip(TextComponent.translatable("impetus.options.entity_shadows.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> opts.entityShadows = value, opts -> opts.entityShadows)
                        .setImpact(OptionImpact.LOW)
                        .build())
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setId(StandardOptions.Option.VIGNETTE.cast())
                        .setName(TextComponent.translatable("options.vignette"))
                        .setTooltip(TextComponent.translatable("impetus.options.vignette.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> opts.quality.enableVignette = value, opts -> opts.quality.enableVignette)
                        .setImpact(OptionImpact.LOW)
                        .build())
                .add(OptionImpl.createBuilder(int.class, sodiumOpts)
                        .setId(StandardOptions.Option.CHUNK_FADE_IN_DURATION.cast())
                        .setName(TextComponent.translatable("options.chunkFade"))
                        .setTooltip(TextComponent.translatable("impetus.options.chunk_fade_in_duration.tooltip"))
                        .setControl(o -> new SliderControl(o, 0, 2000, 50, ControlValueFormatter.translateVariable("impetus.options.chunk_fade_in_duration.value")))
                        .setImpact(OptionImpact.LOW)
                        .setBinding((opts, value) -> opts.quality.chunkFadeInDuration = value, opts -> opts.quality.chunkFadeInDuration)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .build());

        groups.add(OptionGroup.createBuilder()
                .setId(StandardOptions.Group.MIPMAPS)
                .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                        .setId(StandardOptions.Option.MIPMAP_LEVEL.cast())
                        .setName(TextComponent.translatable("options.mipmapLevels"))
                        .setTooltip(TextComponent.translatable("impetus.options.mipmap_levels.tooltip"))
                        .setControl(option -> new SliderControl(option, 0, 4, 1, ControlValueFormatter.multiplier()))
                        .setBinding((opts, value) -> opts.mipmapLevels = value, opts -> opts.mipmapLevels)
                        .setImpact(OptionImpact.MEDIUM)
                        .setFlags(OptionFlag.REQUIRES_ASSET_RELOAD)
                        .build())
                .build());

        groups.add(OptionGroup.createBuilder()
                .setId(StandardOptions.Group.FILTERING)
                .add(OptionImpl.createBuilder(ImpetusGameOptions.PixelFilteringMode.class, sodiumOpts)
                        .setId(StandardOptions.Option.TEXEL_INTERPOLATION.cast())
                        .setName(TextComponent.translatable("impetus.options.pixel_filtering.name"))
                        .setTooltip(TextComponent.translatable("impetus.options.pixel_filtering.tooltip"))
                        .setControl(option -> new CyclingControl<>(option, ImpetusGameOptions.PixelFilteringMode.class))
                        .setBinding((opts, value) -> opts.quality.pixelFiltering = value, opts -> opts.quality.pixelFiltering)
                        .setImpact(OptionImpact.MEDIUM)
                        .setFlags(OptionFlag.REQUIRES_ASSET_RELOAD)
                        .build())
                .build());

        groups.add(OptionGroup.createBuilder()
                .setId(StandardOptions.Group.FLUIDS)
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setId(StandardOptions.Option.FLUID_CULLING.cast())
                        .setName(TextComponent.translatable("impetus.options.hidden_fluid_culling.name"))
                        .setTooltip(TextComponent.translatable("impetus.options.hidden_fluid_culling.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> opts.quality.hiddenFluidCulling = value, opts -> opts.quality.hiddenFluidCulling)
                        .setImpact(OptionImpact.MEDIUM)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setId(StandardOptions.Option.FLUID_SHAPING.cast())
                        .setName(TextComponent.translatable("impetus.options.improved_fluid_shaping.name"))
                        .setTooltip(TextComponent.translatable("impetus.options.improved_fluid_shaping.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> opts.quality.improvedFluidShaping = value, opts -> opts.quality.improvedFluidShaping)
                        .setImpact(OptionImpact.LOW)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setId(StandardOptions.Option.ENTITY_SORTING.cast())
                        .setName(TextComponent.translatable("impetus.options.closest_point_entity_sort.name"))
                        .setTooltip(TextComponent.translatable("impetus.options.closest_point_entity_sort.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> opts.quality.closestPointEntitySort = value, opts -> opts.quality.closestPointEntitySort)
                        .setImpact(OptionImpact.MEDIUM)
                        .build())
                .build());

        return new OptionPage(StandardOptions.Pages.QUALITY, TextComponent.translatable("impetus.options.pages.quality"), ImmutableList.copyOf(groups));
    }

    // Returns the performance page with one switch for each engine.
    public static OptionPage performance() {
        List<OptionGroup> groups = new ArrayList<>();

        groups.add(OptionGroup.createBuilder()
                .setId(OptionIdentifier.create(IMPETUS, "renderer"))
                .setName(TextComponent.translatable("impetus.options.group.renderer"))
                .add(CommonOptionPages.graphicsApi(sodiumOpts))
                .add(ExtrasOptionPages.renderBudget())
                .add(CommonOptionPages.chunkUpdateThreads(sodiumOpts))
                .add(CommonOptionPages.deferChunkUpdates(sodiumOpts))
                .build());

        var occlusion = ExtrasOptionPages.occlusionCulling();
        groups.add(OptionGroup.createBuilder()
                .setId(OptionIdentifier.create(IMPETUS, "culling"))
                .setName(TextComponent.translatable("impetus.options.group.culling"))
                .add(CommonOptionPages.entityCulling(sodiumOpts))
                .add(occlusion)
                .add(ExtrasOptionPages.pauseHiddenArmorStands(occlusion::getValue))
                .add(ExtrasOptionPages.leafCulling())
                .add(CommonOptionPages.animateVisibleTextures(sodiumOpts))
                .build());

        groups.add(OptionGroup.createBuilder()
                .setId(OptionIdentifier.create(IMPETUS, "engines"))
                .setName(TextComponent.translatable("impetus.options.group.engines"))
                .add(FulgorOptionPages.engine())
                .add(ExtrasOptionPages.bakedBlockEntities())
                .add(ExtrasOptionPages.gpuBooster())
                .add(ExtrasOptionPages.parallelTicking())
                .add(CoarctatioOptionPages.memory())
                .add(EquilibriumOptionPages.gameLogic())
                .build());

        return new OptionPage(StandardOptions.Pages.PERFORMANCE, TextComponent.translatable("impetus.options.pages.performance"), ImmutableList.copyOf(groups));
    }

    // Returns the interface page with the Impetus notification switches added.
    public static OptionPage interfacePage() {
        return ExtrasOptionPages.interfacePage(CommonOptionPages.showToasts(sodiumOpts), CommonOptionPages.incompatiblePackWarnings(sodiumOpts));
    }

    // Returns the vanilla opts.
    public static OptionStorage<GameSettings> getVanillaOpts() {
        return vanillaOpts;
    }

    // Returns the sodium opts.
    public static OptionStorage<ImpetusGameOptions> getSodiumOpts() {
        return sodiumOpts;
    }
}
