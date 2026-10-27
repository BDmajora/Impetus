package com.bdmajora.impetus.engine.impl.compat.environment;

import com.bdmajora.testing.FreshClasses;
import com.bdmajora.testing.Statics;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Properties;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Tests the os.name classification of OsKind
class OsKindTest {
    // System property that names the operating system
    private static final String OS_NAME = "os.name";
    // Names the classification looks for
    private static final String[] PATTERNS = {"win", "mac", "darwin", "linux", "bsd", "unix"};
    // Fragments the random names are joined from
    private static final String[] FRAGMENTS = {
            "dar", "win", "darwin", "mac", "linux", "bsd", "unix", "d", "w", "in", "ar", "x", "WIN", "Dar", "DARWIN",
            "ma", "c", "lin", "ux", "un", "ix", "b", "sd", " ", "\u0130", "\u0131", "\u017F", "\u212A", "\u0307"};
    // Largest number of fragments in a random name
    private static final int MAX_FRAGMENTS = 7;
    // Number of random names compared with the reference
    private static final int RANDOM_NAMES = 300_000;
    // Length of the long names in characters
    private static final int LONG_LENGTH = 1 << 24;

    // Returns the kind the detection of OsKind assigns to the name
    private static OsKind detect(String name) {
        return Statics.call(OsKind.class, "detect", name);
    }

    // Returns the result of the pattern match helper of OsKind
    private static boolean matches(String name, int index, String pattern) {
        return Statics.<Boolean>call(OsKind.class, "matches", name, index, pattern);
    }

    // Returns the detect method of a fresh uninstrumented copy of OsKind
    private static Method freshDetect() throws ReflectiveOperationException {
        Method detect = new FreshClasses(OsKind.class).load(OsKind.class).getDeclaredMethod("detect", String.class);
        detect.setAccessible(true);
        return detect;
    }

    // Returns the name of the kind the method assigns to the name
    private static String kindName(Method detect, String name) throws ReflectiveOperationException {
        return ((Enum<?>) detect.invoke(null, name)).name();
    }

    // Returns the name of the kind a fresh copy of OsKind detects while its class initialises
    private static String freshCurrent() throws ReflectiveOperationException {
        Class<?> type = new FreshClasses(OsKind.class).initialise(OsKind.class);
        return ((Enum<?>) type.getMethod("current").invoke(null)).name();
    }

    // Returns the name of the kind a fresh copy of OsKind detects with os.name set to the value or removed when the value is null
    private static String freshCurrent(String osName) throws ReflectiveOperationException {
        String original = System.getProperty(OS_NAME);
        try {
            setOsName(osName);
            return freshCurrent();
        } finally {
            setOsName(original);
        }
    }

    // Sets os.name to the value or removes it when the value is null
    private static void setOsName(String value) {
        if (value == null) {
            System.clearProperty(OS_NAME);
        } else {
            System.setProperty(OS_NAME, value);
        }
    }

    // Returns the kind name the lower case reference rules assign to the name
    private static String reference(String name) {
        String lower = name.toLowerCase(Locale.ROOT);

        if (hasStandaloneWin(lower)) {
            return "WINDOWS";
        } else if (lower.contains("mac") || lower.contains("darwin")) {
            return "MACOS";
        } else if (lower.contains("linux") || lower.contains("bsd") || lower.contains("unix")) {
            return "LINUX";
        }

        return "UNKNOWN";
    }

    // Returns true when the lower case name holds a win that does not end a darwin
    private static boolean hasStandaloneWin(String lower) {
        for (int i = lower.indexOf("win"); i >= 0; i = lower.indexOf("win", i + 1)) {
            if (!lower.startsWith("dar", i - 3)) {
                return true;
            }
        }
        return false;
    }

    // Asserts that every name is detected as the kind
    private static void assertDetects(OsKind kind, String... names) {
        for (String name : names) {
            assertSame(kind, detect(name), name);
        }
    }

    // Verifies current returns the same cached kind on repeated calls
    @Test
    void currentReturnsCachedKind() {
        OsKind cached = Statics.get(OsKind.class, "CURRENT");
        for (int round = 0; round < 3; round++) {
            assertSame(cached, OsKind.current());
        }
    }

    // Verifies current returns a kind swapped into the cache
    @Test
    void currentReadsSwappedCache() {
        OsKind original = Statics.get(OsKind.class, "CURRENT");
        try {
            for (OsKind kind : OsKind.values()) {
                Statics.set(OsKind.class, "CURRENT", kind);
                assertSame(kind, OsKind.current());
            }
        } finally {
            Statics.set(OsKind.class, "CURRENT", original);
        }
    }

