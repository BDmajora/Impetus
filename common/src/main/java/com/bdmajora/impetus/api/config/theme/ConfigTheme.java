package com.bdmajora.impetus.api.config.theme;

import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

// Stores a mod's id and the accent colour for its config screen, created through the nested Builder
public record ConfigTheme(String modId, int accentColor) {
    // Solid teal is set as the default accent colour
    public static final int DEFAULT_ACCENT_COLOR = 0xFF00CBCB;

    // Kept as a constant because withUpperCase() makes a new format on every call
    private static final HexFormat UPPER_HEX = HexFormat.of().withUpperCase();

    // Runs on every new theme to normalise the id and correct a zero alpha before the values are stored
    public ConfigTheme {
        modId = canonicalModId(modId);

        // An alpha of 00 makes the colour invisible, so it is set to FF
        if ((accentColor & 0xFF000000) == 0) {
            accentColor |= 0xFF000000;
        }
    }

    // Returns a builder for this mod, where the colour is set before calling build()
    public static Builder builder(String modId) {
        return new Builder(modId);
    }

    // Rejects null or blank ids, otherwise trims spaces and lower-cases
    public static String canonicalModId(String modId) {
        // Throws straight away if the id is null, before any characters are read
        Objects.requireNonNull(modId, "Mod id must not be null");
        // Walks in from both ends past the same whitespace isBlank() and strip() look for, so the id is scanned once
        // start and end mark the part of the id that is kept, beginning as the whole string
        int start = 0;
        int end = modId.length();
        // Moves start forward past any leading whitespace
        while (start < end && Character.isWhitespace(modId.charAt(start))) {
            start++;
        }
        // If start reached the end, every character was whitespace or the id was empty
        if (start == end) {
            throw new IllegalArgumentException("Mod id must not be blank");
        }
        // Moves end back past any trailing whitespace, with no bounds check needed since a non-whitespace character is known to exist
        while (Character.isWhitespace(modId.charAt(end - 1))) {
            end--;
        }
        // Checks each kept character to see whether lower-casing would change anything
        for (int i = start; i < end; i++) {
            char c = modId.charAt(i);
            // Upper-case ASCII or anything outside ASCII goes to the JDK, with ROOT so the system language cannot change the result
            if (c >= 0x80 || (c >= 'A' && c <= 'Z')) {
                return modId.substring(start, end).toLowerCase(Locale.ROOT);
            }
        }
        // Every kept character is already lower-case ASCII, so only the trim is applied
        // substring returns the same string when nothing was trimmed, so an id that is already clean allocates nothing
        return modId.substring(start, end);
    }

    // Prints the colour as eight hex digits instead of a negative number, joined with + because formatted() parses its pattern on every call
    @Override
    public String toString() {
        return "ConfigTheme[modId=" + this.modId + ", accentColor=0x" + UPPER_HEX.toHexDigits(this.accentColor) + "]";
    }

    // Holds the id and colour until build() is called
    public static final class Builder {
        private final String modId;
        private int accentColor = DEFAULT_ACCENT_COLOR;

        // Validates and normalises the id when the builder is created, so invalid ids are rejected before build()
        private Builder(String modId) {
            this.modId = canonicalModId(modId);
        }

        // Sets the colour and returns the builder so calls can be chained
        public Builder accentColor(int color) {
            this.accentColor = color;
            return this;
        }

        // Creates the ConfigTheme; the record re-checks the stored id, which is already clean, so an ASCII id costs one scan and no copy
        public ConfigTheme build() {
            return new ConfigTheme(this.modId, this.accentColor);
        }
    }
}