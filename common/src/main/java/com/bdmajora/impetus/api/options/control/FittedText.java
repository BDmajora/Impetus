package com.bdmajora.impetus.api.options.control;

import com.bdmajora.impetus.engine.impl.gui.framework.DrawContext;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;

import java.util.EnumSet;

// Text a control draws beside the option's name, cut with an ellipsis to the room it is given and kept until its source, state, room or its row's text generation changes
final class FittedText {
    // Text appended to a cut
    private static final String ELLIPSIS = "...";

    // Row text generation, source, value, state and room the text was last fitted for, the generation starting below any a row hands out
    private long generation;
    private Object source;
    private int value;
    private boolean disabled;
    private int room;
    // Text to draw, or null when not even the ellipsis fits, and its width
    private TextComponent text;
    private int width;

    // Returns true when the text was last fitted for exactly these, an equal source counting as the same and compared last as the only check that is not constant time
    boolean isFittedFor(long generation, Object source, int value, boolean disabled, int room) {
        return this.generation == generation && this.value == value && this.disabled == disabled && this.room == room
                && (this.source == source || source != null && source.equals(this.source));
    }

    // Fits the styled text into the room in the given font source, keeping its styles on a cut, and stores it with what it was fitted for
    void fit(DrawContext context, long generation, Object source, int value, boolean disabled, int room, TextComponent styled) {
        TextComponent text = null;
        int width = 0;
        if (styled != null && room > 0) {
            int full = context.getStringWidth(styled);
            if (fits(full, room)) {
                text = styled;
                width = full;
            } else {
                String extracted = context.extractString(styled);
                String cut = cut(context, extracted != null ? extracted : "", ELLIPSIS, room);
                if (!cut.isEmpty()) {
                    TextComponent restyled = restyled(styled, TextComponent.literal(cut));
                    int cutWidth = context.getStringWidth(restyled);
                    // Kept only when the styled cut still fits, which styles that change widths could stop it doing
                    if (fits(cutWidth, room)) {
                        text = restyled;
                        width = cutWidth;
                    }
                }
            }
        }
        // Stored after the fit so one that throws leaves the previous text with what it was made for
        this.text = text;
        this.width = width;
        this.generation = generation;
        this.source = source;
        this.value = value;
        this.disabled = disabled;
        this.room = room;
    }

    // Returns the fitted text, or null when nothing fits
    TextComponent text() {
        return this.text;
    }

    // Returns the width of the fitted text
    int width() {
        return this.width;
    }

    // Returns the text cut to leave room for the suffix and then the suffix, the suffix alone when the font source cuts too little, or an empty string when not even the suffix fits
    static String cut(DrawContext context, String text, String suffix, int room) {
        int suffixWidth = context.getStringWidth(suffix);
        if (!fits(suffixWidth, room)) {
            return "";
        }
        String cut = context.substrByWidth(text, room - suffixWidth);
        if (cut == null) {
            return suffix;
        }
        int end = cut.length();
        // Drops the first half of a character the cut split in two
        if (end > 0 && Character.isHighSurrogate(cut.charAt(end - 1))) {
            end--;
        }
        // Removes trailing spaces from the cut before appending the suffix
        String fitted = cut.substring(0, end).stripTrailing() + suffix;
        // Keeps the suffix alone when the font source cut less than it was asked to
        return fits(context.getStringWidth(fitted), room) ? fitted : suffix;
    }

    // Returns true for a measured width that fits the room, a negative one from a bad font source never counting as fitting
    static boolean fits(int width, int room) {
        return width >= 0 && width <= room;
    }

    // Returns the component with every style layer's set copied, so a set the caller changes afterwards cannot reach a control or the draw context's caches keyed on it
    static TextComponent copied(TextComponent component) {
        if (component instanceof TextComponent.Styled styled) {
            return new TextComponent.Styled(copied(styled.inner()), EnumSet.copyOf(styled.styles()));
        }
        return component;
    }

    // Wraps the text in each style layer of the original in the same order
    static TextComponent restyled(TextComponent original, TextComponent text) {
        if (original instanceof TextComponent.Styled styled) {
            return new TextComponent.Styled(restyled(styled.inner(), text), EnumSet.copyOf(styled.styles()));
        }
        return text;
    }
}