    // Verifies the class initialiser classifies the os.name of the running JVM
    @Test
    void classInitialiserClassifiesRunningJvm() throws ReflectiveOperationException {
        assertEquals(reference(System.getProperty(OS_NAME, "")), freshCurrent());
    }

    // Verifies the class initialiser reads os.name when the class initialises
    @Test
    void classInitialiserReadsOsName() throws ReflectiveOperationException {
        assertEquals("WINDOWS", freshCurrent("Windows 11"));
        assertEquals("MACOS", freshCurrent("Mac OS X"));
        assertEquals("MACOS", freshCurrent("Darwin"));
        assertEquals("LINUX", freshCurrent("Linux"));
        assertEquals("LINUX", freshCurrent("FreeBSD"));
        assertEquals("UNKNOWN", freshCurrent("SunOS"));
        assertEquals("UNKNOWN", freshCurrent(""));
    }

    // Verifies the class initialiser treats a missing os.name as unknown
    @Test
    void classInitialiserTreatsMissingOsNameAsUnknown() throws ReflectiveOperationException {
        assertEquals("UNKNOWN", freshCurrent(null));
    }

    // Verifies the class initialiser treats a null os.name from replaced system properties as unknown
    @Test
    void classInitialiserTreatsNullOsNameAsUnknown() throws ReflectiveOperationException {
        Properties original = System.getProperties();
        // Copy of the system properties that answers null for os.name
        Properties broken = new Properties() {
            // Returns null for os.name and the stored value for every other key
            @Override
            public String getProperty(String key, String defaultValue) {
                return OS_NAME.equals(key) ? null : super.getProperty(key, defaultValue);
            }
        };
        broken.putAll(original);
        System.setProperties(broken);
        try {
            assertEquals("UNKNOWN", freshCurrent());
        } finally {
            System.setProperties(original);
        }
    }

    // Verifies detect treats a null name as unknown
    @Test
    void detectTreatsNullAsUnknown() {
        assertSame(OsKind.UNKNOWN, detect(null));
    }

    // Verifies detect returns unknown for empty names and names shorter than every pattern
    @Test
    void detectReturnsUnknownForEmptyAndShortNames() {
        assertDetects(OsKind.UNKNOWN, "", " ", "\0", "w", "W", "m", "d", "l", "b", "u", "wi", "WI", "ma", "bs", "un", "li", "da");
    }

    // Verifies detect classifies the os.name values JVMs report
    @Test
    void detectClassifiesReportedNames() {
        assertDetects(OsKind.WINDOWS, "Windows 10", "Windows 11", "Windows Server 2022", "Windows NT (unknown)", "Windows 95", "Windows CE");
        assertDetects(OsKind.MACOS, "Mac OS X", "Mac OS", "Darwin");
        assertDetects(OsKind.LINUX, "Linux", "FreeBSD", "OpenBSD", "NetBSD", "Digital Unix");
        assertDetects(OsKind.UNKNOWN, "SunOS", "Solaris", "AIX", "HP-UX", "OS/2", "OS/400", "z/OS", "Irix", "OpenVMS", "Haiku");
    }

    // Verifies detect ignores ASCII case in every pattern
    @Test
    void detectIgnoresAsciiCase() {
        assertDetects(OsKind.WINDOWS, "WIN", "wIn", "Win", "wiN", "WINDOWS");
        assertDetects(OsKind.MACOS, "MAC", "mAc", "maC", "DARWIN", "DarWin", "dARWIN");
        assertDetects(OsKind.LINUX, "LINUX", "LiNuX", "BSD", "bSd", "UNIX", "uNiX");
    }

    // Verifies detect finds patterns at the start and the end of a name and rejects patterns cut off by the end
    @Test
    void detectHandlesNameBoundaries() {
        assertDetects(OsKind.WINDOWS, "win", "winx", "xwin", "xxwinxx");
        assertDetects(OsKind.MACOS, "mac", "xmac", "macx", "darwin", "xdarwin");
        assertDetects(OsKind.LINUX, "linux", "xlinux", "linuxx", "bsd", "xbsd", "unix", "xunix");
        assertDetects(OsKind.UNKNOWN, "xxwi", "xma", "darwi", "xdarwi", "linu", "xlinu", "xbs", "uni", "xuni");
        assertDetects(OsKind.UNKNOWN, "w in", "m ac", "dar wi", "lin ux", "b sd", "un ix");
    }

