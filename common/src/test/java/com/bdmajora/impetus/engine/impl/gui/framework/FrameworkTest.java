package com.bdmajora.impetus.engine.impl.gui.framework;

import com.bdmajora.testing.FakeDrawContext;
import com.bdmajora.testing.FakeInteractionContext;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

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
        assertFalse(none.keyTyped('a', 1));
        InteractionContext silent = new InteractionContext() {};
        silent.playClickSound();
        assertFalse(silent.isSpecialKeyDown(InteractionContext.SpecialKey.CTRL));
        assertEquals(3, InteractionContext.SpecialKey.values().length);
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
        Mockito.when(hit.keyTyped(Mockito.anyChar(), Mockito.anyInt())).thenReturn(true);
        InteractableContainer container = () -> Stream.of(miss, hit);
        FakeInteractionContext ctx = new FakeInteractionContext();
        assertTrue(container.isMouseOver(1, 1));
        assertTrue(container.mouseClicked(ctx, 1, 1, 0));
        assertTrue(container.mouseReleased(ctx, 1, 1, 0));
        assertTrue(container.mouseDragged(ctx, 1, 1, 0, 0, 0));
        assertTrue(container.mouseScrolled(ctx, 1, 1, 0, 1));
        assertTrue(container.keyTyped('a', 1));
        InteractableContainer empty = Stream::empty;
        assertFalse(empty.isMouseOver(1, 1));
        assertFalse(empty.mouseClicked(ctx, 1, 1, 0));
        assertFalse(empty.mouseDragged(ctx, 1, 1, 0, 0, 0));
        assertFalse(empty.mouseScrolled(ctx, 1, 1, 0, 0));
        Mockito.verify(miss).mouseClicked(ctx, 1, 1, 0);
        Mockito.verify(miss, Mockito.never()).mouseDragged(Mockito.any(), Mockito.anyDouble(), Mockito.anyDouble(), Mockito.anyInt(), Mockito.anyDouble(), Mockito.anyDouble());
    }
}
