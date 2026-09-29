package com.bdmajora.impetus.engine.impl.gui.widgets;

import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.engine.impl.util.Dim2i;
import com.bdmajora.testing.FakeDrawContext;
import com.bdmajora.testing.FakeInteractionContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class WidgetsTest {
    private static final Dim2i DIM = new Dim2i(0, 0, 100, 20);

    @Test
    void flatButtonRendersStatesAndRunsItsAction() {
        AtomicInteger runs = new AtomicInteger();
        FlatButtonWidget button = new FlatButtonWidget(DIM, TextComponent.literal("Go"), runs::incrementAndGet);
        FakeDrawContext draw = new FakeDrawContext();
        FakeInteractionContext ctx = new FakeInteractionContext();
        button.render(draw, 5, 5, 0f);
        assertTrue(draw.strings.contains("Go"));
        assertTrue(button.mouseClicked(ctx, 5, 5, 0));
        assertEquals(1, runs.get());
        assertEquals(1, ctx.clicks);
        assertFalse(button.mouseClicked(ctx, 5, 5, 1));
        assertFalse(button.mouseClicked(ctx, 500, 5, 0));
        assertTrue(button.isMouseOver(5, 5));

        button.setSelected(true);
        button.render(draw, 500, 500, 0f);
        button.setLeftAligned(true);
        button.render(draw, 5, 5, 0f);
        button.setEnabled(false);
        button.render(draw, 5, 5, 0f);
        assertFalse(button.mouseClicked(ctx, 5, 5, 0));
        button.setEnabled(true);
        button.setVisible(false);
        int before = draw.calls.size();
        button.render(draw, 5, 5, 0f);
        assertEquals(before, draw.calls.size());
        assertFalse(button.mouseClicked(ctx, 5, 5, 0));
        button.setVisible(true);
        button.setLabel(TextComponent.literal("New"));
        assertEquals("New", button.getLabel().toString());
        FlatButtonWidget.Style style = FlatButtonWidget.Style.defaults();
        style.accentColor = 0xFF123456;
        button.setStyle(style);
        button.render(draw, 5, 5, 0f);
        assertTrue(draw.drew("ff123456"));
        assertThrows(NullPointerException.class, () -> button.setStyle(null));
    }

    @Test
    void searchBarEditsWhileFocusedAndSwallowsKeys() {
        List<String> seen = new ArrayList<>();
        SearchBarWidget bar = new SearchBarWidget(DIM, "ab", false, seen::add);
        assertEquals("ab", bar.getQuery());
        assertFalse(bar.isFocused());
        assertFalse(bar.keyTyped('c', 0));
        FakeInteractionContext ctx = new FakeInteractionContext();
        assertTrue(bar.mouseClicked(ctx, 5, 5, 0));
        assertTrue(bar.isFocused());
        assertTrue(bar.isMouseOver(5, 5));
        assertTrue(bar.keyTyped('c', 0));
        assertEquals("abc", bar.getQuery());
        assertTrue(bar.keyTyped('\b', 0));
        assertEquals("ab", bar.getQuery());
        assertTrue(bar.keyTyped('\0', 14));
        assertEquals("a", bar.getQuery());
        assertTrue(bar.keyTyped('\0', 259));
        assertEquals("", bar.getQuery());
        assertTrue(bar.keyTyped('\0', 259));
        assertTrue(bar.keyTyped((char) 127, 0));
        assertTrue(bar.keyTyped('\0', 500));
        assertEquals("", bar.getQuery());
        assertTrue(bar.keyTyped('z', 0));
        assertTrue(bar.keyTyped((char) 27, 0));
        assertEquals("", bar.getQuery());
        assertTrue(bar.isFocused());
        assertTrue(bar.keyTyped('\0', 1));
        assertFalse(bar.isFocused());
        assertTrue(bar.mouseClicked(ctx, 5, 5, 0));
        assertTrue(bar.keyTyped('\0', 256));
        assertFalse(bar.isFocused());
        assertEquals(List.of("abc", "ab", "a", "", "z", ""), seen);
        assertFalse(bar.mouseClicked(ctx, 500, 5, 0));

        FakeDrawContext draw = new FakeDrawContext();
        bar.render(draw, 500, 500, 0f);
        assertTrue(draw.strings.contains("impetus.search_bar_empty"));
        bar.mouseClicked(ctx, 5, 5, 0);
        bar.keyTyped('q', 0);
        bar.render(draw, 5, 5, 0f);
        assertTrue(draw.strings.stream().anyMatch(s -> s.startsWith("q")));
    }
}