    // Verifies detect ranks Windows above macOS and macOS above the Linux family in either order
    @Test
    void detectRanksKinds() {
        assertDetects(OsKind.WINDOWS, "Linux on Windows", "Windows Subsystem for Linux", "mac win", "win mac", "darwin win", "unix bsd win");
        assertDetects(OsKind.MACOS, "Linux Mac", "Mac Linux", "bsd darwin", "darwin bsd", "unix mac");
        assertDetects(OsKind.LINUX, "BSD Unix Linux", "linux bsd", "unix");
    }

    // Verifies detect classifies darwin as macOS and keeps every other win as Windows
    @Test
    void detectSeparatesDarwinFromWindows() {
        assertDetects(OsKind.MACOS, "Darwin", "DARWIN", "darwin 24.0.0", "darwindows", "wdarwinin", "dardarwin", "Darwin Linux", "\u0130darwin");
        assertDetects(OsKind.MACOS, "darwindarwin", "macdarwin", "darwin mac darwin", "Mac OS X darwin");
        assertDetects(OsKind.WINDOWS, "darwinwin", "windarwin", "dar win", "xarwin", "arwin", "darwin on windows", "DARWIN WIN");
        assertDetects(OsKind.UNKNOWN, "darwi", "arwi", "daw");
    }

    // Verifies detect finds patterns that overlap a partial copy of themselves
    @Test
    void detectFindsOverlappingRepeats() {
        assertDetects(OsKind.WINDOWS, "wwin", "wiwin", "winwin");
        assertDetects(OsKind.MACOS, "mmac", "mamac", "macmac", "dadarwin");
        assertDetects(OsKind.LINUX, "lilinux", "linlinux", "bbsd", "bsbsd", "ununix", "uunix", "unixunix");
        assertDetects(OsKind.UNKNOWN, "wiwi", "mama", "linulinu", "bsbs", "uniuni");
    }

    // Verifies detect rejects characters that only look like or fold to pattern letters outside ASCII
    @Test
    void detectRejectsUnicodeLookAlikes() {
        assertDetects(OsKind.UNKNOWN, "W\u0130N", "w\u0130n", "w\u0131n", "W\u0131N", "b\u017Fd", "\uFF57\uFF49\uFF4E", "Linu\u0445", "\u043C\u0430\u0441");
        assertDetects(OsKind.WINDOWS, "\u0130win", "\u212Awin", "win\u0307");
        assertDetects(OsKind.LINUX, "LINUX\u212A", "unix\u0307");
    }

    // Verifies detect reads control characters and whitespace as plain separators
    @Test
    void detectReadsControlCharacters() {
        assertDetects(OsKind.WINDOWS, "\0win", "win\0", " win ", "\twin\n");
        assertDetects(OsKind.UNKNOWN, "w\0in", "\0\0\0", "   ", "\u007F\u0080\u00FF");
    }

    // Verifies detect reads names stored as UTF 16 with surrogate pairs and lone surrogates
    @Test
    void detectReadsUtf16Names() {
        assertDetects(OsKind.WINDOWS, "\uD83D\uDE00win", "win\uD83D\uDE00", "\uDBFF\uDFFFWindows");
        assertDetects(OsKind.MACOS, "mac\uDFFF", "\u0100Mac OS X");
        assertDetects(OsKind.LINUX, "Linux\u0100", "\uD800linux", "\uD83Dunix");
        assertDetects(OsKind.UNKNOWN, "w\uD83D\uDE00in", "\uD800\uDC00", "\uFFFF");
    }

    // Verifies detect scans names of many millions of characters to the last character
    @Test
    void detectScansLongNames() {
        String filler = "x".repeat(LONG_LENGTH);
        assertSame(OsKind.UNKNOWN, detect(filler));
        assertSame(OsKind.LINUX, detect(filler + "linux"));
        assertSame(OsKind.WINDOWS, detect("linux" + filler + "win"));
        assertSame(OsKind.MACOS, detect("bsd" + filler + "Darwin"));
        assertSame(OsKind.UNKNOWN, detect(filler + "wi"));
        assertSame(OsKind.MACOS, detect("\u0100".repeat(LONG_LENGTH) + "Mac"));
    }

    // Verifies detect keeps no state between calls
    @Test
    void detectIsStateless() {
        for (int round = 0; round < 3; round++) {
            assertSame(OsKind.MACOS, detect("Mac OS X"));
            assertSame(OsKind.UNKNOWN, detect("SunOS"));
            assertSame(OsKind.WINDOWS, detect("Windows 11"));
            assertSame(OsKind.LINUX, detect("Linux"));
            assertSame(OsKind.UNKNOWN, detect(null));
        }
    }

