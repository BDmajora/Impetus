package com.bdmajora.impetus.api.config.theme;

import com.bdmajora.testing.Statics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static com.bdmajora.impetus.api.config.theme.ConfigThemeRegistry.getAccentColor;
import static com.bdmajora.impetus.api.config.theme.ConfigThemeRegistry.register;
import static org.junit.jupiter.api.Assertions.*;

class ConfigThemeRegistryTest {
    // Every whitespace character.
    private static final String WHITESPACE = whitespace();
    // Starting table of the registry.
    private static final Object[] EMPTY = Statics.get(ConfigThemeRegistry.class, "EMPTY");
    // Handle to the table field.
    private static final VarHandle TABLE = tableHandle();

    // Resets the registry around each test.
    @BeforeEach
    void startEmpty() {
        setSlots(EMPTY);
    }

    @AfterEach
    void leaveEmpty() {
        setSlots(EMPTY);
    }

    @Test
    void aRegisteredThemeIsStoredUnderItsCanonicalIdAndFound() {
        ConfigTheme theme = ConfigTheme.builder("MyMod").accentColor(0xFF123456).build();
        register(theme);
        assertEquals(Map.of("mymod", OptionalInt.of(0xFF123456)), entries());
        // The key is the record's own id string
        assertSame(theme.modId(), slots()[indexOf("mymod")]);
        assertEquals(OptionalInt.of(0xFF123456), getAccentColor("mymod"));
        assertEquals(OptionalInt.of(0xFF123456), getAccentColor(theme.modId()));
        assertEquals(OptionalInt.of(0xFF123456), getAccentColor("MyMod"));
        assertWellFormed(slots());
    }

    @Test
    void anEmptyRegistryAnswersEmptyForEveryId() {
        for (String id : List.of("mymod", "MyMod", " mymod ", "a", " ", "𐐀", "\uD800", "x".repeat(10_000))) {
            assertSame(OptionalInt.empty(), getAccentColor(id), id);
        }
        // Asking never publishes a table
        assertSame(EMPTY, slots());
    }

    @Test
    void idsThatOnlyResembleARegisteredOneMiss() {
        register(new ConfigTheme("mymod", 0xFF123456));
        // Checks lookalike ids.
        for (String id : List.of("mymo", "mymodd", "ymod", "my mod", "my_mod", "my-mod", "mym0d", "mymod\u0000", "\u0000mymod",
                "mymod ", "mymod​", "﻿mymod", "mymod\u0085", "mymoḋ", "mуmod")) {
            assertEquals(OptionalInt.empty(), getAccentColor(id), id);
        }
        assertEquals(1, entries().size());
    }

    @Test
    void theGenerationAdvancesOncePerRegistrationAndNeverOnALookupOrARefusedOne() {
        int start = ConfigThemeRegistry.generation();
        getAccentColor("mymod");
        assertThrows(NullPointerException.class, () -> register(null));
        assertEquals(start, ConfigThemeRegistry.generation());
        // Checks the count.
        register(new ConfigTheme("mymod", 0xFF123456));
        register(new ConfigTheme("mymod", 0xFF123456));
        register(new ConfigTheme("MyMod", 0xFF654321));
        assertEquals(start + 3, ConfigThemeRegistry.generation());
        getAccentColor("mymod");
        getAccentColor("other");
        assertEquals(start + 3, ConfigThemeRegistry.generation());
    }

    @Test
    void registeringNullThrowsAndPublishesNothing() {
        register(new ConfigTheme("mymod", 0xFF123456));
        Object[] before = slots();
        Object[] copy = before.clone();
        assertEquals("Theme must not be null", assertThrows(NullPointerException.class, () -> register(null)).getMessage());
        assertSame(before, slots());
        assertArrayEquals(copy, before);
    }

    @Test
    void nullAndBlankIdsAnswerEmptyWithoutThrowing() {
        // Registers first.
        register(new ConfigTheme("mymod", 0xFF123456));
        assertSame(OptionalInt.empty(), getAccentColor(null));
        assertSame(OptionalInt.empty(), getAccentColor(""));
        for (char c : WHITESPACE.toCharArray()) {
            String pad = String.valueOf(c);
            assertEquals(OptionalInt.empty(), getAccentColor(pad), () -> hex(c));
            assertEquals(OptionalInt.empty(), getAccentColor(pad.repeat(3)), () -> hex(c));
            assertEquals(OptionalInt.empty(), getAccentColor(pad + " \t" + pad), () -> hex(c));
        }
        assertEquals(OptionalInt.empty(), getAccentColor(WHITESPACE));
        assertEquals(OptionalInt.empty(), getAccentColor(" ".repeat(100_000)));
        assertEquals(1, entries().size());
    }

