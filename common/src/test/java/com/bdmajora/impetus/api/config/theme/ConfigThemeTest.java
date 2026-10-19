package com.bdmajora.impetus.api.config.theme;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.*;

class ConfigThemeTest {
    @Test
    void builderDefaultsAndExplicitColour() {
        ConfigTheme theme = ConfigTheme.builder("themed").build();
        assertEquals("themed", theme.modId());
        assertEquals(ConfigTheme.DEFAULT_ACCENT_COLOR, theme.accentColor());
        assertEquals(0xFF00CBCB, ConfigTheme.DEFAULT_ACCENT_COLOR);
        ConfigTheme custom = ConfigTheme.builder("themed").accentColor(0xFF123456).build();
        assertEquals(0xFF123456, custom.accentColor());
        assertEquals("themed", custom.modId());
    }

    @Test
    void nullAndBlankModIdsAreRejectedByBuilderAndConstructor() {
        // The builder fails on creation, before build() is reached
        assertEquals("Mod id must not be null", assertThrows(NullPointerException.class, () -> ConfigTheme.builder(null)).getMessage());
        assertEquals("Mod id must not be blank", assertThrows(IllegalArgumentException.class, () -> ConfigTheme.builder("")).getMessage());
        assertThrows(IllegalArgumentException.class, () -> ConfigTheme.builder(" \t\n"));
        assertEquals("Mod id must not be null", assertThrows(NullPointerException.class, () -> new ConfigTheme(null, 0xFF00CBCB)).getMessage());
        assertEquals("Mod id must not be blank", assertThrows(IllegalArgumentException.class, () -> new ConfigTheme("  ", 0xFF00CBCB)).getMessage());
        assertThrows(NullPointerException.class, () -> ConfigTheme.canonicalModId(null));
        assertThrows(IllegalArgumentException.class, () -> ConfigTheme.canonicalModId(" "));
    }

    @Test
    void modIdsAreTrimmedAndLowerCased() {
        assertEquals("mymod", new ConfigTheme(" MyMod ", 0).modId());
        assertEquals("mymod", ConfigTheme.builder("MYMOD\n").build().modId());
        assertEquals("my_mod-2", ConfigTheme.canonicalModId("My_Mod-2"));
        // strip() rather than trim(), so Unicode spaces such as the em space go too
        assertEquals("mymod", ConfigTheme.canonicalModId(" mymod "));
        // Inner whitespace is part of the id and stays
        assertEquals("my mod", ConfigTheme.canonicalModId("My Mod"));
        assertEquals(new ConfigTheme("mymod", 0xFF123456), new ConfigTheme("MyMod", 0xFF123456));
    }

