package com.bdmajora.impetus.engine.impl;

import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions;

// Snapshot of hot-path option values as plain statics, refreshed at startup and on every Apply so mesh workers never touch a config object
public final class ImpetusRuntimeOptions {
    private ImpetusRuntimeOptions() {
    }

    // Quality
    public static boolean improvedTransparency = false;
    public static boolean hiddenFluidCulling = true;
    public static boolean improvedFluidShaping = false;
    public static boolean closestPointEntitySort = false;
    // Milliseconds a new section takes to fade in; 0 shows it at once
    public static int chunkFadeInDuration = 0;

    // Performance
    public static boolean quadSplittingEnabled = true;
    public static ImpetusGameOptions.DeferChunkUpdatesMode deferMode = ImpetusGameOptions.DeferChunkUpdatesMode.ONE_FRAME;
    public static ImpetusGameOptions.InactivityFpsLimit inactivityFpsLimit = ImpetusGameOptions.InactivityFpsLimit.AFK;
    // Read when a chunk renderer is built, so a change takes a renderer reload; off until the config is applied, so nothing built before then needs GL 4.3
    public static boolean multiDrawIndirect = false;

    // Block atlas sampling; only magnification is configurable, minification is pinned to vanilla's because the atlas has no sprite borders (see BlockAtlasFiltering)
    public static ImpetusGameOptions.PixelFilteringMode pixelFiltering = ImpetusGameOptions.PixelFilteringMode.NEAREST;

    // Copies the hot-path subset out of the config
    public static void apply(ImpetusGameOptions options) {
        var quality = options.quality;
        improvedTransparency = quality.improvedTransparency;
        hiddenFluidCulling = quality.hiddenFluidCulling;
        improvedFluidShaping = quality.improvedFluidShaping;
        closestPointEntitySort = quality.closestPointEntitySort;
        chunkFadeInDuration = quality.chunkFadeInDuration;
        pixelFiltering = quality.pixelFiltering;

        var performance = options.performance;
        quadSplittingEnabled = performance.quadSplittingMode.isEnabled();
        deferMode = performance.deferChunkUpdatesMode;
        inactivityFpsLimit = performance.inactivityFpsLimit;
        multiDrawIndirect = performance.useMultiDrawIndirect;

        // Keep the legacy boolean the engine already reads in sync with the new tri-state defer mode.
        performance.alwaysDeferChunkUpdates = performance.deferChunkUpdatesMode.defersVisible();
    }
}