    @Test
    void spaceLikeCharactersJavaDoesNotStripAreRealIds() {
        // Checks characters that are kept.
        char[] ids = {' ', ' ', ' ', '​', '﻿', '\u0085', '\u0000'};
        for (int i = 0; i < ids.length; i++) {
            String id = String.valueOf(ids[i]);
            register(new ConfigTheme(" " + id + "\t", colour(i)));
            assertEquals(OptionalInt.of(colour(i)), getAccentColor(id), hex(ids[i]));
            assertEquals(OptionalInt.of(colour(i)), getAccentColor("\n" + id + " "), hex(ids[i]));
        }
        assertEquals(ids.length, entries().size());
        assertWellFormed(slots());
    }

    @Test
    void aLaterRegistrationReplacesTheEarlierOneUnderAnySpelling() {
        register(new ConfigTheme("MyMod", 0xFF111111));
        register(ConfigTheme.builder(" MYMOD\t").accentColor(0xFF222222).build());
        assertEquals(Map.of("mymod", OptionalInt.of(0xFF222222)), entries());
        assertEquals(OptionalInt.of(0xFF222222), getAccentColor("MyMod"));
        // Switching back works the same way
        register(new ConfigTheme("mymod", 0xFF111111));
        assertEquals(Map.of("mymod", OptionalInt.of(0xFF111111)), entries());
        assertEquals(OptionalInt.of(0xFF111111), getAccentColor("mymod"));
    }

    @Test
    void reRegisteringAThemeOrAnEqualOneKeepsOneEntry() {
        ConfigTheme theme = new ConfigTheme("mymod", 0xFF123456);
        register(theme);
        register(theme);
        assertEquals(Map.of("mymod", OptionalInt.of(0xFF123456)), entries());
        // Checks an equal theme.
        ConfigTheme equal = new ConfigTheme("MyMod", 0x123456);
        assertEquals(theme, equal);
        assertNotSame(theme.modId(), equal.modId());
        register(equal);
        assertSame(equal.modId(), slots()[indexOf("mymod")]);
        assertEquals(OptionalInt.of(0xFF123456), getAccentColor("mymod"));
        assertEquals(1, entries().size());
    }

    @Test
    void replacingAModNeverResizesTheTable() {
        register(new ConfigTheme("mymod", colour(0)));
        int length = slots().length;
        for (int i = 1; i <= 1_000; i++) {
            register(new ConfigTheme(i % 2 == 0 ? "MyMod" : " mymod ", colour(i)));
            assertEquals(length, slots().length);
        }
        assertEquals(Map.of("mymod", OptionalInt.of(colour(1_000))), entries());
    }

    @Test
    void theTableIsTheSmallestPowerOfTwoThatStaysThreeQuartersFree() {
        assertEquals(2, EMPTY.length);
        for (int count = 1; count <= 300; count++) {
            register(new ConfigTheme("Mod" + count, colour(count)));
            Object[] slots = slots();
            int pairs = slots.length / 2;
            int mods = count;
            // Checks the table size.
            assertTrue(pairs >= 4 * mods, () -> pairs + " pairs for " + mods);
            assertTrue(pairs / 2 < 4 * mods, () -> pairs + " pairs for " + mods);
            assertEquals(count, entries().size());
            assertWellFormed(slots);
        }
    }

    @Test
    void registerPublishesANewTableAndLeavesEveryOldOneUntouched() {
        register(new ConfigTheme("first", colour(1)));
        Object[] before = slots();
        Object[] copy = before.clone();
        register(new ConfigTheme("second", colour(2)));
        assertNotSame(before, slots());
        assertArrayEquals(copy, before);
        // A reader still holding the old table sees it exactly as it was published
        assertEquals(OptionalInt.of(colour(1)), find(before, "first"));
        assertNull(find(before, "second"));
        // A replacement is copied the same way
        before = slots();
        copy = before.clone();
        register(new ConfigTheme("First", colour(3)));
        assertNotSame(before, slots());
        assertArrayEquals(copy, before);
        assertEquals(OptionalInt.of(colour(1)), find(before, "first"));
        assertEquals(OptionalInt.of(colour(3)), find(slots(), "first"));
        // Checks the starting table.
        assertArrayEquals(new Object[2], EMPTY);
    }

