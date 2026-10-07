package com.bdmajora.impetus.engine.impl.gui.options;

import com.bdmajora.impetus.api.options.structure.Option;
import com.bdmajora.impetus.api.options.structure.OptionGroup;
import com.bdmajora.impetus.api.options.structure.OptionPage;
import com.bdmajora.impetus.engine.impl.common.util.NativeBuffer;
import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions;
import com.bdmajora.impetus.engine.impl.render.chunk.region.RenderRegionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CommonOptionPagesTest {
    @AfterEach
    void restoreStatics() {
        NativeBuffer.ENABLE_MEMORY_TRACING = false;
        RenderRegionManager.USE_ADVANCED_STAGING_BUFFERS = true;
    }

    // Reads, flips and applies every option so each binding lambda runs both ways
    @SuppressWarnings("unchecked")
    private static void exercise(Option<?> option) {
        Object value = option.getValue();
        assertNotNull(value, option.getName().toString());
        Object next;
        if (value instanceof Boolean b) {
            next = !b;
        } else if (value instanceof Integer i) {
            next = i + 1;
        } else if (value instanceof Enum<?> e) {
            Object[] constants = e.getDeclaringClass().getEnumConstants();
            next = constants[(e.ordinal() + 1) % constants.length];
        } else {
            throw new AssertionError("unexpected option type " + value.getClass());
        }
        ((Option<Object>) option).setValue(next);
        assertTrue(option.hasChanged());
        option.applyChanges();
        assertEquals(next, option.getValue());
        assertNotNull(option.getControl().createElement(new com.bdmajora.impetus.engine.impl.util.Dim2i(0, 0, 200, 18)));
    }

    @Test
    void sortingGroupAndPerformancePageBindToTheOptions() {
        ImpetusGameOptions options = new ImpetusGameOptions();
        OptionGroup sorting = CommonOptionPages.sortingGroup(options);
        assertEquals(2, sorting.getOptions().size());
        sorting.getOptions().forEach(CommonOptionPagesTest::exercise);
        assertFalse(options.performance.useTranslucentFaceSorting);
        assertEquals(1, options.quality.chunkFadeInDuration);

        OptionPage performance = CommonOptionPages.performance(options);
        assertEquals("impetus:performance", performance.getId().toString());
        assertFalse(performance.getOptions().isEmpty());
        performance.getOptions().forEach(CommonOptionPagesTest::exercise);
        assertTrue(NativeBuffer.ENABLE_MEMORY_TRACING);
        assertFalse(RenderRegionManager.USE_ADVANCED_STAGING_BUFFERS);
        assertTrue(options.advanced.disableIncompatibleModWarnings);
        assertFalse(options.meshTerrain.enabled);
        assertEquals(1, options.meshTerrain.regionKeepDistance);
        new CommonOptionPages();
    }

    @Test
    void keepDistanceReadsAsRenderDistanceChunksOrUnlimited() {
        assertEquals("impetus.options.mesh_terrain.keep_distance.render_distance", CommonOptionPages.formatKeepDistance(0).toString());
        assertTrue(CommonOptionPages.formatKeepDistance(64).toString().contains("keep_distance.value"));
        assertEquals("impetus.options.mesh_terrain.keep_distance.unlimited", CommonOptionPages.formatKeepDistance(256).toString());
    }
}
