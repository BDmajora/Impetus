package com.bdmajora.impetus.api.config.theme;

import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

// A mod's id and the accent colour of its config screen.
public record ConfigTheme(String modId, int accentColor) {
    // Solid teal is set as the default accent colour
    public static final int DEFAULT_ACCENT_COLOR = 0xFF00CBCB;

    // Upper case hex format.
    private static final HexFormat UPPER_HEX = HexFormat.of().withUpperCase();

    // Runs on every new theme to normalise the id and correct a zero alpha before the values are stored
    public ConfigTheme {
        modId = canonicalModId(modId);

        // Sets the alpha to FF when it is 00.
        if ((accentColor & 0xFF000000) == 0) {
            accentColor |= 0xFF000000;
        }
    }

    // Returns a builder for this mod.
    public static Builder builder(String modId) {
        return new Builder(modId);
    }

    // Returns the trimmed lower case id and rejects null or blank ids.
    public static String canonicalModId(String modId) {
        // Rejects a null id.
        Objects.requireNonNull(modId, "Mod id must not be null");
        String canonical = canonicalModIdOrNull(modId);
        // Null comes back only when every character was whitespace or the id was empty
        if (canonical == null) {
            throw new IllegalArgumentException("Mod id must not be blank");
        }
        return canonical;
    }

    // Returns the canonical id or null for a blank id.
    static String canonicalModIdOrNull(String modId) {
        // Walks start and end in past the whitespace at both ends.
        int start = 0;
        int end = modId.length();
        // Moves start forward past any leading whitespace
        while (start < end && Character.isWhitespace(modId.charAt(start))) {
            start++;
        }
        // Returns null when the id was empty or all whitespace.
        if (start == end) {
            return null;
        }
        // Moves end back past the trailing whitespace.
        while (Character.isWhitespace(modId.charAt(end - 1))) {
            end--;
        }
        // Checks each kept character to see whether lower-casing would change anything
        for (int i = start; i < end; i++) {
            char c = modId.charAt(i);
            // Lower cases the id through the JDK with ROOT when it holds upper case or non ASCII characters.
            if (c >= 0x80 || (c >= 'A' && c <= 'Z')) {
                return modId.substring(start, end).toLowerCase(Locale.ROOT);
            }
        }
        // Returns the trimmed id.
        return modId.substring(start, end);
    }

    // Prints the colour as eight hex digits.
    @Override
    public String toString() {
        return "ConfigTheme[modId=" + this.modId + ", accentColor=0x" + UPPER_HEX.toHexDigits(this.accentColor) + "]";
    }

    // Holds the id and colour until build() is called
    public static final class Builder {
        private final String modId;
        private int accentColor = DEFAULT_ACCENT_COLOR;

        // Validates and normalises the id.
        private Builder(String modId) {
            this.modId = canonicalModId(modId);
        }

        // Sets the colour and returns the builder so calls can be chained
        public Builder accentColor(int color) {
            this.accentColor = color;
            return this;
        }

        // Creates the ConfigTheme.
        public ConfigTheme build() {
            return new ConfigTheme(this.modId, this.accentColor);
        }
    }
}