    @Test
    void aHitHandsBackTheStoredOptionalAndAMissTheSharedEmptyOne() {
        register(new ConfigTheme("mymod", 0xFF123456));
        // Checks nothing is allocated.
        OptionalInt stored = (OptionalInt) slots()[indexOf("mymod") + 1];
        assertSame(stored, getAccentColor("mymod"));
        assertSame(stored, getAccentColor(" MyMod "));
        assertSame(stored, getAccentColor(new String("mymod")));
        for (String miss : Arrays.asList("other", "Other", " other ", " ", "", null)) {
            assertSame(OptionalInt.empty(), getAccentColor(miss), miss);
        }
    }

    @Test
    void manyModsEachKeepTheirOwnTheme() {
        int mods = 5_000;
        for (int i = 0; i < mods; i++) {
            register(new ConfigTheme("Mod" + i, colour(i)));
        }
        assertEquals(mods, entries().size());
        for (int i = 0; i < mods; i++) {
            assertEquals(OptionalInt.of(colour(i)), getAccentColor("mod" + i));
            assertEquals(OptionalInt.of(colour(i)), getAccentColor("MOD" + i));
        }
        // Replacing every other mod leaves its neighbours alone
        for (int i = 0; i < mods; i += 2) {
            register(new ConfigTheme("mod" + i, 0xFE000000 | i));
        }
        assertEquals(mods, entries().size());
        for (int i = 0; i < mods; i++) {
            assertEquals(OptionalInt.of(i % 2 == 0 ? 0xFE000000 | i : colour(i)), getAccentColor(" Mod" + i + " "));
        }
        assertEquals(OptionalInt.empty(), getAccentColor("mod" + mods));
        assertEquals(OptionalInt.empty(), getAccentColor("mod-1"));
        assertWellFormed(slots());
    }

    @Test
    void lookupsIgnoreCaseAndPadding() {
        register(new ConfigTheme("MyMod", 0xFF123456));
        for (String id : List.of("mymod", "MYMOD", "MyMod", "mYmOd", " mymod", "mymod ", "\tMyMod\n", "\r\n MYMOD \f\u000B",
                " MyMod　", "mymod", "\u001Cmymod\u001F")) {
            assertEquals(OptionalInt.of(0xFF123456), getAccentColor(id), id);
        }
        // Checks whitespace padding.
        for (char c : WHITESPACE.toCharArray()) {
            String pad = String.valueOf(c);
            assertEquals(OptionalInt.of(0xFF123456), getAccentColor(pad + "MyMod"), () -> hex(c));
            assertEquals(OptionalInt.of(0xFF123456), getAccentColor("mymod" + pad), () -> hex(c));
            assertEquals(OptionalInt.of(0xFF123456), getAccentColor(pad + pad + "MYMOD" + pad), () -> hex(c));
        }
        assertEquals(OptionalInt.of(0xFF123456), getAccentColor(WHITESPACE + "MyMod" + WHITESPACE));
    }

    @Test
    void innerCharactersAreKeptSoTheyNameOtherMods() {
        register(new ConfigTheme("my mod", 0xFF000001));
        register(new ConfigTheme("my\tmod", 0xFF000002));
        register(new ConfigTheme("mymod", 0xFF000003));
        register(new ConfigTheme("my_mod", 0xFF000004));
        assertEquals(4, entries().size());
        assertEquals(OptionalInt.of(0xFF000001), getAccentColor(" My Mod "));
        assertEquals(OptionalInt.of(0xFF000002), getAccentColor("MY\tMOD"));
        assertEquals(OptionalInt.of(0xFF000003), getAccentColor("MyMod"));
        assertEquals(OptionalInt.of(0xFF000004), getAccentColor("My_Mod"));
        assertEquals(OptionalInt.empty(), getAccentColor("my  mod"));
        assertEquals(OptionalInt.empty(), getAccentColor("my\nmod"));
    }

    @Test
    void canonicalQueriesHitWhetherOrNotTheyAreTheStoredString() {
        ConfigTheme theme = new ConfigTheme("mymod", 0xFF123456);
        register(theme);
        // Checks the key spellings.
        assertEquals(OptionalInt.of(0xFF123456), getAccentColor(theme.modId()));
        assertEquals(OptionalInt.of(0xFF123456), getAccentColor("mymod"));
        assertEquals(OptionalInt.of(0xFF123456), getAccentColor(new String("mymod")));
        assertEquals(OptionalInt.of(0xFF123456), getAccentColor(new StringBuilder("my").append("mod").toString()));
        assertEquals(OptionalInt.of(0xFF123456), getAccentColor(String.valueOf(new char[]{'m', 'y', 'm', 'o', 'd'})));
    }

