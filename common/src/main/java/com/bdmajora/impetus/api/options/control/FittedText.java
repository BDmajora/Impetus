package com.bdmajora.impetus.api.options.control;

import com.bdmajora.impetus.engine.impl.gui.framework.DrawContext;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

// Text drawn beside the option name and cut with an ellipsis to fit the room it is given
final class FittedText {
    // Text appended to a cut
    private static final String ELLIPSIS = "...";
    // Most cuts tried on text whose styles widen it
    private static final int MAX_CUTS = 2;
    // Deepest component nesting copied before a component is rejected
    private static final int MAX_DEPTH = 256;

    // Whether any text has been fitted yet
    private boolean fitted;
    // Text generation and source and value and state and room of the last fit
    private long generation;
    private String source;
    private int value;
    private boolean disabled;
    private int room;
    // Text to draw or null when the ellipsis alone does not fit
    private TextComponent text;
    // Width of the text to draw
    private int width;

    // Returns true when the text was last fitted for exactly these arguments
    boolean isFittedFor(long generation, String source, int value, boolean disabled, int room) {
        return this.fitted && this.generation == generation && this.value == value && this.disabled == disabled && this.room == room
                && Objects.equals(this.source, source);
    }

    // Fits the styled text into the room and stores it with the arguments it was fitted for
    void fit(DrawContext context, long generation, String source, int value, boolean disabled, int room, TextComponent styled) {
        Objects.requireNonNull(context, "Draw context must not be null");
        TextComponent text = null;
        int width = 0;
        if (styled != null && room > 0) {
            int full = context.getStringWidth(styled);
            if (fits(full, room)) {
                // Stores a copy of the styled text
                text = copied(styled);
                width = full;
            } else {
                Cut cut = cutToFit(context, context.extractString(styled), styled, room);
                if (cut != null) {
                    text = cut.styled();
                    width = cut.width();
                }
            }
        }
        // Stores the fitted text and the arguments it was fitted for
        this.text = text;
        this.width = width;
        this.generation = generation;
        this.source = source;
        this.value = value;
        this.disabled = disabled;
        this.room = room;
        this.fitted = true;
    }

    // Returns the fitted text or null when nothing fits
    TextComponent text() {
        return this.text;
    }

    // Returns the width of the fitted text
    int width() {
        return this.width;
    }

    // Cut that fits holding the plain text with its ellipsis and the styled text and its width
    record Cut(String text, TextComponent styled, int width) {
    }

    // Returns the cut of the text whose styled form fits the room or null when no cut fits
    static Cut cutToFit(DrawContext context, String text, TextComponent styled, int room) {
        int budget = room;
        for (int cuts = 1; ; cuts++) {
            String cut = cut(context, text, ELLIPSIS, budget);
            if (cut.isEmpty()) {
                return null;
            }
            TextComponent restyled = restyled(styled, TextComponent.literal(cut));
            int cutWidth = context.getStringWidth(restyled);
            // Returns the cut when its styled width fits the room
            if (fits(cutWidth, room)) {
                return new Cut(cut, restyled, cutWidth);
            }
            // Returns null for a negative width or after the last cut
            if (cutWidth < 0 || cuts == MAX_CUTS) {
                return null;
            }
            // Shrinks the budget by the overflow and keeps it below the previous budget
            budget = Math.clamp((long) context.getStringWidth(cut) - (cutWidth - room), Integer.MIN_VALUE, budget - 1);
        }
    }

    // Returns the text cut to leave room for the suffix followed by the suffix or the suffix alone or an empty string when the suffix does not fit
    static String cut(DrawContext context, String text, String suffix, int room) {
        int suffixWidth = context.getStringWidth(suffix);
        if (!fits(suffixWidth, room)) {
            return "";
        }
        if (text == null || text.isEmpty()) {
            return suffix;
        }
        String cut = context.substrByWidth(text, room - suffixWidth);
        if (cut == null) {
            return suffix;
        }
        int end = cut.length();
        // Drops a high surrogate left at the end of the cut
        if (end > 0 && Character.isHighSurrogate(cut.charAt(end - 1))) {
            end--;
        }
        // Removes trailing spaces from the cut before appending the suffix
        String fitted = cut.substring(0, end).stripTrailing() + suffix;
        // Returns the suffix alone when the cut text still does not fit
        return fits(context.getStringWidth(fitted), room) ? fitted : suffix;
    }

    // Returns true when the width is zero or more and no larger than the room
    static boolean fits(int width, int room) {
        return width >= 0 && width <= room;
    }

    // Returns a deep copy of the component with every style set and key list and argument list copied
    static TextComponent copied(TextComponent component) {
        return copied(component, 0);
    }

    // Copies one layer at the given depth and rejects a component nested too deeply
    private static TextComponent copied(TextComponent component, int depth) {
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException("Text component nested more than " + MAX_DEPTH + " deep");
        }
        if (component instanceof TextComponent.Styled styled) {
            return new TextComponent.Styled(copied(styled.inner(), depth + 1), EnumSet.copyOf(styled.styles()));
        }
        if (component instanceof TextComponent.Translatable translatable) {
            // Copies the arguments into a new array
            Object[] args = translatable.args().toArray();
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof TextComponent arg) {
                    args[i] = copied(arg, depth + 1);
                }
            }
            // Copies the keys and wraps the arguments in an unmodifiable list that allows nulls
            return new TextComponent.Translatable(List.copyOf(translatable.keys()), Collections.unmodifiableList(Arrays.asList(args)));
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