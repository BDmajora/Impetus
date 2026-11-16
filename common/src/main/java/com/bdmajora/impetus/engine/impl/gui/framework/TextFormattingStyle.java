package com.bdmajora.impetus.engine.impl.gui.framework;

// Text colors and modifiers.
public enum TextFormattingStyle {
    BLACK,
    DARK_BLUE,
    DARK_GREEN,
    DARK_AQUA,
    DARK_RED,
    DARK_PURPLE,
    GOLD,
    GRAY,
    DARK_GRAY,
    BLUE,
    GREEN,
    AQUA,
    RED,
    LIGHT_PURPLE,
    YELLOW,
    WHITE,
    STRIKETHROUGH,
    UNDERLINE,
    ITALIC;

    // Returns whether the style is a color.
    public boolean isColor() {
        return this.ordinal() < 16;
    }
}
