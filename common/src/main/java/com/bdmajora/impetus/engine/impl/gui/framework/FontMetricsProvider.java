package com.bdmajora.impetus.engine.impl.gui.framework;

import java.util.List;

// Measures and splits text.
public interface FontMetricsProvider {
    // Returns the width of a string.
    default int getStringWidth(String str) {
        return getStringWidth(new TextComponent.Literal(str));
    }

    // Returns the width of a component.
    int getStringWidth(TextComponent component);

    // Returns the longest start of a string that fits the width.
    String substrByWidth(String str, int maxWidth);

    // Splits a component into lines that fit the width.
    List<TextComponent> split(TextComponent component, int maxWidth);

    // Returns the plain text of a component.
    String extractString(TextComponent component);

    // Returns the height of one line.
    int lineHeight();
}
