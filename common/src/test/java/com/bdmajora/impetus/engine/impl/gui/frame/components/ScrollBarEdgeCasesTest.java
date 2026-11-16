package com.bdmajora.impetus.engine.impl.gui.frame.components;

import com.bdmajora.impetus.engine.impl.util.Dim2i;
import com.bdmajora.testing.FakeDrawContext;
import com.bdmajora.testing.FakeInteractionContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Tests degenerate sizes and null arguments of the scroll bar.
class ScrollBarEdgeCasesTest {
    // Track used by the tests.
    private static final Dim2i TRACK = new Dim2i(0, 0, 10, 100);

    // Rejects null arguments.
    @Test
    void rejectsNulls() {
        assertThrows(NullPointerException.class, () -> new ScrollBarComponent(null, ScrollBarComponent.Mode.VERTICAL, 200, 100, o -> {}));
        assertThrows(NullPointerException.class, () -> new ScrollBarComponent(TRACK, null, 200, 100, o -> {}));
        assertThrows(NullPointerException.class, () -> new ScrollBarComponent(TRACK, ScrollBarComponent.Mode.VERTICAL, 200, 100, null));
    }

    // Survives content that is not longer than the view.
    @Test
    void equalOrZeroLengthsDoNotDivideByZero() {
        for (int[] lengths : new int[][]{{100, 100}, {0, 0}, {0, 100}, {50, 100}, {-5, 10}}) {
            ScrollBarComponent bar = new ScrollBarComponent(TRACK, ScrollBarComponent.Mode.VERTICAL, lengths[0], lengths[1], o -> {});
            bar.updateThumbPosition();
            bar.setOffset(40);
            assertEquals(0, bar.getOffset());
            bar.render(new FakeDrawContext(), 1, 1, 0f);
            bar.mouseClicked(new FakeInteractionContext(), 5, 50, 0);
        }
    }

    // Draws before any offset change and handles the int limits.
    @Test
    void largeLengthsDoNotOverflow() {
        List<Integer> seen = new ArrayList<>();
        ScrollBarComponent bar = new ScrollBarComponent(new Dim2i(0, 0, 10, 1_000_000), ScrollBarComponent.Mode.VERTICAL, Integer.MAX_VALUE, 1_000_000, seen::add);
        bar.render(new FakeDrawContext(), 0, 0, 0f);
        bar.setOffset(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE - 1_000_000, bar.getOffset());
        bar.setOffset(Integer.MIN_VALUE);
        assertEquals(0, bar.getOffset());
        assertEquals(List.of(Integer.MAX_VALUE - 1_000_000, 0), seen);
    }

    // Scrolls by the wheel inside the track or the extra area only.
    @Test
    void wheelAndHover() {
        ScrollBarComponent bar = new ScrollBarComponent(TRACK, ScrollBarComponent.Mode.VERTICAL, 400, 100, o -> {}, new Dim2i(-50, 0, 50, 100));
        FakeInteractionContext ctx = new FakeInteractionContext();
        assertTrue(bar.mouseScrolled(ctx, 5, 5, 0, -1));
        assertEquals(18, bar.getOffset());
        assertTrue(bar.mouseScrolled(ctx, -10, 5, 0, 1));
        assertEquals(0, bar.getOffset());
        assertFalse(bar.mouseScrolled(ctx, 500, 5, 0, -1));
        assertTrue(bar.isMouseOver(-10, 5));
        assertFalse(bar.isMouseOver(60, 5));
    }

    // Drags the thumb and ends the drag on release and on an outside click.
    @Test
    void dragLifecycle() {
        ScrollBarComponent bar = new ScrollBarComponent(TRACK, ScrollBarComponent.Mode.VERTICAL, 400, 100, o -> {});
        FakeInteractionContext ctx = new FakeInteractionContext();
        assertFalse(bar.mouseDragged(ctx, 5, 90, 0, 0, 0));
        assertTrue(bar.mouseClicked(ctx, 5, 10, 0));
        assertTrue(bar.mouseDragged(ctx, 5, 60, 0, 0, 0));
        assertTrue(bar.getOffset() > 0);
        assertFalse(bar.mouseReleased(ctx, 5, 60, 1));
        assertTrue(bar.mouseDragged(ctx, 5, 70, 0, 0, 0));
        assertFalse(bar.mouseReleased(ctx, 5, 60, 0));
        assertFalse(bar.mouseDragged(ctx, 5, 70, 0, 0, 0));
        assertFalse(bar.mouseClicked(ctx, 500, 500, 0));
    }

