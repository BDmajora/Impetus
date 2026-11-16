package com.bdmajora.impetus.engine.impl.gui.widgets;

import com.bdmajora.impetus.engine.impl.gui.frame.tab.TabHeaderWidget;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.engine.impl.util.Dim2i;
import com.bdmajora.testing.FakeInteractionContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Tests null handling and input edge cases of the widgets.
class WidgetEdgeCasesTest {
    // Bounds shared by the tests.
    private static final Dim2i DIM = new Dim2i(0, 0, 100, 20);

    // Rejects null constructor arguments of a button.
    @Test
    void buttonRejectsNulls() {
        TextComponent label = TextComponent.literal("a");
        assertThrows(NullPointerException.class, () -> new FlatButtonWidget(null, label, () -> {}));
        assertThrows(NullPointerException.class, () -> new FlatButtonWidget(DIM, null, () -> {}));
        assertThrows(NullPointerException.class, () -> new FlatButtonWidget(DIM, label, null));
        FlatButtonWidget button = new FlatButtonWidget(DIM, label, () -> {});
        assertThrows(NullPointerException.class, () -> button.setLabel(null));
        assertSame(label, button.getLabel());
    }

    // Reports a hidden button as not under the cursor.
    @Test
    void hiddenButtonIsNotHovered() {
        FlatButtonWidget button = new FlatButtonWidget(DIM, TextComponent.literal("a"), () -> {});
        assertTrue(button.isMouseOver(0, 0));
        assertFalse(button.isMouseOver(100, 0));
        assertFalse(button.isMouseOver(0, 20));
        assertFalse(button.isMouseOver(-1, -1));
        button.setVisible(false);
        assertFalse(button.isMouseOver(5, 5));
    }

    // Presses a focused button with Space and ignores keys while unfocused or disabled.
    @Test
    void keyPressNeedsFocusAndEnabled() {
        List<String> log = new ArrayList<>();
        FlatButtonWidget button = new FlatButtonWidget(DIM, TextComponent.literal("a"), () -> log.add("run"));
        FakeInteractionContext ctx = new FakeInteractionContext();
        assertFalse(button.keyTyped(ctx, ' ', 0));
        button.setFocused(true);
        assertTrue(button.keyTyped(ctx, ' ', 0));
        assertFalse(button.keyTyped(ctx, 'x', 0));
        button.setEnabled(false);
        assertFalse(button.canFocus());
        assertFalse(button.keyTyped(ctx, ' ', 0));
        assertEquals(List.of("run"), log);
        assertSame(DIM, button.getDimensions());
    }

    // Reads a null initial query as empty.
    @Test
    void searchBarNullQueryIsEmpty() {
        SearchBarWidget bar = new SearchBarWidget(DIM, null, false, q -> {});
        assertEquals("", bar.getQuery());
        assertThrows(NullPointerException.class, () -> new SearchBarWidget(null, "", false, q -> {}));
        assertThrows(NullPointerException.class, () -> new SearchBarWidget(DIM, "", false, null));
    }

    // Accepts printable characters and refuses control characters.
    @Test
    void searchBarFiltersControlCharacters() {
        List<String> seen = new ArrayList<>();
        SearchBarWidget bar = new SearchBarWidget(DIM, "", true, seen::add);
        FakeInteractionContext ctx = new FakeInteractionContext();
        for (char c : new char[]{'\0', 1, 31, 127, 128, 159}) {
            assertTrue(bar.keyTyped(ctx, c, 0));
        }
        assertEquals("", bar.getQuery());
        assertTrue(seen.isEmpty());
        for (char c : new char[]{' ', '~', 160, 'é', '\uD83D'}) {
            assertTrue(bar.keyTyped(ctx, c, 0));
        }
        assertEquals(" ~ é\uD83D", bar.getQuery());
        assertEquals(5, seen.size());
    }