    @Test
    void lowerCasingIgnoresTheDefaultLocale() {
        Locale previous = Locale.getDefault();
        try {
            // Turkish lower-cases "I" to a dotless "ı", which would split "IMPETUS" from "impetus"
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals("impetus", ConfigTheme.canonicalModId("IMPETUS"));
            assertEquals("impetus", new ConfigTheme("IMPETUS", 0).modId());
            // The non-ASCII path is pinned to ROOT as well, where Turkish would drop the combining dot from a dotted capital I
            assertEquals("id", "\u0130D".toLowerCase(Locale.getDefault()));
            assertEquals("i\u0307d", ConfigTheme.canonicalModId("\u0130D"));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void alphaZeroAccentsAreMadeOpaque() {
        assertEquals(0xFF123456, new ConfigTheme("themed", 0x123456).accentColor());
        assertEquals(0xFF123456, ConfigTheme.builder("themed").accentColor(0x123456).build().accentColor());
        assertEquals(0xFF000000, new ConfigTheme("themed", 0).accentColor());
        // Any non-zero alpha is the caller's choice and is kept
        assertEquals(0x80123456, new ConfigTheme("themed", 0x80123456).accentColor());
        assertEquals(0x01123456, new ConfigTheme("themed", 0x01123456).accentColor());
        // Normalised before equality, so the RGB and ARGB spellings are the same theme
        assertEquals(new ConfigTheme("themed", 0xFF123456), new ConfigTheme("themed", 0x123456));
    }

    @Test
    void builderStaysIndependentOfWhatItBuilt() {
        ConfigTheme.Builder builder = ConfigTheme.builder("themed").accentColor(0xFF111111);
        ConfigTheme first = builder.build();
        ConfigTheme second = builder.accentColor(0xFF222222).build();
        assertEquals(0xFF111111, first.accentColor());
        assertEquals(0xFF222222, second.accentColor());
        assertEquals("themed", second.modId());
        // Each build() is a fresh theme, equal by value
        assertEquals(builder.build(), builder.build());
        assertNotSame(builder.build(), builder.build());
        assertSame(builder, builder.accentColor(0xFF333333));
    }

    @Test
    void equalityCoversBothComponents() {
        ConfigTheme built = ConfigTheme.builder("themed").accentColor(0xFF123456).build();
        ConfigTheme constructed = new ConfigTheme("themed", 0xFF123456);
        assertEquals(built, constructed);
        assertEquals(built.hashCode(), constructed.hashCode());
        assertNotEquals(built, new ConfigTheme("themed", 0xFF654321));
        assertNotEquals(built, new ConfigTheme("other", 0xFF123456));
        assertNotEquals(built, null);
        assertNotEquals(built, "themed");
    }

    @Test
    void equalsAndHashCodeKeepTheObjectContract() {
        // Three routes to the same theme: builder, plain constructor, and an RGB colour with a messy id
        ConfigTheme a = ConfigTheme.builder("themed").accentColor(0xFF123456).build();
        ConfigTheme b = new ConfigTheme("themed", 0xFF123456);
        ConfigTheme c = new ConfigTheme(" Themed ", 0x123456);
        assertEquals(a, a);
        assertTrue(a.equals(b) && b.equals(a));
        assertTrue(a.equals(b) && b.equals(c) && a.equals(c));
        // Repeated calls give the same answer
        for (int i = 0; i < 3; i++) {
            assertEquals(a, c);
            assertEquals(a.hashCode(), c.hashCode());
        }
        assertEquals(a.hashCode(), b.hashCode());
        assertFalse(a.equals(null));
    }

    @Test
    void toStringPrintsTheColourAsPaddedHex() {
        assertEquals("ConfigTheme[modId=themed, accentColor=0xFF00CBCB]", ConfigTheme.builder("themed").build().toString());
        // Leading zero nibbles stay so every colour prints as eight digits
        assertEquals("ConfigTheme[modId=themed, accentColor=0x01000A0B]", new ConfigTheme("themed", 0x01000A0B).toString());
        assertEquals("ConfigTheme[modId=mymod, accentColor=0xFF123456]", new ConfigTheme("MyMod", 0x123456).toString());
    }

    @Test
    void publicShapeStaysTheApiOtherModsCompileAgainst() throws NoSuchMethodException {
        // Renaming or reordering a component breaks every mod that calls the constructor or an accessor
        assertTrue(ConfigTheme.class.isRecord());
        RecordComponent[] components = ConfigTheme.class.getRecordComponents();
        assertEquals(List.of("modId", "accentColor"), Arrays.stream(components).map(RecordComponent::getName).toList());
        assertEquals(List.of(String.class, int.class), Arrays.stream(components).map(RecordComponent::getType).toList());
        assertTrue(Modifier.isPublic(ConfigTheme.class.getDeclaredConstructor(String.class, int.class).getModifiers()));
        // The hex format behind toString() stays private, so the default colour is the only public field
        assertEquals(List.of("DEFAULT_ACCENT_COLOR"), Arrays.stream(ConfigTheme.class.getFields()).map(Field::getName).toList());
        // builder() is the only way in, and nothing can subclass the builder
        assertTrue(Modifier.isFinal(ConfigTheme.Builder.class.getModifiers()));
        assertTrue(Modifier.isPrivate(ConfigTheme.Builder.class.getDeclaredConstructor(String.class).getModifiers()));
        // The blank-tolerant variant exists for the registry, so it stays package-private
        Method orNull = ConfigTheme.class.getDeclaredMethod("canonicalModIdOrNull", String.class);
        int modifiers = orNull.getModifiers();
        assertTrue(Modifier.isStatic(modifiers));
        assertFalse(Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers) || Modifier.isPrivate(modifiers));
        assertEquals(String.class, orNull.getReturnType());
    }

    @Test
    void canonicalModIdOrNullAnswersNullWhereCanonicalModIdThrows() {
        for (String blank : List.of("", " ", "\t\n\r\f\u000B", "\u001C\u001D\u001E\u001F", "　", " ".repeat(1000))) {
            assertNull(ConfigTheme.canonicalModIdOrNull(blank));
        }
        // An id with nothing to change comes back as the same instance, which is what lets the registry skip its second lookup
        for (String id : List.of("themed", "a", "my mod", "a\tb", "möd", "οδος", "i̇d", " ", "​")) {
            assertSame(id, ConfigTheme.canonicalModIdOrNull(id));
        }
        // The JDK copies any id holding a surrogate even when nothing changes, so those only come back equal
        assertEquals("𐐨", ConfigTheme.canonicalModIdOrNull("𐐨"));
        assertEquals("\uD800", ConfigTheme.canonicalModIdOrNull("\uD800"));
        assertEquals("mymod", ConfigTheme.canonicalModIdOrNull(" MyMod\t"));
        assertEquals("οδος", ConfigTheme.canonicalModIdOrNull("ΟΔΟΣ"));
        assertEquals("𐐨", ConfigTheme.canonicalModIdOrNull(" 𐐀 "));
        // Ruling out null is the caller's job, which canonicalModId and the registry both do first
        assertThrows(NullPointerException.class, () -> ConfigTheme.canonicalModIdOrNull(null));
    }

    @Test
    void cleanIdsComeBackAsTheSameString() {
        for (String id : List.of("themed", "a", "my_mod-2", "my mod", "0123", "@[`{~", "a\tb")) {
            assertSame(id, ConfigTheme.canonicalModId(id));
            assertSame(id, new ConfigTheme(id, 0).modId());
            assertSame(id, ConfigTheme.builder(id).build().modId());
        }
    }

    @Test
    void everyAsciiCharacterIsKeptExceptUpperCaseLetters() {
        for (char c = 0; c < 0x80; c++) {
            String id = "a" + c + "b";
            if (c >= 'A' && c <= 'Z') {
                assertEquals("a" + (char) (c + 32) + "b", ConfigTheme.canonicalModId(id));
            } else {
                // Inner whitespace and control characters belong to the id, so nothing changes and nothing is copied
                assertSame(id, ConfigTheme.canonicalModId(id));
            }
        }
    }

    @Test
    void fastPathEdgesSitJustOutsideTheUpperCaseRange() {
        // '@' and '[' border 'A' and 'Z', and DEL is the last ASCII character
        assertSame("@id[", ConfigTheme.canonicalModId("@id["));
        assertSame("id\u007F", ConfigTheme.canonicalModId("id\u007F"));
        assertEquals("aid", ConfigTheme.canonicalModId("Aid"));
        assertEquals("idz", ConfigTheme.canonicalModId("idZ"));
        // U+0080 is the first character handed to the JDK, which leaves it alone
        assertEquals("id\u0080", ConfigTheme.canonicalModId("id\u0080"));
        // An upper-case letter found at the very end still lower-cases the whole id
        assertEquals("abcdefghijklmnopqrstuvwxyz", ConfigTheme.canonicalModId("abcdefghijklmnopqrstuvwxyZ"));
        assertEquals("abc", ConfigTheme.canonicalModId("ABC"));
        assertEquals("abc", ConfigTheme.canonicalModId("\t ABC \n"));
    }

    @Test
    void singleCharacterIdsWork() {
        assertSame("a", ConfigTheme.canonicalModId("a"));
        assertEquals("a", ConfigTheme.canonicalModId("A"));
        assertEquals("a", ConfigTheme.canonicalModId(" A "));
        assertEquals("_", ConfigTheme.canonicalModId("\t_\t"));
        assertEquals("é", ConfigTheme.canonicalModId(" É "));
    }

    @Test
    void builderNormalisesOnceAndEveryBuildSharesThatString() throws ReflectiveOperationException {
        ConfigTheme.Builder builder = ConfigTheme.builder("  MyMod\t");
        Field stored = ConfigTheme.Builder.class.getDeclaredField("modId");
        stored.setAccessible(true);
        Object canonical = stored.get(builder);
        assertEquals("mymod", canonical);
        // The record's own pass finds nothing left to change, so the builder's string goes straight through
        assertSame(canonical, builder.build().modId());
        assertSame(canonical, builder.accentColor(0xFF123456).build().modId());
        assertSame(builder.build().modId(), builder.build().modId());
    }

    @Test
    void everyWhitespaceCharacterIsStrippedFromEitherEnd() {
        int found = 0;
        for (int c = 0; c <= Character.MAX_VALUE; c++) {
            if (!Character.isWhitespace(c)) {
                continue;
            }
            found++;
            String pad = String.valueOf((char) c);
            assertEquals("id", ConfigTheme.canonicalModId(pad + "id" + pad));
            assertEquals("id", ConfigTheme.canonicalModId(pad + pad + "ID"));
            assertEquals("i" + pad + "d", ConfigTheme.canonicalModId(pad + "I" + pad + "D" + pad));
            assertEquals("Mod id must not be blank", assertThrows(IllegalArgumentException.class, () -> ConfigTheme.canonicalModId(pad)).getMessage());
            assertEquals("Mod id must not be blank", assertThrows(IllegalArgumentException.class, () -> ConfigTheme.canonicalModId(pad + " " + pad)).getMessage());
        }
        // Five ASCII controls, four separators, fourteen breaking spaces, and the line and paragraph separators
        assertEquals(25, found);
    }

    @Test
    void spaceLikeCharactersJavaDoesNotCountAsWhitespaceAreKept() {
        // No-break spaces, the zero-width space, the byte order mark and NEL all survive strip(), so they survive here too
        for (char c : new char[]{' ', ' ', ' ', '​', '﻿', '\u0085'}) {
            String id = c + "id" + c;
            assertEquals(id, ConfigTheme.canonicalModId(id));
            assertEquals(String.valueOf(c), ConfigTheme.canonicalModId(String.valueOf(c)));
        }
    }

    @Test
    void everyBlankSpellingIsRejectedWithTheSameMessage() {
        for (String blank : List.of("", " ", "\t\n\r\f\u000B", "\u001C\u001D\u001E\u001F", " 　 ", " ".repeat(1000))) {
            assertEquals("Mod id must not be blank", assertThrows(IllegalArgumentException.class, () -> ConfigTheme.canonicalModId(blank)).getMessage());
            assertEquals("Mod id must not be blank", assertThrows(IllegalArgumentException.class, () -> ConfigTheme.builder(blank)).getMessage());
            assertEquals("Mod id must not be blank", assertThrows(IllegalArgumentException.class, () -> new ConfigTheme(blank, 0)).getMessage());
        }
        assertEquals("Mod id must not be null", assertThrows(NullPointerException.class, () -> ConfigTheme.canonicalModId(null)).getMessage());
    }

    @Test
    void caseMappingsOutsideAsciiStillApply() {
        // The Kelvin sign lower-cases to a plain k, so it names the same mod as "key"
        assertEquals("key", ConfigTheme.canonicalModId("Key"));
        assertEquals(new ConfigTheme("key", 0xFF123456), new ConfigTheme("KEY", 0xFF123456));
        // A title-case letter is not isUpperCase, which is why the fast path tests for ASCII rather than for upper case
        assertFalse(Character.isUpperCase('ǅ'));
        assertEquals("ǆ", ConfigTheme.canonicalModId("ǅ"));
        // A dotted capital I grows into an i and a combining dot under ROOT
        assertEquals("i̇d", ConfigTheme.canonicalModId("İD"));
        // A capital sigma at the end of a word takes the final form
        assertEquals("οδος", ConfigTheme.canonicalModId("ΟΔΟΣ"));
        assertEquals("ß", ConfigTheme.canonicalModId("ẞ"));
        assertEquals("été", ConfigTheme.canonicalModId("ÉTÉ"));
        // A Deseret capital is a surrogate pair, lower-cased as one code point
        assertEquals("𐐨", ConfigTheme.canonicalModId("𐐀"));
        assertEquals("möd", ConfigTheme.canonicalModId("　MÖD "));
    }

    @Test
    void agreesWithStripThenLowerCaseForEveryCodePoint() {
        for (int cp = 0; cp <= Character.MAX_CODE_POINT; cp++) {
            int point = cp;
            String ch = Character.toString(cp);
            String[] ids = cp < Character.MIN_SUPPLEMENTARY_CODE_POINT ? new String[]{ch, "a" + ch, ch + "a", "A" + ch + "Z", " " + ch + "\t"} : new String[]{ch, " A" + ch};
            for (String id : ids) {
                String expected = outcome(ConfigThemeTest::reference, id);
                assertEquals(expected, outcome(ConfigTheme::canonicalModId, id), () -> "U+" + Integer.toHexString(point));
                assertEquals(orNull(expected), ConfigTheme.canonicalModIdOrNull(id), () -> "U+" + Integer.toHexString(point));
                if (expected.startsWith("ok:")) {
                    String once = expected.substring(3);
                    // A second pass changes nothing, so normalising a key the registry already holds lands on that same key
                    assertEquals(once, ConfigTheme.canonicalModId(once), () -> "U+" + Integer.toHexString(point));
                }
            }
        }
    }

    @Test
    void randomIdsAgreeWithTheReference() {
        // Both ASCII cases, every kind of padding, and the non-ASCII mappings that change length or depend on context
        String pool = "aZm09_-. \t\n\u000B\f\r\u001F  　\u0085​ÉéßẞİıǅKΣσΟΔ𐐀";
        Random random = new Random(0x7EA1L);
        for (int i = 0; i < 20_000; i++) {
            StringBuilder id = new StringBuilder();
            for (int n = random.nextInt(12); n > 0; n--) {
                id.append(random.nextInt(8) == 0 ? (char) random.nextInt(0x10000) : pool.charAt(random.nextInt(pool.length())));
            }
            String raw = id.toString();
            String expected = outcome(ConfigThemeTest::reference, raw);
            assertEquals(expected, outcome(ConfigTheme::canonicalModId, raw), raw);
            assertEquals(orNull(expected), ConfigTheme.canonicalModIdOrNull(raw), raw);
            if (expected.startsWith("ok:")) {
                assertEquals(expected.substring(3), ConfigTheme.canonicalModId(expected.substring(3)), raw);
            }
        }
    }

    @Test
    void onlyAZeroAlphaIsReplaced() {
        for (int alpha = 0; alpha < 256; alpha++) {
            int colour = alpha << 24 | 0x123456;
            assertEquals(alpha == 0 ? 0xFF123456 : colour, new ConfigTheme("themed", colour).accentColor());
        }
    }

    @Test
    void rgbBitsAreNeverTouched() {
        Random random = new Random(0xA1FAL);
        for (int i = 0; i < 10_000; i++) {
            int colour = random.nextInt();
            int stored = new ConfigTheme("themed", colour).accentColor();
            assertEquals(colour & 0xFFFFFF, stored & 0xFFFFFF);
            assertNotEquals(0, stored >>> 24);
        }
    }

    @Test
    void builderColourFollowsTheLastCallAndTheSameAlphaRule() {
        assertEquals(0xFF000002, ConfigTheme.builder("themed").accentColor(1).accentColor(2).build().accentColor());
        assertEquals(0xFF000000, ConfigTheme.builder("themed").accentColor(0).build().accentColor());
        assertEquals(0x01000000, ConfigTheme.builder("themed").accentColor(0x01000000).build().accentColor());
        // Setting the default by hand is the same as leaving it
        assertEquals(ConfigTheme.builder("themed").build(), ConfigTheme.builder("themed").accentColor(ConfigTheme.DEFAULT_ACCENT_COLOR).build());
    }

    @Test
    void defaultAccentIsAnOpaquePublicConstant() throws NoSuchFieldException {
        int modifiers = ConfigTheme.class.getField("DEFAULT_ACCENT_COLOR").getModifiers();
        assertTrue(Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers));
        assertEquals(0xFF, ConfigTheme.DEFAULT_ACCENT_COLOR >>> 24);
    }

