package com.bdmajora.impetus.engine.impl.gui;

import com.bdmajora.testing.FakeDrawContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Tests construction and size edge cases of the options controller.
class ImpetusVideoOptionsControllerEdgeTest {
    // Rejects null arguments.
    @Test
    void rejectsNulls() {
        FakeDrawContext draw = new FakeDrawContext();
        assertThrows(NullPointerException.class, () -> new ImpetusVideoOptionsController(null, List.of(), draw));
        assertThrows(NullPointerException.class, () -> new ImpetusVideoOptionsController(() -> {}, List.of(), null));
        assertThrows(NullPointerException.class, () -> new ImpetusVideoOptionsController(() -> {}, null, draw));
    }

    // Reports a screen that was drawn before its size was set.
    @Test
    void renderBeforeInitIsRejected() {
        ImpetusVideoOptionsController controller = new ImpetusVideoOptionsController(() -> {}, List.of(), new FakeDrawContext());
        assertThrows(IllegalStateException.class, () -> controller.render(new FakeDrawContext(), 0, 0, 0f));
    }

    // Builds and draws at degenerate sizes.
    @Test
    void unusualSizesBuild() {
        for (int[] size : new int[][]{{0, 0}, {1, 1}, {10000, 10}, {10, 10000}, {-5, -5}, {550, 440}, {Integer.MAX_VALUE / 4, 600}}) {
            ImpetusVideoOptionsController controller = new ImpetusVideoOptionsController(() -> {}, List.of(), new FakeDrawContext());
            controller.init(size[0], size[1]);
            controller.render(new FakeDrawContext(), 0, 0, 0f);
            assertNotNull(controller.getFrame());
            assertFalse(controller.isHasPendingChanges());
        }
    }
}
