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
import org.lwjgl.opengl.Display;
import com.bdmajora.impetus.ImpetusVintage;
import com.bdmajora.impetus.api.options.structure.OptionFlag;
import com.bdmajora.impetus.api.options.structure.OptionGroup;
import com.bdmajora.impetus.api.options.structure.OptionImpact;
import com.bdmajora.impetus.api.options.structure.OptionImpl;
import com.bdmajora.impetus.api.options.structure.OptionPage;
import com.bdmajora.impetus.api.options.structure.OptionStorage;
import com.bdmajora.impetus.api.options.structure.StandardOptions;
import com.bdmajora.impetus.impl.compat.fluidlogged.FluidloggedCompat;
import com.bdmajora.impetus.impl.compat.modernui.MuiGuiScaleHook;

import java.util.ArrayList;
import java.util.List;

// General and Quality pages laid out to match Sodium 0.9.1 exactly; every option is real and backed by vanilla GameSettings or the engine, no placeholders
public class ImpetusGameOptionPages {
    private static final ImpetusGameOptions sodiumOpts = ImpetusVintage.options();
    private static final MinecraftOptionsStorage vanillaOpts = new MinecraftOptionsStorage();
    private static final MenuState menuState = new MenuState();
    private static final OptionStorage<MenuState> menuOpts = () -> menuState;

    // Pushes the leaves setting to every leaf block, which caches it
    private static void applyLeavesQuality(boolean seeThrough) {
        Blocks.LEAVES.setGraphicsLevel(seeThrough);
        Blocks.LEAVES2.setGraphicsLevel(seeThrough);
    }

