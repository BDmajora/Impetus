package com.bdmajora.extras.config;

import com.bdmajora.extras.ExtrasConfig.ExtraSettings;
import com.bdmajora.extras.ExtrasConfig.HudSettings;
import com.bdmajora.extras.ExtrasConfig.TextSettings;
import com.bdmajora.extras.ExtrasConfig.ZoomSettings;

// Settings of the overlays and toasts and HUD and text and zoom
final class InterfaceSchema {
    // Overlay and toast and quality of life settings
    static final SettingGroup<ExtraSettings> EXTRA = SettingGroup.builder("extra", c -> c.extra, ExtraSettings::new)
            .bool("showFps", "Show the FPS overlay", s -> s.showFps, (s, v) -> s.showFps = v)
            .bool("showFPSExtended", "Include average, 1% low and 0.1% low in the FPS overlay",
                    s -> s.showFpsExtended, (s, v) -> s.showFpsExtended = v)
            .bool("showCoords", "Show the coordinates overlay", s -> s.showCoords, (s, v) -> s.showCoords = v)
            .bool("ignoreReducedDebugInfo", "Show coordinates even when the server sets the reducedDebugInfo game rule",
                    s -> s.ignoreReducedDebugInfo, (s, v) -> s.ignoreReducedDebugInfo = v)
            .bool("steadyDebugHud", "Throttle how often the F3 overlay text is rebuilt",
                    s -> s.steadyDebugHud, (s, v) -> s.steadyDebugHud = v)
            .bool("useAdaptiveSync", "Use adaptive VSync (swap interval -1) when supported",
                    s -> s.useAdaptiveSync, (s, v) -> s.useAdaptiveSync = v)
            .bool("toasts", "Master switch for toast pop-ups", s -> s.toasts, (s, v) -> s.toasts = v)
            .bool("toastAdvancement", "Advancement toasts", s -> s.toastAdvancement, (s, v) -> s.toastAdvancement = v)
            .bool("toastRecipe", "Recipe unlock toasts", s -> s.toastRecipe, (s, v) -> s.toastRecipe = v)
            .bool("toastTutorial", "Tutorial toasts", s -> s.toastTutorial, (s, v) -> s.toastTutorial = v)
            .bool("toastSystem", "System toasts", s -> s.toastSystem, (s, v) -> s.toastSystem = v)
            .bool("modNameTooltip", "Append the source mod's name to item tooltips",
                    s -> s.modNameTooltip, (s, v) -> s.modNameTooltip = v)
            .bool("paniniProjection", "Apply a Panini projection post-effect to widen the view",
                    s -> s.paniniProjection, (s, v) -> s.paniniProjection = v)
            .integer("steadyDebugHudRefreshInterval", ExtraSettings.STEADY_HUD_REFRESH_MIN, ExtraSettings.STEADY_HUD_REFRESH_MAX,
                    "F3 overlay rebuild interval in ticks",
                    s -> s.steadyDebugHudRefreshInterval, (s, v) -> s.steadyDebugHudRefreshInterval = v)
            .integer("paniniProjectionStrength", 0, 100, "Panini projection strength as a percentage",
                    s -> s.paniniProjectionStrength, (s, v) -> s.paniniProjectionStrength = v)
            .integer("autosaveInterval", ExtraSettings.AUTOSAVE_MIN_TICKS, ExtraSettings.AUTOSAVE_MAX_TICKS,
                    "Singleplayer autosave interval in ticks (900 = vanilla)",
                    s -> s.autosaveInterval, (s, v) -> s.autosaveInterval = v)
            .choice("overlayCorner", "Overlay corner (0 = Top Left, 1 = Top Right, 2 = Bottom Left, 3 = Bottom Right)",
                    s -> s.overlayCorner, (s, v) -> s.overlayCorner = v)
            .choice("textContrast", "Overlay text contrast (0 = None, 1 = Background, 2 = Shadow)",
                    s -> s.textContrast, (s, v) -> s.textContrast = v)
            .choice("timeOverride", "Client-side time of day (0 = Default, 1 = Day only, 2 = Night only). Creative/cheats only.",
                    s -> s.timeOverride, (s, v) -> s.timeOverride = v)
            .choice("weatherOverride", "Client-side weather (0 = Default, 1 = Clear, 2 = Rain, 3 = Thunder). Creative/cheats only.",
                    s -> s.weatherOverride, (s, v) -> s.weatherOverride = v)
            .build();

    // HUD framebuffer cache switch and rate
    static final SettingGroup<HudSettings> HUD = SettingGroup.builder("hud", c -> c.hud, HudSettings::new)
            .bool("cacheEnabled", "Draw the HUD into a framebuffer at a capped rate and composite it every frame; crosshair and vignette stay live",
                    s -> s.cacheEnabled, (s, v) -> s.cacheEnabled = v)
            .integer("cacheFps", HudSettings.CACHE_FPS_MIN, HudSettings.CACHE_FPS_MAX, "How many times per second the cached HUD is redrawn",
                    s -> s.cacheFps, (s, v) -> s.cacheFps = v)
            .build();

    // Glyph batching switch
    static final SettingGroup<TextSettings> TEXT = SettingGroup.builder("text", c -> c.text, TextSettings::new)
            .bool("batchGlyphs", "Draw each string's glyphs in one batch instead of one immediate-mode quad per glyph",
                    s -> s.batchGlyphs, (s, v) -> s.batchGlyphs = v)
            .build();

    // Hold to zoom switches and zoom level
    static final SettingGroup<ZoomSettings> ZOOM = SettingGroup.builder("zoom", c -> c.zoom, ZoomSettings::new)
            .bool("enabled", "Zoom while the zoom key (C by default) is held", s -> s.enabled, (s, v) -> s.enabled = v)
            .bool("smoothCamera", "Turn the cinematic camera on while zoomed", s -> s.smoothCamera, (s, v) -> s.smoothCamera = v)
            .bool("hideHand", "Hide the first-person hand and held items while zoomed", s -> s.hideHand, (s, v) -> s.hideHand = v)
            .integer("level", ZoomSettings.LEVEL_MIN, ZoomSettings.LEVEL_MAX,
                    "Percentage of the normal view width kept while zoomed (23 = Logical Zoom's 4.35x)",
                    s -> s.level, (s, v) -> s.level = v)
            .build();

    // Prevents instantiation
    private InterfaceSchema() {
    }
}
