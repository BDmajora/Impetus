package com.bdmajora.impetus.engine.impl.gui.framework;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Tests component copying and text output.
class TextComponentTest {
    // Rejects null keys and null argument arrays.
    @Test
    void nullsAreRejected() {
        assertThrows(NullPointerException.class, () -> TextComponent.translatable((List<String>) null).toString());
        assertThrows(NullPointerException.class, () -> TextComponent.translatable("k", (Object[]) null));
    }

    // Prints an empty key list and null arguments.
    @Test
    void unusualTranslatables() {
        assertEquals("[]", TextComponent.translatable(List.of()).toString());
        assertEquals("k[null]", TextComponent.translatable("k", (Object) null).toString());
        assertEquals("k", TextComponent.translatable("k").toString());
        assertEquals(TextComponent.translatable("k", 1), TextComponent.translatable("k", 1));
    }

    // Keeps a null literal text and prints it as null.
    @Test
    void literalKeepsText() {
        assertEquals("", TextComponent.literal("").toString());
        assertNull(new TextComponent.Literal(null).text());
        assertEquals(new TextComponent.Literal("a"), TextComponent.literal("a"));
    }

    // Adds styles without changing the original.
    @Test
    void withStyleReturnsCopies() {
        TextComponent base = TextComponent.literal("x");
        TextComponent.Styled a = (TextComponent.Styled) base.withStyle(TextFormattingStyle.RED);
        TextComponent.Styled b = (TextComponent.Styled) a.withStyle(TextFormattingStyle.RED, TextFormattingStyle.BLUE);
        assertEquals(EnumSet.of(TextFormattingStyle.RED), a.styles());
        assertEquals(EnumSet.of(TextFormattingStyle.RED, TextFormattingStyle.BLUE), b.styles());
        assertThrows(NullPointerException.class, () -> base.withStyle(null));
        assertThrows(NullPointerException.class, () -> a.withStyle(null));
        assertThrows(NullPointerException.class, () -> a.withStyle(TextFormattingStyle.RED, (TextFormattingStyle) null));
        assertEquals("x[styled]", base.withStyle(TextFormattingStyle.RED).withStyle(TextFormattingStyle.BLUE).toString());
    }

    // Marks exactly the first sixteen styles as colors.
    @Test
    void colorFlags() {
        int colors = 0;
        for (TextFormattingStyle style : TextFormattingStyle.values()) {
            if (style.isColor()) {
                colors++;
            }
        }
        assertEquals(16, colors);
        assertTrue(TextFormattingStyle.WHITE.isColor());
        assertFalse(TextFormattingStyle.STRIKETHROUGH.isColor());
    }

    // Tests the key helpers.
    @Test
    void keyHelpers() {
        assertTrue(Keys.isTab('\t', 0));
        assertTrue(Keys.isTab('\0', Keys.TAB));
        assertFalse(Keys.isTab('a', 0));
        for (char c : new char[]{'\r', '\n', ' '}) {
            assertTrue(Keys.isPress(c, 0));
        }
        for (int code : new int[]{Keys.RETURN, Keys.NUMPAD_ENTER, Keys.SPACE}) {
            assertTrue(Keys.isPress('\0', code));
        }
        assertFalse(Keys.isPress('a', Keys.LEFT));
        assertFalse(Keys.isPress('\0', Keys.RIGHT));
        assertFalse(Keys.isPress('\0', -1));
    }
}