    // Verifies detect agrees with the lower case reference for every UTF 16 unit in every pattern position
    @Test
    void detectMatchesReferenceForEveryCharInEveryPosition() throws ReflectiveOperationException {
        Method detect = freshDetect();
        for (String pattern : PATTERNS) {
            char[] chars = pattern.toCharArray();
            for (int position = 0; position < chars.length; position++) {
                for (int c = Character.MIN_VALUE; c <= Character.MAX_VALUE; c++) {
                    chars[position] = (char) c;
                    String name = new String(chars);
                    assertEquals(reference(name), kindName(detect, name), name);
                }
                chars[position] = pattern.charAt(position);
            }
        }
    }

    // Verifies detect agrees with the lower case reference for random names joined from overlapping fragments
    @Test
    void detectMatchesReferenceForRandomNames() throws ReflectiveOperationException {
        Method detect = freshDetect();
        Random random = new Random(0x05C1A55L);
        StringBuilder builder = new StringBuilder();
        for (int round = 0; round < RANDOM_NAMES; round++) {
            builder.setLength(0);
            for (int count = random.nextInt(MAX_FRAGMENTS + 1); count > 0; count--) {
                builder.append(FRAGMENTS[random.nextInt(FRAGMENTS.length)]);
            }
            String name = builder.toString();
            assertEquals(reference(name), kindName(detect, name), name);
        }
    }

    // Verifies the match helper compares each pattern letter against both ASCII cases
    @Test
    void matchesComparesAsciiCase() {
        assertTrue(matches("win", 0, "win"));
        assertTrue(matches("WIN", 0, "win"));
        assertTrue(matches("xWiN", 1, "win"));
        assertFalse(matches("xwin", 0, "win"));
        assertFalse(matches("wan", 0, "win"));
        assertFalse(matches("win", 0, "WIN"));
        assertFalse(matches("w\u0130n", 0, "win"));
    }

    // Verifies the match helper rejects indexes before the start and windows past the end
    @Test
    void matchesRejectsOutOfRangeIndexes() {
        assertTrue(matches("xxwin", 2, "win"));
        assertFalse(matches("xxwin", 3, "win"));
        assertFalse(matches("xxwin", 5, "win"));
        assertFalse(matches("win", -1, "win"));
        assertFalse(matches("win", Integer.MIN_VALUE, "win"));
        assertFalse(matches("win", Integer.MAX_VALUE, "win"));
        assertFalse(matches("wi", 0, "win"));
        assertFalse(matches("", 0, "win"));
    }

    // Verifies the match helper accepts an empty pattern at every index between 0 and the name length
    @Test
    void matchesAcceptsEmptyPatternInsideName() {
        assertTrue(matches("", 0, ""));
        assertTrue(matches("abc", 0, ""));
        assertTrue(matches("abc", 3, ""));
        assertFalse(matches("abc", 4, ""));
        assertFalse(matches("abc", -1, ""));
        assertFalse(matches("", Integer.MAX_VALUE, ""));
        assertFalse(matches("", Integer.MIN_VALUE, ""));
    }

    // Verifies the match helper rejects a null name or pattern
    @Test
    void matchesRejectsNullArguments() {
        assertThrows(NullPointerException.class, () -> matches(null, 0, "win"));
        assertThrows(NullPointerException.class, () -> matches("win", 0, null));
    }

    // Verifies values returns every kind in declaration order in a new array on each call
    @Test
    void valuesReturnsFreshArrayInDeclarationOrder() {
        OsKind[] expected = {OsKind.WINDOWS, OsKind.LINUX, OsKind.MACOS, OsKind.UNKNOWN};
        OsKind[] first = OsKind.values();
        assertArrayEquals(expected, first);
        first[0] = null;
        OsKind[] second = OsKind.values();
        assertNotSame(first, second);
        assertArrayEquals(expected, second);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(i, expected[i].ordinal());
        }
    }

    // Verifies valueOf resolves exact constant names and rejects every other name
    @Test
    void valueOfResolvesExactNames() {
        for (OsKind kind : OsKind.values()) {
            assertSame(kind, OsKind.valueOf(kind.name()));
            assertSame(kind, Enum.valueOf(OsKind.class, kind.toString()));
        }
        assertThrows(NullPointerException.class, () -> OsKind.valueOf(null));
        assertThrows(IllegalArgumentException.class, () -> OsKind.valueOf(""));
        assertThrows(IllegalArgumentException.class, () -> OsKind.valueOf("linux"));
        assertThrows(IllegalArgumentException.class, () -> OsKind.valueOf(" LINUX"));
        assertThrows(IllegalArgumentException.class, () -> OsKind.valueOf("MAC"));
    }
}
