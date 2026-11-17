package com.bdmajora.impetus.engine.impl;

import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions;

// Copies of option values that are read often.
public final class ImpetusRuntimeOptions {
    // Prevents instantiation
    private ImpetusRuntimeOptions() {
    }

    // Whether hidden fluid faces are culled.
    public static boolean hiddenFluidCulling = true;
    // Whether fluid shapes are improved.
    public static boolean improvedFluidShaping = false;
    // Whether entities sort by their closest point.
    public static boolean closestPointEntitySort = false;
    // Milliseconds a new section takes to fade in.
    public static int chunkFadeInDuration = 0;

    // Whether quads are split for sorting.
    public static boolean quadSplittingEnabled = true;
    // Mode that defers chunk updates.
    public static ImpetusGameOptions.DeferChunkUpdatesMode deferMode = ImpetusGameOptions.DeferChunkUpdatesMode.ONE_FRAME;
    // Frame rate limit while inactive.
    public static ImpetusGameOptions.InactivityFpsLimit inactivityFpsLimit = ImpetusGameOptions.InactivityFpsLimit.AFK;
    // Whether multi draw indirect is used.
    public static boolean multiDrawIndirect = false;

    // Filtering of the block atlas.
    public static ImpetusGameOptions.PixelFilteringMode pixelFiltering = ImpetusGameOptions.PixelFilteringMode.NEAREST;

    // Copies the values from the options and rejects null.
    public static void apply(ImpetusGameOptions options) {
        java.util.Objects.requireNonNull(options, "options");
        var quality = options.quality;
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

        // Updates the legacy defer flag
        performance.alwaysDeferChunkUpdates = performance.deferChunkUpdatesMode.defersVisible();
    }
}
