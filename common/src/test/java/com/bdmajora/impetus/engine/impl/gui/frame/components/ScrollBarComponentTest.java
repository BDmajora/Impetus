package com.bdmajora.impetus.engine.impl.gui.frame.components;

import com.bdmajora.impetus.engine.impl.util.Dim2i;
import com.bdmajora.testing.FakeDrawContext;
import com.bdmajora.testing.FakeInteractionContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScrollBarComponentTest {
    @Test
    void verticalBarClicksDragsAndScrolls() {
        List<Integer> offsets = new ArrayList<>();
        // 100px track over 400px of content in a 100px viewport
        ScrollBarComponent bar = new ScrollBarComponent(new Dim2i(90, 0, 10, 100), ScrollBarComponent.Mode.VERTICAL, 400, 100, offsets::add, new Dim2i(0, 0, 90, 100));
        bar.updateThumbPosition();
        FakeInteractionContext ctx = new FakeInteractionContext();
        assertTrue(bar.isMouseOver(95, 5));
        assertTrue(bar.isMouseOver(5, 5));
        assertFalse(bar.isMouseOver(5, 500));
        assertTrue(bar.mouseClicked(ctx, 95, 90, 0));
        assertEquals(300, bar.getOffset());
        assertEquals(300, offsets.getLast());
        assertTrue(bar.mouseClicked(ctx, 95, 85, 0));
        assertTrue(bar.mouseDragged(ctx, 95, 0, 0, 0, 0));
        assertEquals(0, bar.getOffset());
        assertFalse(bar.mouseReleased(ctx, 95, 10, 0));
        assertFalse(bar.mouseDragged(ctx, 95, 50, 0, 0, 0));
        assertFalse(bar.mouseClicked(ctx, 500, 500, 0));
        assertTrue(bar.mouseScrolled(ctx, 5, 5, 0, -1));
        assertEquals(18, bar.getOffset());
        assertFalse(bar.mouseScrolled(ctx, 500, 500, 0, -1));
        bar.setOffset(9999);
        assertEquals(300, bar.getOffset());
        bar.setOffset(-5);
        assertEquals(0, bar.getOffset());
        FakeDrawContext draw = new FakeDrawContext();
        bar.render(draw, 95, 5, 0f);
        bar.render(draw, 500, 500, 0f);
        assertEquals(4, draw.calls.size());
        assertFalse(bar.mouseReleased(ctx, 0, 0, 1));
    }

    @Test
    void horizontalBarUsesTheOtherAxis() {
        List<Integer> offsets = new ArrayList<>();
        ScrollBarComponent bar = new ScrollBarComponent(new Dim2i(0, 90, 100, 10), ScrollBarComponent.Mode.HORIZONTAL, 200, 100, offsets::add);
        bar.updateThumbPosition();
        FakeInteractionContext ctx = new FakeInteractionContext();
        assertTrue(bar.mouseClicked(ctx, 95, 95, 0));
        assertEquals(100, bar.getOffset());
        assertTrue(bar.mouseClicked(ctx, 60, 95, 0));
        assertTrue(bar.mouseDragged(ctx, 5, 95, 0, 0, 0));
        assertEquals(0, bar.getOffset());
        assertTrue(bar.mouseScrolled(ctx, 50, 95, 0, -2));
        assertEquals(36, bar.getOffset());
        new ScrollBarComponent(new Dim2i(0, 0, 10, 10), ScrollBarComponent.Mode.VERTICAL, 20, 10, offsets::add, new Dim2i(0, 0, 1, 1)).updateThumbPosition();
        new ScrollBarComponent(new Dim2i(0, 0, 10, 10), ScrollBarComponent.Mode.VERTICAL, 20, 10, offsets::add, 1).updateThumbPosition();
        assertEquals(2, ScrollBarComponent.Mode.values().length);
    }
}
