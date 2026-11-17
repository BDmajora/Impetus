package com.bdmajora.impetus.engine.impl.gui.options;

import com.bdmajora.impetus.api.options.control.Control;
import com.bdmajora.impetus.api.options.structure.Option;
import com.bdmajora.impetus.api.options.structure.OptionFlag;
import com.bdmajora.impetus.api.options.structure.OptionImpl;
import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions;
import com.bdmajora.impetus.engine.impl.render.mesh.MeshShaderSupport;
import com.bdmajora.impetus.engine.impl.util.Dim2i;
import com.bdmajora.testing.FakeDrawContext;
import com.bdmajora.testing.Statics;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommonOptionPagesTest {
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
    void everyBuilderBindsItsOption() {
        ImpetusGameOptions options = new ImpetusGameOptions();
        List<Option<?>> built = List.of(CommonOptionPages.chunkUpdateThreads(options), CommonOptionPages.deferChunkUpdates(options),
                CommonOptionPages.entityCulling(options), CommonOptionPages.animateVisibleTextures(options),
                CommonOptionPages.inactivityFpsLimit(options), CommonOptionPages.showToasts(options),
                CommonOptionPages.incompatiblePackWarnings(options));
        built.forEach(CommonOptionPagesTest::exercise);
        assertEquals(1, options.performance.chunkBuilderThreads);
        assertNotEquals(new ImpetusGameOptions().performance.deferChunkUpdatesMode, options.performance.deferChunkUpdatesMode);
        assertFalse(options.performance.useEntityCulling);
        assertFalse(options.performance.animateOnlyVisibleTextures);
        assertFalse(options.notifications.showToasts);
        assertTrue(options.advanced.disableIncompatibleModWarnings);
        assertEquals("impetus:chunk_update_threads", built.get(0).getId().toString());
    }

    @Test
    void theRendererChoiceFollowsMeshShaderSupport() {
        ImpetusGameOptions options = new ImpetusGameOptions();
        Statics.set(MeshShaderSupport.class, "supported", true);
        try {
            OptionImpl<ImpetusGameOptions, ImpetusGameOptions.GraphicsApi> api = CommonOptionPages.graphicsApi(options);
            assertTrue(api.isAvailable());
            assertTrue(api.getFlags().contains(OptionFlag.REQUIRES_RENDERER_RELOAD));
            assertEquals(ImpetusGameOptions.GraphicsApi.OPENGL, api.getValue());
            exercise(api);
            assertEquals(ImpetusGameOptions.GraphicsApi.NVIDIUM, options.graphicsApi);

            // Checks a failed backend.
            MeshShaderSupport.markBroken("test");
            assertFalse(api.isAvailable());
            assertEquals(ImpetusGameOptions.GraphicsApi.OPENGL, api.getValue());
            assertEquals(ImpetusGameOptions.GraphicsApi.NVIDIUM, options.graphicsApi);
        } finally {
            Statics.set(MeshShaderSupport.class, "supported", null);
        }
    }

    // Returns the idle slider value text.
    private static String shown(Option<Integer> option, int value) {
        option.setValue(value);
        FakeDrawContext draw = new FakeDrawContext();
        Control.createElementFor(option, new Dim2i(0, 0, 1000, 20)).render(draw, 5000, 5000, 0f);
        option.reset();
        return draw.strings.getLast();
    }

    @Test
    void threadCountsAreTranslatedAndSingularAtOne() {
        Option<Integer> threads = CommonOptionPages.chunkUpdateThreads(new ImpetusGameOptions());
        assertEquals("impetus.options.chunk_update_threads.default", shown(threads, 0));
        assertEquals("impetus.options.chunk_update_threads.value.one[1]", shown(threads, 1));
        assertEquals("impetus.options.chunk_update_threads.value[2]", shown(threads, 2));
    }
}