    @Test
    void everySpellingOfOneThemeCollapsesInHashCollections() {
        Set<ConfigTheme> themes = new HashSet<>(List.of(
                new ConfigTheme("MyMod", 0x123456),
                new ConfigTheme(" mymod ", 0xFF123456),
                ConfigTheme.builder("MYMOD\t").accentColor(0x123456).build(),
                new ConfigTheme(" myMod", 0xFF123456)));
        assertEquals(Set.of(new ConfigTheme("mymod", 0xFF123456)), themes);
        Map<ConfigTheme, String> names = new HashMap<>();
        names.put(new ConfigTheme("MyMod", 0x123456), "first");
        names.put(new ConfigTheme("mymod", 0xFF123456), "second");
        assertEquals(Map.of(new ConfigTheme("mymod", 0xFF123456), "second"), names);
    }

    @Test
    void toStringMatchesTheOldFormattedOutput() {
        List<Integer> colours = new ArrayList<>(List.of(0, -1, Integer.MIN_VALUE, Integer.MAX_VALUE, 0x01000000, 0x00FFFFFF, 0x0F0F0F0F, 0xF0F0F0F0, ConfigTheme.DEFAULT_ACCENT_COLOR));
        // Every hex digit in every position, so no nibble can lose its padding or its case
        for (int shift = 0; shift < 32; shift += 4) {
            for (int digit = 0; digit < 16; digit++) {
                colours.add(digit << shift);
            }
        }
        Random random = new Random(0xC010L);
        for (int i = 0; i < 10_000; i++) {
            colours.add(random.nextInt());
        }
        for (int colour : colours) {
            ConfigTheme theme = new ConfigTheme("themed", colour);
            assertEquals("ConfigTheme[modId=%s, accentColor=0x%08X]".formatted(theme.modId(), theme.accentColor()), theme.toString());
        }
    }