    // Render distance, brightness, GUI scale and the like
    public static OptionPage general() {
        List<OptionGroup> groups = new ArrayList<>();

        // Group 1: render/simulation distance + brightness
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
                .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                        .setId(StandardOptions.Option.SIMULATION_DISTANCE.cast())
                        .setName(TextComponent.translatable("options.simulationDistance"))
                        .setTooltip(TextComponent.translatable("impetus.options.simulation_distance.tooltip"))
                        .setControl(option -> new SliderControl(option, 5, 32, 1, ControlValueFormatter.translateVariable("options.chunks")))
                        // 1.12.2 unifies simulation and render distance; keep them in lockstep.
                        .setBinding((options, value) -> options.renderDistanceChunks = value, options -> options.renderDistanceChunks)
                        .setImpact(OptionImpact.HIGH)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
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

        // Group 2: window/display
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
                        .setBinding((opts, value) -> {
                            opts.fullscreenMode = value;
                            Minecraft client = Minecraft.getMinecraft();
                            boolean wantFullscreen = value.isFullscreen();
                            client.gameSettings.fullScreen = wantFullscreen;
                            if (client.isFullScreen() != wantFullscreen) {
                                client.toggleFullscreen();
                                // The client might not be able to enter full-screen mode.
                                opts.fullscreenMode = client.isFullScreen() ? value : ImpetusGameOptions.FullscreenMode.OFF;
                            }
                        }, opts -> opts.fullscreenMode)
                        .build())
                .add(OptionImpl.createBuilder(int.class, sodiumOpts)
                        .setId(StandardOptions.Option.FULLSCREEN_RESOLUTION.cast())
                        .setName(TextComponent.translatable("options.fullscreen.resolution"))
                        .setTooltip(TextComponent.translatable("impetus.options.fullscreen.resolution.tooltip"))
                        .setControl(option -> new SliderControl(option, 0, Math.max(0, FullscreenResolutions.count() - 1), 1,
                                value -> TextComponent.literal(FullscreenResolutions.label(value))))
                        .setBinding((opts, value) -> {
                            opts.fullscreenResolution = value;
                            FullscreenResolutions.apply(value);
                        }, opts -> opts.fullscreenResolution)
                        .build())
                .add(OptionImpl.createBuilder(boolean.class, vanillaOpts)
                        .setId(StandardOptions.Option.VSYNC.cast())
                        .setName(TextComponent.translatable("options.vsync"))
                        .setTooltip(TextComponent.translatable("impetus.options.v_sync.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> {
                            opts.enableVsync = value;
                            Display.setVSyncEnabled(opts.enableVsync);
                        }, opts -> opts.enableVsync)
                        .setImpact(OptionImpact.VARIES)
                        .build())
                .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                        .setId(StandardOptions.Option.MAX_FRAMERATE.cast())
                        .setName(TextComponent.translatable("options.framerateLimit"))
                        .setTooltip(TextComponent.translatable("impetus.options.fps_limit.tooltip"))
                        .setControl(option -> new SliderControl(option, 10, 260, 10, ControlValueFormatter.fpsLimit()))
                        .setBinding((opts, value) -> opts.limitFramerate = value, opts -> opts.limitFramerate)
                        .build())
                .build());

        // Group 3: indicators
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

        // Group 4: graphics backend
        groups.add(OptionGroup.createBuilder()
                .setId(StandardOptions.Group.GRAPHICS)
                .add(OptionImpl.createBuilder(String.class, menuOpts)
                        .setId(StandardOptions.Option.GRAPHICS_API.cast())
                        .setName(TextComponent.translatable("options.graphicsApi"))
                        .setTooltip(TextComponent.translatable("impetus.options.graphics_api.tooltip"))
                        .setControl(o -> new CyclingControl<>(o, new String[] { "OpenGL" }, new TextComponent[] { TextComponent.literal("OpenGL") }))
                        .setBinding((state, value) -> { }, state -> "OpenGL")
                        .build())
                .build());

        return new OptionPage(StandardOptions.Pages.GENERAL, TextComponent.translatable("impetus.options.pages.general"), ImmutableList.copyOf(groups));
    }

    // Graphics, clouds, weather, particles, leaves and similar fidelity switches
    public static OptionPage quality() {
        List<OptionGroup> groups = new ArrayList<>();

        // Group 1: improved transparency
        groups.add(OptionGroup.createBuilder()
                .setId(StandardOptions.Group.GRAPHICS)
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setId(StandardOptions.Option.IMPROVED_TRANSPARENCY.cast())
                        .setName(TextComponent.translatable("options.improvedTransparency"))
                        .setTooltip(TextComponent.translatable("impetus.options.improved_transparency.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> {
                            opts.quality.improvedTransparency = value;
                            // Improved transparency requires per-face translucency sorting to be active.
                            if (value) {
                                opts.performance.useTranslucentFaceSorting = true;
                            }
                        }, opts -> opts.quality.improvedTransparency)
                        .setImpact(OptionImpact.HIGH)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .build());

        // Group 2: the big "details" group (clouds .. chunk fade)
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
                        .setName(TextComponent.translatable("options.biomeBlendRadius"))
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

        // Group 3: mipmaps
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

        // Group 4, texture filtering; minification is deliberately not offered since the atlas has no sprite borders, so anisotropic and mipmapped minification paint the block grid on distant terrain (see BlockAtlasFiltering)
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

        // Group 5: fluids + entity sort
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
                // Needs Fluidlogged API on the client to render the guessed fluid at all
                .addConditionally(FluidloggedCompat.IS_LOADED, () -> OptionImpl.createBuilder(ImpetusGameOptions.FluidloggingGuess.class, sodiumOpts)
                        .setId(StandardOptions.Option.INFERRED_FLUIDLOGGING.cast())
                        .setName(TextComponent.translatable("impetus.options.inferred_fluidlogging.name"))
                        .setTooltip(TextComponent.translatable("impetus.options.inferred_fluidlogging.tooltip"))
                        .setControl(option -> new CyclingControl<>(option, ImpetusGameOptions.FluidloggingGuess.class))
                        .setBinding((opts, value) -> opts.quality.inferredFluidlogging = value, opts -> opts.quality.inferredFluidlogging)
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

    // Storage over vanilla's GameSettings
    public static OptionStorage<GameSettings> getVanillaOpts() {
        return vanillaOpts;
    }

    // Storage over Impetus' own options
    public static OptionStorage<ImpetusGameOptions> getSodiumOpts() {
        return sodiumOpts;
    }

    private static class MenuState {
    }
}
