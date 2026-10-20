package com.bdmajora.impetus.engine.impl.gui.framework;

import com.bdmajora.impetus.engine.impl.util.Dim2i;
import com.bdmajora.testing.FakeDrawContext;
import com.bdmajora.testing.FakeInteractionContext;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class FrameworkTest {
    @Test
    void textComponentsFlattenAndStyle() {
        TextComponent literal = TextComponent.literal("hi");
        assertEquals("hi", literal.toString());
        assertTrue(literal.children().isEmpty());
        TextComponent styled = literal.withStyle(TextFormattingStyle.RED);
        assertEquals("hi[styled]", styled.toString());
        assertEquals(List.of(literal), styled.children());
        TextComponent.Styled more = (TextComponent.Styled) styled.withStyle(TextFormattingStyle.ITALIC, TextFormattingStyle.UNDERLINE);
        assertEquals(EnumSet.of(TextFormattingStyle.RED, TextFormattingStyle.ITALIC, TextFormattingStyle.UNDERLINE), more.styles());
        assertEquals("key", TextComponent.translatable("key").toString());
        assertEquals("key[1, 2]", TextComponent.translatable("key", 1, 2).toString());
        assertEquals("[a, b]", TextComponent.translatable(List.of("a", "b")).toString());
        assertTrue(TextFormattingStyle.GOLD.isColor());
        assertFalse(TextFormattingStyle.ITALIC.isColor());
    }

    @Test
    void drawContextDefaultsDelegateAndDrawBorderFillsFourEdges() {
        FakeDrawContext draw = new FakeDrawContext();
        assertEquals(12, draw.drawString("ab", 0, 0, 1));
        draw.drawBorder(0, 0, 10, 10, 5);
        assertEquals(4, draw.calls.stream().filter(c -> c.startsWith("fill")).count());
        assertNull(draw.getModLogoPath("x"));
        assertEquals("x", draw.getFriendlyModName("x").toString());
        assertEquals(12, draw.getStringWidth("ab"));
        assertEquals(0xFF00CBCB, draw.getModAccentColor("impetus"));
    }

    @Test
    void interactableDefaultsDoNothing() {
        Interactable none = (x, y) -> false;
        FakeInteractionContext ctx = new FakeInteractionContext();
        assertFalse(none.mouseClicked(ctx, 0, 0, 0));
        assertFalse(none.mouseReleased(ctx, 0, 0, 0));
        assertFalse(none.mouseDragged(ctx, 0, 0, 0, 0, 0));
        assertFalse(none.mouseScrolled(ctx, 0, 0, 0, 0));
        assertFalse(none.keyTyped(ctx, 'a', 1));
        InteractionContext silent = new InteractionContext() {};
        silent.playClickSound();
        assertFalse(silent.isSpecialKeyDown(InteractionContext.SpecialKey.CTRL));
        assertEquals(3, InteractionContext.SpecialKey.values().length);
    }

    // A container over fixed children that remembers the child capturing the mouse
    private static InteractableContainer container(Interactable... children) {
        return new InteractableContainer() {
            private Interactable captured;

            @Override
            public Stream<? extends Interactable> interactableChildren() {
                return Stream.of(children);
            }

            @Override
            public Interactable getCapturedChild() {
                return captured;
            }

            @Override
            public void setCapturedChild(Interactable child) {
                captured = child;
            }
        };
    }

    @Test
    void containersForwardToTheFirstChildThatHandles() {
        Interactable miss = Mockito.mock(Interactable.class);
        Interactable hit = Mockito.mock(Interactable.class);
        Mockito.when(hit.isMouseOver(Mockito.anyDouble(), Mockito.anyDouble())).thenReturn(true);
        Mockito.when(hit.mouseClicked(Mockito.any(), Mockito.anyDouble(), Mockito.anyDouble(), Mockito.anyInt())).thenReturn(true);
        Mockito.when(hit.mouseReleased(Mockito.any(), Mockito.anyDouble(), Mockito.anyDouble(), Mockito.anyInt())).thenReturn(true);
        Mockito.when(hit.mouseDragged(Mockito.any(), Mockito.anyDouble(), Mockito.anyDouble(), Mockito.anyInt(), Mockito.anyDouble(), Mockito.anyDouble())).thenReturn(true);
        Mockito.when(hit.mouseScrolled(Mockito.any(), Mockito.anyDouble(), Mockito.anyDouble(), Mockito.anyDouble(), Mockito.anyDouble())).thenReturn(true);
        Mockito.when(hit.keyTyped(Mockito.any(), Mockito.anyChar(), Mockito.anyInt())).thenReturn(true);
        InteractableContainer container = container(miss, hit);
        FakeInteractionContext ctx = new FakeInteractionContext();
        assertTrue(container.isMouseOver(1, 1));
        assertTrue(container.mouseClicked(ctx, 1, 1, 0));
        // The child that took the click keeps the drag even once the cursor leaves it
        assertSame(hit, container.getCapturedChild());
        Mockito.when(hit.isMouseOver(Mockito.anyDouble(), Mockito.anyDouble())).thenReturn(false);
        assertTrue(container.mouseDragged(ctx, 50, 50, 0, 0, 0));
        assertTrue(container.mouseReleased(ctx, 1, 1, 0));
        assertNull(container.getCapturedChild());
        assertFalse(container.mouseDragged(ctx, 50, 50, 0, 0, 0));
        Mockito.when(hit.isMouseOver(Mockito.anyDouble(), Mockito.anyDouble())).thenReturn(true);
        assertTrue(container.mouseDragged(ctx, 1, 1, 0, 0, 0));
        assertTrue(container.mouseReleased(ctx, 1, 1, 0));
        assertTrue(container.mouseScrolled(ctx, 1, 1, 0, 1));
        assertTrue(container.keyTyped(ctx, 'a', 1));
        InteractableContainer empty = container();
        assertFalse(empty.isMouseOver(1, 1));
        assertFalse(empty.mouseClicked(ctx, 1, 1, 0));
        assertFalse(empty.mouseReleased(ctx, 1, 1, 0));
        assertFalse(empty.mouseDragged(ctx, 1, 1, 0, 0, 0));
        assertFalse(empty.mouseScrolled(ctx, 1, 1, 0, 0));
        Mockito.verify(miss).mouseClicked(ctx, 1, 1, 0);
        Mockito.verify(miss, Mockito.never()).mouseDragged(Mockito.any(), Mockito.anyDouble(), Mockito.anyDouble(), Mockito.anyInt(), Mockito.anyDouble(), Mockito.anyDouble());
    }

    @Test
    void keysMatchEitherTheCharacterOrVanillasKeyCode() {
        // LWJGL2 numbering, which lwjglx keeps, rather than GLFW's
        assertEquals(List.of(15, 28, 57, 156, 203, 205), List.of(Keys.TAB, Keys.RETURN, Keys.SPACE, Keys.NUMPAD_ENTER, Keys.LEFT, Keys.RIGHT));
        assertTrue(Keys.isTab('\t', 0));
        assertTrue(Keys.isTab('\0', Keys.TAB));
        assertTrue(Keys.isTab('\t', Keys.TAB));
        assertFalse(Keys.isTab('\0', 0));
        assertFalse(Keys.isTab(' ', Keys.SPACE));
        assertFalse(Keys.isTab('\0', 258));
        for (char typed : new char[]{'\r', '\n', ' '}) {
            assertTrue(Keys.isPress(typed, 0), () -> "char " + (int) typed);
        }
        for (int code : new int[]{Keys.RETURN, Keys.NUMPAD_ENTER, Keys.SPACE}) {
            assertTrue(Keys.isPress('\0', code), () -> "key " + code);
        }
        assertFalse(Keys.isPress('a', 30));
        assertFalse(Keys.isPress('\t', Keys.TAB));
        assertFalse(Keys.isPress('\0', Keys.LEFT));
        assertFalse(Keys.isPress('\0', 257));
    }

    // A focusable control that logs focus changes and the keys it is offered, claims clicks inside its bounds and, while focused, the characters in claims
    private static final class Leaf implements Focusable {
        final String name;
        final List<String> log;
        final Dim2i dim;
        boolean focused;
        boolean focusable = true;
        String claims = "";

        Leaf(String name, List<String> log, int x) {
            this.name = name;
            this.log = log;
            this.dim = new Dim2i(x, 0, 10, 10);
        }

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            return dim.containsCursor(mouseX, mouseY);
        }

        @Override
        public boolean mouseClicked(InteractionContext context, double mouseX, double mouseY, int button) {
            return dim.containsCursor(mouseX, mouseY);
        }

        @Override
        public boolean keyTyped(InteractionContext context, char typedChar, int keyCode) {
            log.add(name + " " + typedChar);
            return focused && claims.indexOf(typedChar) >= 0;
        }

        @Override
        public boolean canFocus() {
            return focusable;
        }

        @Override
        public boolean isFocused() {
            return focused;
        }

        @Override
        public void setFocused(boolean focused) {
            log.add(name + (focused ? "+" : "-"));
            this.focused = focused;
        }

        @Override
        public Dim2i getDimensions() {
            return dim;
        }
    }

    @Test
    void theFocusIsFoundThroughNestedContainersAndClearedOnlyWhereItIs() {
        List<String> log = new ArrayList<>();
        Leaf a = new Leaf("a", log, 0);
        Leaf b = new Leaf("b", log, 20);
        Leaf c = new Leaf("c", log, 40);
        InteractableContainer inner = container(b, c);
        InteractableContainer root = container(a, Mockito.mock(Interactable.class), inner, container());
        assertNull(root.getFocusedChild());
        assertNull(root.getFocusedControl());
        c.focused = true;
        assertSame(inner, root.getFocusedChild());
        assertSame(c, inner.getFocusedChild());
        assertSame(c, root.getFocusedControl());
        assertSame(c, inner.getFocusedControl());
        // Containers never make two at once, but should it happen the first in order is the one found
        a.focused = true;
        assertSame(a, root.getFocusedChild());
        assertSame(a, root.getFocusedControl());
        root.clearFocus();
        assertFalse(a.focused || b.focused || c.focused);
        assertEquals(List.of("a-", "c-"), log);
        root.clearFocus();
        assertEquals(2, log.size());
    }

    @Test
    void moveFocusStepsThroughFocusableControlsInTreeOrderAndRunsOffEitherEnd() {
        List<String> log = new ArrayList<>();
        Leaf a = new Leaf("a", log, 0);
        Leaf skipped = new Leaf("skipped", log, 10);
        skipped.focusable = false;
        Leaf c = new Leaf("c", log, 20);
        Leaf e = new Leaf("e", log, 30);
        Leaf d = new Leaf("d", log, 40);
        InteractableContainer inner = container(c, Mockito.mock(Interactable.class), e);
        InteractableContainer root = container(a, skipped, container(), inner, d);

        List<String> forward = new ArrayList<>();
        while (root.moveFocus(true)) {
            forward.add(((Leaf) root.getFocusedControl()).name);
        }
        assertEquals(List.of("a", "c", "e", "d"), forward);
        assertNull(root.getFocusedControl());
        // Going backwards a container is entered at its last control
        List<String> backward = new ArrayList<>();
        while (root.moveFocus(false)) {
            backward.add(((Leaf) root.getFocusedControl()).name);
        }
        assertEquals(List.of("d", "e", "c", "a"), backward);
        assertNull(root.getFocusedControl());
        // Each step unfocused the control it left, so the log pairs up and never shows two at once
        assertEquals(List.of("a+", "a-", "c+", "c-", "e+", "e-", "d+", "d-", "d+", "d-", "e+", "e-", "c+", "c-", "a+", "a-"), log);

        // A control that can no longer take focus while it has it is still left normally
        assertTrue(root.moveFocus(true));
        a.focusable = false;
        assertTrue(root.moveFocus(true));
        assertSame(c, root.getFocusedControl());
        assertFalse(a.focused);
        assertFalse(container().moveFocus(true));
        assertFalse(container(skipped).moveFocus(false));
    }

    @Test
    void keysGoToTheFocusedChildFirstAndThenToEveryOtherChildOnce() {
        List<String> log = new ArrayList<>();
        FakeInteractionContext ctx = new FakeInteractionContext();
        Leaf first = new Leaf("first", log, 0);
        Leaf deep = new Leaf("deep", log, 10);
        Leaf last = new Leaf("last", log, 20);
        InteractableContainer root = container(first, container(deep), last);
        last.focused = true;
        last.claims = "x";
        // The focused control takes its key before the earlier children are asked
        assertTrue(root.keyTyped(ctx, 'x', 0));
        assertEquals(List.of("last x"), log);
        log.clear();
        // One it refuses goes round the rest in order, the focused one not asked twice
        assertFalse(root.keyTyped(ctx, 'y', 0));
        assertEquals(List.of("last y", "first y", "deep y"), log);

        last.focused = false;
        deep.focused = true;
        deep.claims = "z";
        log.clear();
        assertTrue(root.keyTyped(ctx, 'z', 0));
        assertEquals(List.of("deep z"), log);
        log.clear();
        assertFalse(root.keyTyped(ctx, 'w', 0));
        assertEquals(List.of("deep w", "first w", "last w"), log);
        // With nothing focused the key goes round every child in order, as before focus existed
        deep.focused = false;
        log.clear();
        assertFalse(root.keyTyped(ctx, 'v', 0));
        assertEquals(List.of("first v", "deep v", "last v"), log);
    }

    @Test
    void dispatchMovesFocusOnATabNoControlTookWrappingRoundAndShiftGoesBack() {
        List<String> log = new ArrayList<>();
        FakeInteractionContext ctx = new FakeInteractionContext();
        Leaf a = new Leaf("a", log, 0);
        Leaf b = new Leaf("b", log, 10);
        InteractableContainer root = container(a, container(b));
        assertTrue(root.dispatchKeyTyped(ctx, '\t', Keys.TAB));
        assertSame(a, root.getFocusedControl());
        assertTrue(root.dispatchKeyTyped(ctx, '\0', Keys.TAB));
        assertSame(b, root.getFocusedControl());
        assertTrue(root.dispatchKeyTyped(ctx, '\t', 0));
        assertSame(a, root.getFocusedControl());
        ctx.held.add(InteractionContext.SpecialKey.SHIFT);
        assertTrue(root.dispatchKeyTyped(ctx, '\t', Keys.TAB));
        assertSame(b, root.getFocusedControl());
        assertTrue(root.dispatchKeyTyped(ctx, '\t', Keys.TAB));
        assertSame(a, root.getFocusedControl());
        ctx.held.clear();
        // The first Tab was offered to every control before it moved the focus
        assertEquals(List.of("a \t", "b \t"), log.subList(0, 2));
        // A control that takes Tab itself keeps the focus
        a.claims = "\t";
        assertTrue(root.dispatchKeyTyped(ctx, '\t', Keys.TAB));
        assertSame(a, root.getFocusedControl());
        // A claimed key is just claimed, and one nobody takes is left to the screen
        a.claims = "\tk";
        assertTrue(root.dispatchKeyTyped(ctx, 'k', 37));
        assertFalse(root.dispatchKeyTyped(ctx, 'j', 36));
        assertSame(a, root.getFocusedControl());
        // With nothing that can take focus Tab goes to the screen too
        Leaf off = new Leaf("off", log, 0);
        off.focusable = false;
        assertFalse(container(off, Mockito.mock(Interactable.class)).dispatchKeyTyped(ctx, '\t', Keys.TAB));
        assertFalse(container().dispatchKeyTyped(ctx, '\0', Keys.TAB));
    }

    @Test
    void aClickFocusesTheControlThatTakesItAndClearsTheFocusEverywhereElse() {
        List<String> log = new ArrayList<>();
        FakeInteractionContext ctx = new FakeInteractionContext();
        Leaf a = new Leaf("a", log, 0);
        Leaf b = new Leaf("b", log, 20);
        Leaf off = new Leaf("off", log, 40);
        off.focusable = false;
        Interactable plain = Mockito.mock(Interactable.class);
        Mockito.when(plain.mouseClicked(Mockito.any(), Mockito.eq(65.0), Mockito.anyDouble(), Mockito.anyInt())).thenReturn(true);
        InteractableContainer inner = container(b);
        InteractableContainer root = container(a, inner, off, plain);
        a.focused = true;
        assertTrue(root.mouseClicked(ctx, 25, 5, 0));
        assertSame(b, root.getFocusedControl());
        assertSame(inner, root.getCapturedChild());
        assertFalse(a.focused);
        // A control that cannot take focus still takes the click, and the focus is gone
        assertTrue(root.mouseClicked(ctx, 45, 5, 0));
        assertNull(root.getFocusedControl());
        assertTrue(root.mouseClicked(ctx, 5, 5, 0));
        assertSame(a, root.getFocusedControl());
        // So does a click on something that is not focusable at all, or on nothing
        assertTrue(root.mouseClicked(ctx, 65, 5, 0));
        assertNull(root.getFocusedControl());
        assertTrue(root.mouseClicked(ctx, 5, 5, 1));
        assertSame(a, root.getFocusedControl());
        assertFalse(root.mouseClicked(ctx, 500, 500, 0));
        assertNull(root.getFocusedControl());
    }

    @Test
    void aFocusableTakesFocusUnlessItSaysOtherwise() {
        Focusable minimal = new Focusable() {
            @Override
            public boolean isMouseOver(double mouseX, double mouseY) {
                return false;
            }

            @Override
            public boolean isFocused() {
                return false;
            }

            @Override
            public void setFocused(boolean focused) {
            }

            @Override
            public Dim2i getDimensions() {
                return null;
            }
        };
        assertTrue(minimal.canFocus());
        assertFalse(minimal.keyTyped(new FakeInteractionContext(), ' ', Keys.SPACE));
    }
}
