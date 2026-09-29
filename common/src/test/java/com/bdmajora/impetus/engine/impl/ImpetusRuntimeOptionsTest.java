package com.bdmajora.impetus.engine.impl;

import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ImpetusRuntimeOptionsTest {
    @Test
    void applyCopiesTheHotPathSubset() {
        ImpetusGameOptions options = new ImpetusGameOptions();
        options.quality.improvedTransparency = true;
        options.quality.hiddenFluidCulling = false;
        options.quality.improvedFluidShaping = true;
        options.quality.closestPointEntitySort = true;
        options.quality.pixelFiltering = ImpetusGameOptions.PixelFilteringMode.LINEAR;
        options.performance.quadSplittingMode = ImpetusGameOptions.QuadSplittingMode.DISABLED;
        options.performance.deferChunkUpdatesMode = ImpetusGameOptions.DeferChunkUpdatesMode.ALWAYS;
        options.performance.inactivityFpsLimit = ImpetusGameOptions.InactivityFpsLimit.MINIMIZED;
        ImpetusRuntimeOptions.apply(options);
        assertTrue(ImpetusRuntimeOptions.improvedTransparency);
        assertFalse(ImpetusRuntimeOptions.hiddenFluidCulling);
        assertTrue(ImpetusRuntimeOptions.improvedFluidShaping);
        assertTrue(ImpetusRuntimeOptions.closestPointEntitySort);
        assertEquals(ImpetusGameOptions.PixelFilteringMode.LINEAR, ImpetusRuntimeOptions.pixelFiltering);
        assertFalse(ImpetusRuntimeOptions.quadSplittingEnabled);
        assertEquals(ImpetusGameOptions.DeferChunkUpdatesMode.ALWAYS, ImpetusRuntimeOptions.deferMode);
        assertEquals(ImpetusGameOptions.InactivityFpsLimit.MINIMIZED, ImpetusRuntimeOptions.inactivityFpsLimit);
        assertTrue(options.performance.alwaysDeferChunkUpdates);
        ImpetusRuntimeOptions.apply(new ImpetusGameOptions());
        assertTrue(ImpetusRuntimeOptions.quadSplittingEnabled);
    }
}