    // Behaves the same on the horizontal axis.
    @Test
    void horizontalMode() {
        ScrollBarComponent bar = new ScrollBarComponent(new Dim2i(0, 0, 100, 10), ScrollBarComponent.Mode.HORIZONTAL, 400, 100, o -> {});
        FakeInteractionContext ctx = new FakeInteractionContext();
        assertTrue(bar.mouseClicked(ctx, 90, 5, 0));
        assertTrue(bar.getOffset() > 0);
        bar.render(new FakeDrawContext(), 5, 5, 0f);
    }

    // Keeps the thumb inside the track when the content is shorter than the viewport.
    @Test
    void shortContentThumbFillsTrack() {
        for (int frame : new int[]{1, 50, 99, 100}) {
            ScrollBarComponent bar = new ScrollBarComponent(TRACK, ScrollBarComponent.Mode.VERTICAL, frame, 100, o -> {});
            FakeDrawContext draw = new FakeDrawContext();
            bar.render(draw, 0, 0, 0f);
            assertTrue(bar.mouseClicked(new FakeInteractionContext(), 5, 50, 0));
            assertEquals(0, bar.getOffset());
        }
    }

    // Accepts a negative viewport with zero content without dividing by zero.
    @Test
    void negativeViewportAndZeroContent() {
        ScrollBarComponent bar = new ScrollBarComponent(TRACK, ScrollBarComponent.Mode.VERTICAL, 0, -5, o -> {});
        bar.setOffset(Integer.MAX_VALUE);
        assertEquals(0, bar.getOffset());
        bar.updateThumbPosition();
    }

    // Clamps wheel steps of every size and reports each new offset.
    @Test
    void wheelStepsClampAtBothEnds() {
        List<Integer> seen = new ArrayList<>();
        ScrollBarComponent bar = new ScrollBarComponent(TRACK, ScrollBarComponent.Mode.VERTICAL, 400, 100, seen::add);
        FakeInteractionContext context = new FakeInteractionContext();
        assertTrue(bar.mouseScrolled(context, 5, 50, 0, -1));
        assertEquals(18, bar.getOffset());
        assertTrue(bar.mouseScrolled(context, 5, 50, 0, -1e12));
        assertEquals(300, bar.getOffset());
        assertTrue(bar.mouseScrolled(context, 5, 50, 0, Double.NEGATIVE_INFINITY));
        assertEquals(300, bar.getOffset());
        assertTrue(bar.mouseScrolled(context, 5, 50, 0, 1e12));
        assertEquals(0, bar.getOffset());
        assertTrue(bar.mouseScrolled(context, 5, 50, 0, 0));
        assertEquals(0, bar.getOffset());
        assertFalse(bar.mouseScrolled(context, 50, 50, 0, -1));
        assertEquals(List.of(18, 300, 300, 0, 0), seen);
    }

    // Ignores releases of other buttons and ends a drag on the left button.
    @Test
    void releaseOnlyEndsLeftButtonDrags() {
        ScrollBarComponent bar = new ScrollBarComponent(TRACK, ScrollBarComponent.Mode.VERTICAL, 400, 100, o -> {});
        FakeInteractionContext context = new FakeInteractionContext();
        assertTrue(bar.mouseClicked(context, 5, 5, 0));
        assertFalse(bar.mouseReleased(context, 5, 5, 1));
        assertTrue(bar.mouseDragged(context, 5, 60, 0, 0, 55));
        assertFalse(bar.mouseReleased(context, 5, 5, 0));
        assertFalse(bar.mouseDragged(context, 5, 90, 0, 0, 30));
    }
}
