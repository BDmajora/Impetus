package com.bdmajora.impetus.engine.impl.gui.options;

import com.bdmajora.impetus.api.options.OptionIdentifier;
import com.bdmajora.impetus.api.options.control.Control;
import com.bdmajora.impetus.api.options.structure.Option;
import com.bdmajora.impetus.api.options.structure.OptionFlag;
import com.bdmajora.impetus.api.options.structure.OptionGroup;
import com.bdmajora.impetus.api.options.structure.OptionImpl;
import com.bdmajora.impetus.api.options.structure.OptionPage;
import com.bdmajora.impetus.api.options.structure.StandardOptions;
import com.bdmajora.impetus.engine.impl.common.util.NativeBuffer;
import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions;
import com.bdmajora.impetus.engine.impl.render.chunk.region.RenderRegionManager;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.engine.impl.render.mesh.MeshShaderSupport;
import com.bdmajora.impetus.engine.impl.util.Dim2i;
import com.bdmajora.testing.FakeDrawContext;
import com.bdmajora.testing.Statics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

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
        assertNotNull(com.bdmajora.impetus.api.options.control.Control.createElementFor(option, new com.bdmajora.impetus.engine.impl.util.Dim2i(0, 0, 200, 18)));
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
        // Nvidium's settings live on their own page now, not at the bottom of Performance
        assertTrue(performance.getGroups().stream().noneMatch(group -> group.getId() == StandardOptions.Group.MESH_TERRAIN));

        OptionPage nvidium = CommonOptionPages.nvidium(options, CommonOptionPages.graphicsApi(options));
        assertEquals("impetus:nvidium", nvidium.getId().toString());
        assertEquals("impetus.options.pages.nvidium", nvidium.getName().toString());
        assertEquals(6, nvidium.getOptions().size());
        nvidium.getOptions().forEach(CommonOptionPagesTest::exercise);
        assertEquals(1, options.meshTerrain.regionKeepDistance);
        new CommonOptionPages();
    }

    @Test
    void theNvidiumGroupFollowsThePendingGraphicsApi() {
        ImpetusGameOptions options = new ImpetusGameOptions();
        Statics.set(MeshShaderSupport.class, "supported", true);
        try {
            OptionImpl<ImpetusGameOptions, ImpetusGameOptions.GraphicsApi> api = CommonOptionPages.graphicsApi(options);
            OptionGroup mesh = CommonOptionPages.meshTerrainGroup(options, api);
            assertEquals(6, mesh.getOptions().size());
            assertTrue(api.isAvailable());
            assertTrue(api.getFlags().contains(OptionFlag.REQUIRES_RENDERER_RELOAD));
            assertEquals(ImpetusGameOptions.GraphicsApi.OPENGL, api.getValue());
            assertTrue(mesh.getOptions().stream().noneMatch(Option::isAvailable));

            // The pending choice lights the group up before Apply
            api.setValue(ImpetusGameOptions.GraphicsApi.NVIDIUM);
            assertTrue(mesh.getOptions().stream().allMatch(Option::isAvailable));
            assertEquals(ImpetusGameOptions.GraphicsApi.OPENGL, options.graphicsApi);
            api.reset();
            exercise(api);
            assertEquals(ImpetusGameOptions.GraphicsApi.NVIDIUM, options.graphicsApi);

            // A backend that failed to start greys both out and reads OpenGL, but the saved choice stays for when it works again
            MeshShaderSupport.markBroken("test");
            assertFalse(api.isAvailable());
            assertEquals(ImpetusGameOptions.GraphicsApi.OPENGL, api.getValue());
            assertTrue(mesh.getOptions().stream().noneMatch(Option::isAvailable));
            assertEquals(ImpetusGameOptions.GraphicsApi.NVIDIUM, options.graphicsApi);
        } finally {
            Statics.set(MeshShaderSupport.class, "supported", null);
        }
    }

    @Test
    void keepDistanceReadsAsRenderDistanceChunksOrUnlimited() {
        assertEquals("impetus.options.mesh_terrain.keep_distance.render_distance", CommonOptionPages.formatKeepDistance(0).toString());
        assertEquals(TextComponent.translatable("impetus.options.mesh_terrain.keep_distance.value", 64), CommonOptionPages.formatKeepDistance(64));
        assertEquals("impetus.options.mesh_terrain.keep_distance.unlimited", CommonOptionPages.formatKeepDistance(256).toString());
        // A config holding one chunk reads in the singular
        assertEquals(TextComponent.translatable(List.of("impetus.options.mesh_terrain.keep_distance.value.one", "impetus.options.mesh_terrain.keep_distance.value"), 1),
                CommonOptionPages.formatKeepDistance(1));
        assertEquals(TextComponent.translatable("impetus.options.mesh_terrain.keep_distance.value", 2), CommonOptionPages.formatKeepDistance(2));
    }

    @SuppressWarnings("unchecked")
    private static Option<Integer> option(OptionPage page, OptionIdentifier<?> id) {
        return (Option<Integer>) page.getOptions().stream().filter(o -> o.getId().toString().equals(id.toString())).findFirst().orElseThrow();
    }

    // The value text an idle slider row shows with its option pending at the value, the fake font naming the first key with the arguments
    private static String shown(Option<Integer> option, int value) {
        option.setValue(value);
        FakeDrawContext draw = new FakeDrawContext();
        Control.createElementFor(option, new Dim2i(0, 0, 1000, 20)).render(draw, 5000, 5000, 0f);
        option.reset();
        return draw.strings.getLast();
    }

    @Test
    void threadAndFrameCountsAreTranslatedAndSingularAtOne() {
        OptionPage performance = CommonOptionPages.performance(new ImpetusGameOptions());
        Option<Integer> threads = option(performance, StandardOptions.Option.CHUNK_UPDATE_THREADS);
        assertEquals("impetus.options.chunk_update_threads.default", shown(threads, 0));
        assertEquals("impetus.options.chunk_update_threads.value.one[1]", shown(threads, 1));
        assertEquals("impetus.options.chunk_update_threads.value[2]", shown(threads, 2));
        Option<Integer> ahead = option(performance, StandardOptions.Option.CPU_FRAMES_AHEAD);
        // No frames ahead is a real setting rather than an off switch, so zero stays a count
        assertEquals("impetus.options.cpu_render_ahead_limit.value[0]", shown(ahead, 0));
        assertEquals("impetus.options.cpu_render_ahead_limit.value.one[1]", shown(ahead, 1));
        assertEquals("impetus.options.cpu_render_ahead_limit.value[9]", shown(ahead, 9));
    }
}