    @Test
    void theExactSpellingIsTriedBeforeTheIdIsNormalised() {
        // Plants a non canonical key.
        Object[] planted = new Object[8];
        int at = home("Planted", planted.length);
        planted[at] = "Planted";
        planted[at + 1] = OptionalInt.of(0xFF123456);
        setSlots(planted);
        assertEquals(OptionalInt.of(0xFF123456), getAccentColor("Planted"));
        // Checks other spellings.
        assertEquals(OptionalInt.empty(), getAccentColor("planted"));
        assertEquals(OptionalInt.empty(), getAccentColor("PLANTED"));
        assertEquals(OptionalInt.empty(), getAccentColor(" Planted "));
    }

    @Test
    void probesWrapPastTheEndOfTheTable() {
        // Checks a wrapped run.
        List<String> last = new ArrayList<>();
        for (int i = 0; last.size() < 3; i++) {
            if (home("wrap" + i, 16) == 14) {
                last.add("wrap" + i);
            }
        }
        register(new ConfigTheme(last.get(0), colour(1)));
        register(new ConfigTheme(last.get(1), colour(2)));
        // The second found the last pair taken and wrapped round to the first
        assertEquals(16, slots().length);
        assertEquals(last.get(0), slots()[14]);
        assertEquals(last.get(1), slots()[0]);
        assertEquals(OptionalInt.of(colour(1)), getAccentColor(last.get(0)));
        assertEquals(OptionalInt.of(colour(2)), getAccentColor(last.get(1)));
        // Checks the copy order.
        register(new ConfigTheme(last.get(0), colour(3)));
        assertEquals(16, slots().length);
        assertEquals(last.get(1), slots()[14]);
        assertEquals(last.get(0), slots()[0]);
        assertEquals(OptionalInt.of(colour(3)), getAccentColor(last.get(0)));
        assertEquals(OptionalInt.of(colour(2)), getAccentColor(last.get(1)));
        // Checks a miss.
        assertEquals(OptionalInt.empty(), getAccentColor(last.get(2)));
        assertWellFormed(slots());
    }

    @Test
    void nonAsciiIdsMatchThroughTheirCaseMappings() {
        // Checks sigma.
        register(new ConfigTheme("ΟΔΟΣ", 0xFF000001));
        assertEquals(OptionalInt.of(0xFF000001), getAccentColor("οδος"));
        assertEquals(OptionalInt.of(0xFF000001), getAccentColor(" ΟΔΟΣ "));
        assertEquals(OptionalInt.of(0xFF000001), getAccentColor("Οδος"));
        assertEquals(OptionalInt.empty(), getAccentColor("οδοσ"));
        // The Kelvin sign lower-cases to a plain k
        register(new ConfigTheme("key", 0xFF000002));
        assertEquals(OptionalInt.of(0xFF000002), getAccentColor("KEY"));
        assertEquals(OptionalInt.of(0xFF000002), getAccentColor("Key"));
        // Checks dotted I.
        register(new ConfigTheme("İD", 0xFF000003));
        assertEquals(OptionalInt.of(0xFF000003), getAccentColor("i̇d"));
        assertEquals(OptionalInt.of(0xFF000003), getAccentColor("İd"));
        assertEquals(OptionalInt.empty(), getAccentColor("ID"));
        assertEquals(OptionalInt.empty(), getAccentColor("id"));
        // Checks DZ digraphs.
        register(new ConfigTheme("ǅ", 0xFF000004));
        assertEquals(OptionalInt.of(0xFF000004), getAccentColor("Ǆ"));
        assertEquals(OptionalInt.of(0xFF000004), getAccentColor("ǆ"));
        assertEquals(OptionalInt.empty(), getAccentColor("dž"));
        // Checks a Deseret capital.
        register(new ConfigTheme("𐐀", 0xFF000005));
        assertEquals(OptionalInt.of(0xFF000005), getAccentColor("𐐨"));
        assertEquals(OptionalInt.of(0xFF000005), getAccentColor(" 𐐀 "));
        // Checks the capital sharp s.
        register(new ConfigTheme("ẞ", 0xFF000006));
        assertEquals(OptionalInt.of(0xFF000006), getAccentColor("ß"));
        assertEquals(OptionalInt.empty(), getAccentColor("ss"));
        assertEquals(OptionalInt.empty(), getAccentColor("SS"));
        // Checks no normalisation.
        register(new ConfigTheme("möd", 0xFF000007));
        assertEquals(OptionalInt.of(0xFF000007), getAccentColor("möd"));
        assertEquals(OptionalInt.of(0xFF000007), getAccentColor("MÖD"));
        assertEquals(OptionalInt.empty(), getAccentColor("möd"));
        // Unpaired surrogates are kept as they are and do not match each other
        register(new ConfigTheme("\uD800", 0xFF000008));
        register(new ConfigTheme(" \uDC00 ", 0xFF000009));
        assertEquals(OptionalInt.of(0xFF000008), getAccentColor("\uD800"));
        assertEquals(OptionalInt.of(0xFF000009), getAccentColor("\uDC00"));
        assertEquals(OptionalInt.of(0xFF000008), getAccentColor("\t\uD800\n"));
        assertEquals(9, entries().size());
        assertWellFormed(slots());
    }