    @Test
    void toStringPrintsBoundaryColoursAsUpperCaseHex() {
        assertEquals("ConfigTheme[modId=themed, accentColor=0x80000000]", new ConfigTheme("themed", Integer.MIN_VALUE).toString());
        assertEquals("ConfigTheme[modId=themed, accentColor=0xFFFFFFFF]", new ConfigTheme("themed", -1).toString());
        assertEquals("ConfigTheme[modId=themed, accentColor=0x7FFFFFFF]", new ConfigTheme("themed", Integer.MAX_VALUE).toString());
        assertEquals("ConfigTheme[modId=themed, accentColor=0x01000000]", new ConfigTheme("themed", 0x01000000).toString());
        assertEquals("ConfigTheme[modId=themed, accentColor=0xABCDEF12]", new ConfigTheme("themed", 0xABCDEF12).toString());
        // Zero alpha is fixed before printing, so a bare RGB value prints opaque
        assertEquals("ConfigTheme[modId=themed, accentColor=0xFF000000]", new ConfigTheme("themed", 0).toString());
        assertEquals("ConfigTheme[modId=themed, accentColor=0xFFFFFFFF]", new ConfigTheme("themed", 0x00FFFFFF).toString());
        // The record's own toString would print the colour as a signed int
        assertFalse(new ConfigTheme("themed", ConfigTheme.DEFAULT_ACCENT_COLOR).toString().contains(String.valueOf(ConfigTheme.DEFAULT_ACCENT_COLOR)));
    }

