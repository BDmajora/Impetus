package com.bdmajora.impetus.engine.impl.gui.framework;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

// Text that a font renders and a translation can replace.
public interface TextComponent {
    // Returns the nested components of this component.
    default List<TextComponent> children() {
        return List.of();
    }

    // Text without translation.
    record Literal(String text) implements TextComponent {
        // Returns the text.
        @Override
        public @NotNull String toString() {
            return text;
        }
    }

    // Text looked up by language keys.
    record Translatable(List<String> keys, List<?> args) implements TextComponent {
        // Returns the key followed by the arguments.
        @Override
        public @NotNull String toString() {
            String keyString = keys.size() == 1 ? keys.getFirst() : keys.toString();
            if (args.isEmpty()) {
                return keyString;
            }
            return keyString + args;
        }
    }

    // Component with formatting styles.
    record Styled(TextComponent inner, EnumSet<TextFormattingStyle> styles) implements TextComponent {
        // Returns the styled component.
        @Override
        public List<TextComponent> children() {
            return List.of(inner);
        }

        // Returns a copy with more styles.
        @Override
        public TextComponent withStyle(TextFormattingStyle style, TextFormattingStyle... rest) {
            var newSet = EnumSet.copyOf(styles);
            newSet.add(style);
            newSet.addAll(Arrays.asList(rest));
            return new Styled(inner, newSet);
        }

        // Returns the inner text followed by a style marker.
        @Override
        public @NotNull String toString() {
            return inner.toString() + "[styled]";
        }
    }

    // Returns a styled copy with the given styles.
    default TextComponent withStyle(TextFormattingStyle style, TextFormattingStyle... rest) {
        return new Styled(this, EnumSet.of(style, rest));
    }

    // Creates a component from plain text.
    static TextComponent literal(String text) {
        return new Literal(text);
    }

    // Creates a component from a language key and arguments.
    static TextComponent translatable(String key, Object... args) {
        return new Translatable(List.of(key), Arrays.asList(args));
    }

    // Creates a component from language keys and arguments and uses the first key with a translation.
    static TextComponent translatable(List<String> keys, Object... args) {
        return new Translatable(keys, Arrays.asList(args));
    }
}