    // Backspaces an empty query without notifying and clears or blurs on escape.
    @Test
    void searchBarBackspaceAndEscape() {
        List<String> seen = new ArrayList<>();
        SearchBarWidget bar = new SearchBarWidget(DIM, "ab", true, seen::add);
        FakeInteractionContext ctx = new FakeInteractionContext();
        assertTrue(bar.keyTyped(ctx, '\b', 0));
        assertEquals("a", bar.getQuery());
        assertTrue(bar.keyTyped(ctx, '\0', 14));
        assertEquals("", bar.getQuery());
        assertTrue(bar.keyTyped(ctx, '\b', 0));
        assertEquals(List.of("a", ""), seen);
        bar.keyTyped(ctx, 'z', 0);
        assertTrue(bar.keyTyped(ctx, (char) 27, 0));
        assertEquals("", bar.getQuery());
        assertTrue(bar.isFocused());
        assertTrue(bar.keyTyped(ctx, '\0', 1));
        assertFalse(bar.isFocused());
        assertFalse(bar.keyTyped(ctx, 'a', 0));
    }

    // Leaves Tab to the container while focused.
    @Test
    void searchBarIgnoresTab() {
        SearchBarWidget bar = new SearchBarWidget(DIM, "", true, q -> {});
        assertFalse(bar.keyTyped(new FakeInteractionContext(), '\t', 15));
        assertEquals("", bar.getQuery());
    }

    // Takes focus on an inside click and loses it on an outside click.
    @Test
    void searchBarClickFocus() {
        SearchBarWidget bar = new SearchBarWidget(DIM, "", false, q -> {});
        FakeInteractionContext ctx = new FakeInteractionContext();
        assertTrue(bar.mouseClicked(ctx, 1, 1, 0));
        assertTrue(bar.isFocused());
        assertFalse(bar.mouseClicked(ctx, 500, 1, 0));
        assertFalse(bar.isFocused());
        assertSame(DIM, bar.getDimensions());
    }

    // Rejects a null group id in a header.
    @Test
    void headerRejectsNullGroup() {
        assertThrows(NullPointerException.class, () -> new TabHeaderWidget(DIM, null, false, () -> {}));
        assertThrows(NullPointerException.class, () -> new TabHeaderWidget(DIM, "m", false, null));
    }

    // Removes a whole surrogate pair and a lone surrogate with one backspace each.
    @Test
    void searchBarBackspaceRemovesSurrogatePairs() {
        List<String> seen = new ArrayList<>();
        SearchBarWidget bar = new SearchBarWidget(DIM, "a😀", true, seen::add);
        FakeInteractionContext ctx = new FakeInteractionContext();
        assertTrue(bar.keyTyped(ctx, '\b', 0));
        assertEquals("a", bar.getQuery());
        bar.keyTyped(ctx, '\uDE00', 0);
        bar.keyTyped(ctx, '\uDE00', 0);
        bar.keyTyped(ctx, '\b', 0);
        assertEquals("a\uDE00", bar.getQuery());
        bar.keyTyped(ctx, '\b', 0);
        bar.keyTyped(ctx, '\uD83D', 0);
        bar.keyTyped(ctx, '\b', 0);
        assertEquals("a", bar.getQuery());
        SearchBarWidget lone = new SearchBarWidget(DIM, "\uDE00", true, q -> {});
        lone.keyTyped(ctx, '\b', 0);
        assertEquals("", lone.getQuery());
        assertEquals("a", seen.getFirst());
    }

    // Draws the current query and caret after edits and the placeholder when empty and unfocused.
    @Test
    void searchBarDrawsTheLatestQuery() {
        SearchBarWidget bar = new SearchBarWidget(DIM, "x", true, q -> {});
        FakeInteractionContext ctx = new FakeInteractionContext();
        bar.keyTyped(ctx, 'y', 0);
        com.bdmajora.testing.FakeDrawContext draw = new com.bdmajora.testing.FakeDrawContext();
        bar.render(draw, 0, 0, 0f);
        assertTrue(draw.strings.contains("xy") || draw.strings.contains("xy_"), draw.strings::toString);
        bar.setFocused(false);
        bar.keyTyped(ctx, 'z', 0);
        assertEquals("xy", bar.getQuery());
        SearchBarWidget empty = new SearchBarWidget(DIM, "", false, q -> {});
        com.bdmajora.testing.FakeDrawContext second = new com.bdmajora.testing.FakeDrawContext();
        empty.render(second, 0, 0, 0f);
        assertFalse(second.strings.contains(""));
    }

    // Names the style in the null refusal.
    @Test
    void buttonStyleRejectsNull() {
        FlatButtonWidget button = new FlatButtonWidget(DIM, TextComponent.literal("a"), () -> {});
        NullPointerException error = assertThrows(NullPointerException.class, () -> button.setStyle(null));
        assertEquals("style", error.getMessage());
    }
}
