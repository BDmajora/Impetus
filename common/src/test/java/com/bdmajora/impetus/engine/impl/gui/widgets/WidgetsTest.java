package com.bdmajora.impetus.engine.impl.gui.widgets;

import com.bdmajora.impetus.engine.impl.gui.framework.Keys;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.engine.impl.gui.theme.DefaultColors;
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
        FakeInteractionContext ctx = new FakeInteractionContext();
        assertFalse(bar.keyTyped(ctx, 'c', 0));
        assertTrue(bar.mouseClicked(ctx, 5, 5, 0));
        assertTrue(bar.isFocused());
        assertTrue(bar.isMouseOver(5, 5));
        assertTrue(bar.keyTyped(ctx, 'c', 0));
        assertEquals("abc", bar.getQuery());
        assertTrue(bar.keyTyped(ctx, '\b', 0));
        assertEquals("ab", bar.getQuery());
        assertTrue(bar.keyTyped(ctx, '\0', 14));
        assertEquals("a", bar.getQuery());
        assertTrue(bar.keyTyped(ctx, '\0', 14));
        assertEquals("", bar.getQuery());
        assertTrue(bar.keyTyped(ctx, '\0', 14));
        assertTrue(bar.keyTyped(ctx, (char) 127, 0));
        assertTrue(bar.keyTyped(ctx, '\0', 500));
        assertEquals("", bar.getQuery());
        assertTrue(bar.keyTyped(ctx, 'z', 0));
        assertTrue(bar.keyTyped(ctx, (char) 27, 0));
        assertEquals("", bar.getQuery());
        assertTrue(bar.isFocused());
        assertTrue(bar.keyTyped(ctx, '\0', 1));
        assertFalse(bar.isFocused());
        // A GLFW key code is not what vanilla screens pass on, so it is just swallowed
        assertTrue(bar.mouseClicked(ctx, 5, 5, 0));
        assertTrue(bar.keyTyped(ctx, '\0', 256));
        assertTrue(bar.isFocused());
        assertTrue(bar.keyTyped(ctx, (char) 27, 0));
        assertFalse(bar.isFocused());
        assertEquals(List.of("abc", "ab", "a", "", "z", ""), seen);
        assertFalse(bar.mouseClicked(ctx, 500, 5, 0));

        FakeDrawContext draw = new FakeDrawContext();
        bar.render(draw, 500, 500, 0f);
        assertTrue(draw.strings.contains("impetus.search_bar_empty"));
        bar.mouseClicked(ctx, 5, 5, 0);
        bar.keyTyped(ctx, 'q', 0);
        bar.render(draw, 5, 5, 0f);
        assertTrue(draw.strings.stream().anyMatch(s -> s.startsWith("q")));
    }

    @Test
    void aFocusedFlatButtonLooksHoveredIsOutlinedAndIsPressedByEnterOrSpace() {
        AtomicInteger runs = new AtomicInteger();
        FlatButtonWidget button = new FlatButtonWidget(DIM, TextComponent.literal("Go"), runs::incrementAndGet);
        FakeInteractionContext ctx = new FakeInteractionContext();
        assertEquals(DIM, button.getDimensions());
        assertTrue(button.canFocus());
        assertFalse(button.isFocused());
        assertFalse(button.keyTyped(ctx, ' ', Keys.SPACE));
        FakeDrawContext idle = new FakeDrawContext();
        button.render(idle, 500, 500, 0f);
        assertEquals(1, idle.calls.stream().filter(c -> c.startsWith("fill")).count());

        button.setFocused(true);
        assertTrue(button.isFocused());
        assertTrue(button.keyTyped(ctx, '\r', Keys.RETURN));
        assertTrue(button.keyTyped(ctx, '\0', Keys.NUMPAD_ENTER));
        assertTrue(button.keyTyped(ctx, ' ', Keys.SPACE));
        assertFalse(button.keyTyped(ctx, 'a', 30));
        assertFalse(button.keyTyped(ctx, '\t', Keys.TAB));
        assertEquals(3, runs.get());
        assertEquals(3, ctx.clicks);

        // Far from the cursor it is drawn hovered, label over it, and outlined last in the style's accent
        FakeDrawContext draw = new FakeDrawContext();
        button.render(draw, 500, 500, 0f);
        String accent = Integer.toHexString(DefaultColors.ELEMENT_ACTIVATED);
        assertEquals("fill(0,0,100,20," + Integer.toHexString(DefaultColors.BACKGROUND_HOVERED) + ")", draw.calls.getFirst());
        assertTrue(draw.calls.get(1).startsWith("text(Go,"));
        assertEquals(List.of("fill(0,0,100,1," + accent + ")", "fill(0,19,100,20," + accent + ")", "fill(0,0,1,20," + accent + ")", "fill(99,0,100,20," + accent + ")"), draw.calls.subList(2, 6));
        assertEquals(6, draw.calls.size());

        // Greyed out or hidden, Tab passes it over and keys leave it alone, though it keeps the flag until its container moves the focus
        button.setEnabled(false);
        assertFalse(button.canFocus());
        assertFalse(button.keyTyped(ctx, ' ', Keys.SPACE));
        button.setEnabled(true);
        button.setVisible(false);
        assertFalse(button.canFocus());
        assertFalse(button.keyTyped(ctx, ' ', Keys.SPACE));
        assertTrue(button.isFocused());
        int before = draw.calls.size();
        button.render(draw, 500, 500, 0f);
        assertEquals(before, draw.calls.size());
        assertEquals(3, runs.get());
        button.setFocused(false);
        assertFalse(button.isFocused());
    }

    @Test
    void theSearchBarTakesFocusFromItsContainerAndLeavesTabToIt() {
        List<String> seen = new ArrayList<>();
        SearchBarWidget bar = new SearchBarWidget(DIM, "", false, seen::add);
        FakeInteractionContext ctx = new FakeInteractionContext();
        assertEquals(DIM, bar.getDimensions());
        assertTrue(bar.canFocus());
        bar.setFocused(true);
        assertTrue(bar.isFocused());
        // Tab, by character or key code, is not typing, so the container can move the focus on
        assertFalse(bar.keyTyped(ctx, '\t', Keys.TAB));
        assertFalse(bar.keyTyped(ctx, '\0', Keys.TAB));
        assertFalse(bar.keyTyped(ctx, '\t', 0));
        assertTrue(bar.isFocused());
        // Space and Enter still are: Space joins the query and Enter is swallowed
        assertTrue(bar.keyTyped(ctx, ' ', Keys.SPACE));
        assertTrue(bar.keyTyped(ctx, '\r', Keys.RETURN));
        assertTrue(bar.keyTyped(ctx, '\0', Keys.NUMPAD_ENTER));
        assertEquals(" ", bar.getQuery());
        assertEquals(List.of(" "), seen);
        bar.setFocused(false);
        assertFalse(bar.keyTyped(ctx, 'x', 0));
        assertEquals(" ", bar.getQuery());
    }
}