    @Test
    void lookupsIgnoreTheDefaultLocale() {
        Locale previous = Locale.getDefault();
        try {
            register(new ConfigTheme("IMPETUS", 0xFF000001));
            // Checks the locale.
            for (String tag : List.of("tr-TR", "az-AZ", "lt-LT")) {
                Locale.setDefault(Locale.forLanguageTag(tag));
                assertEquals(OptionalInt.of(0xFF000001), getAccentColor("IMPETUS"), tag);
                assertEquals(OptionalInt.of(0xFF000001), getAccentColor(" Impetus "), tag);
                assertEquals(OptionalInt.empty(), getAccentColor("ımpetus"), tag);
                // Registering under these locales lands on the same keys as under any other
                register(new ConfigTheme("INDIGO", 0xFF000002));
                assertEquals(Set.of("impetus", "indigo"), entries().keySet(), tag);
            }
            Locale.setDefault(Locale.ROOT);
            assertEquals(OptionalInt.of(0xFF000002), getAccentColor("Indigo"));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void idsThatShareOneHashAreStillToldApart() {
        // Checks colliding hashes.
        assertEquals("az".hashCode(), "b[".hashCode());
        List<String> ids = new ArrayList<>();
        for (int bits = 0; bits < 1 << 7; bits++) {
            StringBuilder id = new StringBuilder();
            for (int block = 0; block < 7; block++) {
                id.append((bits >> block & 1) == 0 ? "az" : "b[");
            }
            ids.add(id.toString());
        }
        for (String id : ids) {
            assertEquals(ids.get(0).hashCode(), id.hashCode());
        }
        // Checks the worst case.
        for (int i = 0; i < ids.size(); i += 2) {
            register(new ConfigTheme(ids.get(i).toUpperCase(Locale.ROOT), colour(i)));
        }
        assertEquals(64, entries().size());
        assertWellFormed(slots());
        // The other half share that home with every stored key and must walk the whole run to miss
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            OptionalInt expected = i % 2 == 0 ? OptionalInt.of(colour(i)) : OptionalInt.empty();
            assertEquals(expected, getAccentColor(id), id);
            assertEquals(expected, getAccentColor(id.toUpperCase(Locale.ROOT)), id);
            assertEquals(expected, getAccentColor(" " + id + "\t"), id);
        }
    }

    @Test
    void aQueryThatHashesLikeAnotherModStillFindsOnlyItsOwn() {
        // Checks a lookalike hash.
        assertEquals("aZ".hashCode(), "`y".hashCode());
        register(new ConfigTheme("`y", 0xFF000001));
        assertEquals(OptionalInt.empty(), getAccentColor("aZ"));
        register(new ConfigTheme("az", 0xFF000002));
        assertEquals(OptionalInt.of(0xFF000002), getAccentColor("aZ"));
        assertEquals(OptionalInt.of(0xFF000001), getAccentColor("`y"));
        assertEquals(OptionalInt.of(0xFF000001), getAccentColor("`Y"));
    }

    @Test
    void theStoredColourComesBackAfterTheAlphaFix() {
        int[] given = {0, 0x123456, 0x00FFFFFF, 0x01000000, 0x80123456, -1, Integer.MIN_VALUE, Integer.MAX_VALUE, ConfigTheme.DEFAULT_ACCENT_COLOR};
        int[] stored = {0xFF000000, 0xFF123456, 0xFFFFFFFF, 0x01000000, 0x80123456, -1, Integer.MIN_VALUE, Integer.MAX_VALUE, ConfigTheme.DEFAULT_ACCENT_COLOR};
        for (int i = 0; i < given.length; i++) {
            register(new ConfigTheme("mod" + i, given[i]));
            assertEquals(OptionalInt.of(stored[i]), getAccentColor("mod" + i), "mod" + i);
        }
        // A builder left alone registers the default teal
        register(ConfigTheme.builder("Plain").build());
        assertEquals(OptionalInt.of(ConfigTheme.DEFAULT_ACCENT_COLOR), getAccentColor("plain"));
    }

    @Test
    void anAnswerIsASnapshotThatLaterRegistrationsDoNotChange() {
        OptionalInt before = getAccentColor("mymod");
        register(new ConfigTheme("mymod", 0xFF000001));
        OptionalInt first = getAccentColor("mymod");
        register(new ConfigTheme("mymod", 0xFF000002));
        assertEquals(OptionalInt.empty(), before);
        assertEquals(OptionalInt.of(0xFF000001), first);
        assertEquals(OptionalInt.of(0xFF000002), getAccentColor("mymod"));
    }

    @Test
    void veryLongIdsAreNormalisedAndFound() {
        String body = "Mod".repeat(50_000);
        String key = body.toLowerCase(Locale.ROOT);
        register(new ConfigTheme(" ".repeat(1_000) + body + "\t".repeat(1_000), 0xFF123456));
        assertEquals(Set.of(key), entries().keySet());
        assertEquals(OptionalInt.of(0xFF123456), getAccentColor(body));
        assertEquals(OptionalInt.of(0xFF123456), getAccentColor(key));
        assertEquals(OptionalInt.of(0xFF123456), getAccentColor(body.toUpperCase(Locale.ROOT)));
        // One character off at either end is another mod
        assertEquals(OptionalInt.empty(), getAccentColor(key + "x"));
        assertEquals(OptionalInt.empty(), getAccentColor(key.substring(1)));
    }

    @Test
    void everyCodePointRoundTripsThroughTheRegistry() {
        for (int cp = 0; cp <= Character.MAX_CODE_POINT; cp++) {
            int point = cp;
            String ch = Character.toString(cp);
            for (String raw : new String[]{ch, "M" + ch}) {
                String key = reference(raw);
                if (key == null) {
                    // A blank id can neither be registered nor found
                    assertEquals(OptionalInt.empty(), getAccentColor(raw), () -> "U+" + Integer.toHexString(point));
                    continue;
                }
                ConfigTheme theme = new ConfigTheme(raw, colour(cp));
                register(theme);
                // Checks the key.
                int at = indexOf(key);
                assertTrue(at >= 0, () -> "U+" + Integer.toHexString(point));
                assertSame(theme.modId(), slots()[at], () -> "U+" + Integer.toHexString(point));
                assertEquals(OptionalInt.of(colour(cp)), getAccentColor(raw), () -> "U+" + Integer.toHexString(point));
                assertEquals(OptionalInt.of(colour(cp)), getAccentColor(key), () -> "U+" + Integer.toHexString(point));
                assertEquals(OptionalInt.of(colour(cp)), getAccentColor(" " + raw + "　"), () -> "U+" + Integer.toHexString(point));
                setSlots(EMPTY);
            }
        }
    }

    @Test
    void randomRegistrationsAndLookupsBehaveLikeAMapKeyedByStripThenLowerCase() {
        // Checks the spellings.
        String pool = "aAzZ09_- \t\n\u000B\f\r\u001F 　 \u0085​ÉéßẞİıǅKΣσΟ";
        Random random = new Random(0x7EA1L);
        Map<String, OptionalInt> model = new HashMap<>();
        for (int i = 0; i < 20_000; i++) {
            StringBuilder built = new StringBuilder();
            for (int n = random.nextInt(4); n > 0; n--) {
                built.append(random.nextInt(10) == 0 ? (char) random.nextInt(0x10000) : pool.charAt(random.nextInt(pool.length())));
            }
            String raw = built.toString();
            String key = reference(raw);
            // Registers on one call in four.
            if (random.nextInt(4) == 0) {
                if (key == null) {
                    assertThrows(IllegalArgumentException.class, () -> new ConfigTheme(raw, 0), raw);
                } else {
                    int colour = 0xFF000000 | random.nextInt(0x1000000);
                    register(new ConfigTheme(raw, colour));
                    model.put(key, OptionalInt.of(colour));
                }
            }
            assertEquals(key == null ? OptionalInt.empty() : model.getOrDefault(key, OptionalInt.empty()), getAccentColor(raw), raw);
        }
        // Checks the table.
        assertEquals(model, entries());
        assertWellFormed(slots());
    }

    @Test
    void parallelRegistrationsAreNeitherLostNorMixedUp() throws Exception {
        int writers = 4;
        int perWriter = 1_000;
        ExecutorService pool = Executors.newFixedThreadPool(writers + 2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicBoolean writing = new AtomicBoolean(true);
        try {
            List<Future<?>> writes = new ArrayList<>();
            for (int w = 0; w < writers; w++) {
                int writer = w;
                writes.add(pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < perWriter; i++) {
                        String id = "mod_" + writer + "_" + i;
                        register(new ConfigTheme(id.toUpperCase(Locale.ROOT), colourOf(id)));
                        // A thread always sees its own registration straight away
                        assertEquals(OptionalInt.of(colourOf(id)), getAccentColor(id), id);
                    }
                    return null;
                }));
            }
            // Checks racing readers.
            List<Future<?>> reads = new ArrayList<>();
            for (int r = 0; r < 2; r++) {
                long seed = r;
                reads.add(pool.submit(() -> {
                    start.await();
                    Random random = new Random(seed);
                    while (writing.get()) {
                        String id = "mod_" + random.nextInt(writers) + "_" + random.nextInt(perWriter);
                        OptionalInt seen = getAccentColor(" " + id.toUpperCase(Locale.ROOT) + " ");
                        if (seen.isPresent()) {
                            assertEquals(colourOf(id), seen.getAsInt(), id);
                        }
                    }
                    return null;
                }));
            }
            start.countDown();
            try {
                for (Future<?> write : writes) {
                    join(write);
                }
            } finally {
                writing.set(false);
            }
            for (Future<?> read : reads) {
                join(read);
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(writers * perWriter, entries().size());
        for (int w = 0; w < writers; w++) {
            for (int i = 0; i < perWriter; i++) {
                String id = "mod_" + w + "_" + i;
                assertEquals(OptionalInt.of(colourOf(id)), getAccentColor(id), id);
            }
        }
        assertWellFormed(slots());
    }

    @Test
    void racingReplacementsOfOneModNeverLetItVanish() throws Exception {
        int writers = 4;
        int rounds = 5_000;
        // Checks racing writers.
        String[] spellings = {"Shared", " SHARED", "shared\t", "sHaReD"};
        register(new ConfigTheme("shared", colour(0)));
        ExecutorService pool = Executors.newFixedThreadPool(writers + 1);
        CountDownLatch start = new CountDownLatch(1);
        AtomicBoolean writing = new AtomicBoolean(true);
        try {
            List<Future<?>> writes = new ArrayList<>();
            for (int w = 0; w < writers; w++) {
                int writer = w;
                writes.add(pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < rounds; i++) {
                        register(new ConfigTheme(spellings[writer], colour(writer << 16 | i)));
                    }
                    return null;
                }));
            }
            // Checks every published table.
            Future<?> read = pool.submit(() -> {
                start.await();
                while (writing.get()) {
                    OptionalInt seen = getAccentColor("SHARED");
                    assertTrue(seen.isPresent());
                    int seenColour = seen.getAsInt();
                    assertEquals(0xFF, seenColour >>> 24);
                    assertTrue((seenColour >> 16 & 0xFF) < writers, () -> Integer.toHexString(seenColour));
                    assertTrue((seenColour & 0xFFFF) < rounds, () -> Integer.toHexString(seenColour));
                }
                return null;
            });
            start.countDown();
            try {
                for (Future<?> write : writes) {
                    join(write);
                }
            } finally {
                writing.set(false);
            }
            join(read);
        } finally {
            pool.shutdownNow();
        }
        // Checks the final pair.
        assertEquals(Set.of("shared"), entries().keySet());
        assertEquals(8, slots().length);
        int last = getAccentColor("shared").orElseThrow();
        assertEquals(rounds - 1, last & 0xFFFF);
        assertTrue((last >> 16 & 0xFF) < writers);
    }

    @Test
    void publicShapeStaysTheApiOtherModsCompileAgainst() throws ReflectiveOperationException {
        Class<ConfigThemeRegistry> type = ConfigThemeRegistry.class;
        assertTrue(Modifier.isPublic(type.getModifiers()) && Modifier.isFinal(type.getModifiers()));
        // Checks the return types.
        Method registerMethod = type.getMethod("register", ConfigTheme.class);
        assertEquals(void.class, registerMethod.getReturnType());
        assertTrue(Modifier.isStatic(registerMethod.getModifiers()));
        Method lookupMethod = type.getMethod("getAccentColor", String.class);
        assertEquals(OptionalInt.class, lookupMethod.getReturnType());
        assertTrue(Modifier.isStatic(lookupMethod.getModifiers()));
        // Checks the added method.
        Method generationMethod = type.getMethod("generation");
        assertEquals(int.class, generationMethod.getReturnType());
        assertTrue(Modifier.isStatic(generationMethod.getModifiers()));
        assertEquals(Set.of("register", "getAccentColor", "generation"), Arrays.stream(type.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers())).map(Method::getName).collect(Collectors.toSet()));
        assertEquals(0, type.getFields().length);
        // Checks the table field.
        int table = type.getDeclaredField("table").getModifiers();
        assertTrue(Modifier.isPrivate(table) && Modifier.isStatic(table) && Modifier.isVolatile(table));
        assertFalse(Modifier.isFinal(table));
        assertEquals(Object[].class, type.getDeclaredField("table").getType());
        // Checks the count field.
        int generation = type.getDeclaredField("generation").getModifiers();
        assertTrue(Modifier.isPrivate(generation) && Modifier.isStatic(generation) && Modifier.isVolatile(generation));
        assertEquals(int.class, type.getDeclaredField("generation").getType());
        for (String constant : List.of("EMPTY", "WRITE_LOCK")) {
            int modifiers = type.getDeclaredField(constant).getModifiers();
            assertTrue(Modifier.isPrivate(modifiers) && Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers), constant);
        }
        // Checks the private constructor.
        Constructor<?>[] constructors = type.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        assertEquals(0, constructors[0].getParameterCount());
        assertTrue(Modifier.isPrivate(constructors[0].getModifiers()));
        constructors[0].setAccessible(true);
        assertInstanceOf(type, constructors[0].newInstance());
    }

    // Checks a published table.
    private static void assertWellFormed(Object[] slots) {
        assertEquals(0, slots.length % 2);
        int pairs = slots.length / 2;
        assertEquals(1, Integer.bitCount(pairs), () -> pairs + " pairs");
        int full = 0;
        for (int i = 0; i < slots.length; i += 2) {
            if (slots[i] == null) {
                assertNull(slots[i + 1]);
                continue;
            }
            full++;
            String key = assertInstanceOf(String.class, slots[i]);
            assertTrue(assertInstanceOf(OptionalInt.class, slots[i + 1]).isPresent(), key);
            assertEquals(ConfigTheme.canonicalModId(key), key);
            for (int j = home(key, slots.length); j != i; j = (j + 2) & (slots.length - 1)) {
                assertNotNull(slots[j], key);
            }
        }
        int stored = full;
        assertTrue(4 * stored <= pairs, () -> stored + " full of " + pairs);
    }

    // Decodes the published table.
    private static Map<String, OptionalInt> entries() {
        Object[] slots = slots();
        Map<String, OptionalInt> found = new HashMap<>();
        for (int i = 0; i < slots.length; i += 2) {
            if (slots[i] != null) {
                String key = (String) slots[i];
                assertNull(found.put(key, (OptionalInt) slots[i + 1]), key);
            }
        }
        return found;
    }

    // Returns the pair index of an id.
    private static int indexOf(String id) {
        Object[] slots = slots();
        for (int i = 0; i < slots.length; i += 2) {
            if (id.equals(slots[i])) {
                return i;
            }
        }
        return -1;
    }

    private static Object[] slots() {
        return (Object[]) TABLE.getVolatile();
    }

    private static void setSlots(Object[] slots) {
        TABLE.setVolatile(slots);
    }

    private static int home(String id, int length) {
        return Statics.call(ConfigThemeRegistry.class, "home", id, length);
    }

    private static OptionalInt find(Object[] slots, String id) {
        return Statics.call(ConfigThemeRegistry.class, "find", slots, id);
    }

    private static VarHandle tableHandle() {
        try {
            return MethodHandles.privateLookupIn(ConfigThemeRegistry.class, MethodHandles.lookup())
                    .findStaticVarHandle(ConfigThemeRegistry.class, "table", Object[].class);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    // Old canonical form of an id.
    private static String reference(String id) {
        return id.isBlank() ? null : id.strip().toLowerCase(Locale.ROOT);
    }

    private static int colour(int index) {
        return 0xFF000000 | index;
    }

    // Returns the colour of an id.
    private static int colourOf(String id) {
        return 0xFF000000 | (id.hashCode() & 0xFFFFFF);
    }

    private static String hex(char c) {
        return "U+" + Integer.toHexString(c);
    }

    private static String whitespace() {
        StringBuilder found = new StringBuilder();
        for (char c = 0; c < Character.MAX_VALUE; c++) {
            if (Character.isWhitespace(c)) {
                found.append(c);
            }
        }
        return found.toString();
    }

    // Waits for a task and rethrows its failure.
    private static void join(Future<?> task) throws Exception {
        try {
            task.get(60, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof Error error) {
                throw error;
            }
            throw e;
        }
    }
}