    @Test
    void toStringPrintsTheIdAsStored() {
        // Format specifiers in an id are printed, not interpreted
        assertEquals("ConfigTheme[modId=%s%n%08x, accentColor=0xFF123456]", new ConfigTheme("%s%n%08X", 0xFF123456).toString());
        assertEquals("ConfigTheme[modId=a[b]=c, accentColor=0xFF123456]", new ConfigTheme("A[b]=C", 0xFF123456).toString());
        assertEquals("ConfigTheme[modId=möd, accentColor=0xFF123456]", new ConfigTheme(" MÖD ", 0xFF123456).toString());
    }

    @Test
    void toStringIgnoresTheDefaultLocale() {
        Locale previous = Locale.getDefault();
        try {
            // Thai and Arabic digits and the Turkish dotted I are the usual ways a locale leaks into formatting
            for (String tag : List.of("th-TH-u-nu-thai", "ar-SA", "tr-TR")) {
                Locale.setDefault(Locale.forLanguageTag(tag));
                assertEquals("ConfigTheme[modId=themed, accentColor=0x01000A0B]", new ConfigTheme("themed", 0x01000A0B).toString());
            }
        } finally {
            Locale.setDefault(previous);
        }
    }

    // The three-pass version canonicalModId replaced, which the single pass must match on every input
    private static String reference(String modId) {
        Objects.requireNonNull(modId, "Mod id must not be null");
        if (modId.isBlank()) {
            throw new IllegalArgumentException("Mod id must not be blank");
        }
        return modId.strip().toLowerCase(Locale.ROOT);
    }

    // Folds a result, or the exception and message it threw, into one string so two implementations compare in one assert
    private static String outcome(UnaryOperator<String> canonicalise, String modId) {
        try {
            return "ok:" + canonicalise.apply(modId);
        } catch (RuntimeException e) {
            return e.getClass().getSimpleName() + ":" + e.getMessage();
        }
    }

    // What canonicalModIdOrNull must give for an id whose canonicalModId outcome is known, where the only failure an id can hit is being blank
    private static String orNull(String outcome) {
        return outcome.startsWith("ok:") ? outcome.substring(3) : null;
    }
}
