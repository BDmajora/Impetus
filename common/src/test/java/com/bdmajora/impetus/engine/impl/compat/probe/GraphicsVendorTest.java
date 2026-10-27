package com.bdmajora.impetus.engine.impl.compat.probe;

import com.bdmajora.impetus.engine.impl.compat.environment.GlContextInfo;
import com.bdmajora.testing.FreshClasses;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

import static com.bdmajora.impetus.engine.impl.compat.probe.GraphicsVendor.AMD;
import static com.bdmajora.impetus.engine.impl.compat.probe.GraphicsVendor.INTEL;
import static com.bdmajora.impetus.engine.impl.compat.probe.GraphicsVendor.MESA;
import static com.bdmajora.impetus.engine.impl.compat.probe.GraphicsVendor.NVIDIA;
import static com.bdmajora.impetus.engine.impl.compat.probe.GraphicsVendor.OTHER;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

// Tests the vendor classification of GraphicsVendor
class GraphicsVendorTest {
    // PCI vendor ids that classify as a known vendor
    private static final int[] KNOWN_IDS = {0x10DE, 0x1002, 0x1022, 0x8086};
    // Vendors of the known ids in the same order
    private static final GraphicsVendor[] KNOWN_VENDORS = {NVIDIA, AMD, AMD, INTEL};
    // Patterns the vendor string is searched for
    private static final String[] VENDOR_PATTERNS = {"x.org", "nvidia", "ati", "amd", "advanced micro devices", "intel"};
    // Vendors of the vendor patterns in the same order
    private static final GraphicsVendor[] PATTERN_VENDORS = {MESA, NVIDIA, AMD, AMD, AMD, INTEL};
    // Pattern the version and renderer strings are searched for
    private static final String MESA_PATTERN = "mesa";
    // Whole word ati with ASCII case ignored and a capital I with dot above read as its i
    private static final Pattern ATI_WORD = Pattern.compile("(?<![A-Za-z])[Aa][Tt][Ii\u0130](?![A-Za-z])");
    // Fragments the random context strings are joined from
    private static final String[] FRAGMENTS = {
            "nvidia", "NVIDIA", "nvidi", "vidia", "ati", "ATI", "at", "a", "t", "i", "I", "\u0130", "\u0131", "\u0307",
            "amd", "AMD", "am", "md", "intel", "Intel", "inte", "ntel", "x.org", "X.Org", "x.or", "x", ".", "org", "\u000E",
            "mesa", "Mesa", "MESA", "mes", "esa", "m", "advanced micro devices", "Advanced Micro Devices", "advanced", "micro",
            "devices", " ", "\0", "\u212A", "k", "Corporation", "1", "\u00E9", "@", "["};
    // Largest number of fragments in a random string
    private static final int MAX_FRAGMENTS = 5;
    // Number of random contexts compared with the reference
    private static final int RANDOM_CONTEXTS = 200_000;
    // Length of the long strings in characters
    private static final int LONG_LENGTH = 1 << 22;
    // Number of calls made before the allocation count starts
    private static final int WARM_CALLS = 100_000;
    // Number of calls the allocation count covers
    private static final int COUNTED_CALLS = 100_000;
    // Number of threads that classify at the same time
    private static final int THREADS = 8;
    // Number of rounds each thread classifies the shared contexts
    private static final int ROUNDS = 2_000;

    // Returns the vendor fromPciVendorId assigns to the id
    private static GraphicsVendor pci(int id) {
        return GraphicsVendor.fromPciVendorId(id);
    }

    // Returns the vendor fromContext assigns to the three strings
    private static GraphicsVendor classify(String vendor, String renderer, String version) {
        return GraphicsVendor.fromContext(new GlContextInfo(vendor, renderer, version));
    }

    // Returns the vendor fromContext assigns to a vendor string with an empty renderer and version
    private static GraphicsVendor vendor(String vendor) {
        return classify(vendor, "", "");
    }

    // Returns the text or an empty string for null
    private static String orEmpty(String text) {
        return text == null ? "" : text;
    }

    // Returns the name of the vendor the lower case reference rules assign to the three strings
    private static String reference(String vendor, String renderer, String version) {
        String lowerVendor = orEmpty(vendor).toLowerCase(Locale.ROOT);

        if (orEmpty(version).toLowerCase(Locale.ROOT).contains(MESA_PATTERN)
                || orEmpty(renderer).toLowerCase(Locale.ROOT).contains(MESA_PATTERN)
                || lowerVendor.contains("x.org")) {
            return "MESA";
        } else if (lowerVendor.contains("nvidia")) {
            return "NVIDIA";
        } else if (ATI_WORD.matcher(orEmpty(vendor)).find() || lowerVendor.contains("amd") || lowerVendor.contains("advanced micro devices")) {
            return "AMD";
        } else if (lowerVendor.contains("intel")) {
            return "INTEL";
        }

        return "OTHER";
    }

    // Returns fromContext of a fresh uninstrumented copy of GraphicsVendor typed to return an enum constant
    private static MethodHandle freshFromContext() throws ReflectiveOperationException {
        Class<?> type = new FreshClasses(GraphicsVendor.class).load(GraphicsVendor.class);
        return MethodHandles.privateLookupIn(type, MethodHandles.lookup())
                .findStatic(type, "fromContext", MethodType.methodType(type, GlContextInfo.class))
                .asType(MethodType.methodType(Enum.class, GlContextInfo.class));
    }

    // Returns the constant name the fresh fromContext assigns to the context
    private static String freshName(MethodHandle fromContext, GlContextInfo context) throws Throwable {
        return ((Enum<?>) fromContext.invokeExact(context)).name();
    }

    // Asserts that the fresh fromContext and the reference agree on the three strings
    private static void assertMatchesReference(MethodHandle fromContext, String vendor, String renderer, String version) throws Throwable {
        String expected = reference(vendor, renderer, version);
        String actual = freshName(fromContext, new GlContextInfo(vendor, renderer, version));
        if (!expected.equals(actual)) {
            assertEquals(expected, actual, describe(vendor) + " | " + describe(renderer) + " | " + describe(version));
        }
    }

    // Returns the text with every char outside printable ASCII written as a Java escape
    private static String describe(String text) {
        if (text == null) {
            return "null";
        }

        var escaped = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            escaped.append(c >= ' ' && c <= '~' ? String.valueOf(c) : String.format("\\u%04X", (int) c));
        }

        return escaped.toString();
    }

    // Returns the pattern with the char at the slot replaced
    private static String withChar(String pattern, int slot, char c) {
        char[] chars = pattern.toCharArray();
        chars[slot] = c;
        return new String(chars);
    }

    // Returns a string joined from a random number of random fragments or null for one in sixteen
    private static String randomText(Random random) {
        if (random.nextInt(16) == 0) {
            return null;
        }

        var text = new StringBuilder();
        for (int count = random.nextInt(MAX_FRAGMENTS + 1); count > 0; count--) {
            text.append(FRAGMENTS[random.nextInt(FRAGMENTS.length)]);
        }

        return text.toString();
    }

    // Returns a random renderer or version that stays empty for three in four
    private static String rareText(Random random) {
        return random.nextInt(4) == 0 ? randomText(random) : "";
    }

    // Verifies the four known PCI vendor ids classify as their vendors
    @Test
    void knownPciIdsClassifyAsTheirVendors() {
        for (int k = 0; k < KNOWN_IDS.length; k++) {
            assertSame(KNOWN_VENDORS[k], pci(KNOWN_IDS[k]));
        }
    }

    // Verifies ids one above or one below a known id are OTHER
    @Test
    void pciIdsNextToKnownIdsAreOther() {
        for (int id : KNOWN_IDS) {
            assertSame(OTHER, pci(id - 1));
            assertSame(OTHER, pci(id + 1));
        }
    }

    // Verifies zero and negative ids and the int limits are OTHER
    @Test
    void pciIdsAtZeroNegativeValuesAndIntLimitsAreOther() {
        int[] ids = {0, -1, Integer.MIN_VALUE, Integer.MIN_VALUE + 1, Integer.MAX_VALUE, Integer.MAX_VALUE - 1, 0xFFFF, 0x10000};
        for (int id : ids) {
            assertSame(OTHER, pci(id), Integer.toHexString(id));
        }
        for (int id : KNOWN_IDS) {
            assertSame(OTHER, pci(-id));
            assertSame(OTHER, pci(~id));
        }
    }

    // Verifies a known id with any bit above the low 16 bits set is OTHER
    @Test
    void knownPciIdsWithHighBitsSetAreOther() {
        for (int id : KNOWN_IDS) {
            for (int bit = Short.SIZE; bit < Integer.SIZE; bit++) {
                assertSame(OTHER, pci(id | 1 << bit), Integer.toHexString(id | 1 << bit));
            }
            assertSame(OTHER, pci(id | 0xFFFF0000));
        }
        assertSame(OTHER, pci((short) 0x8086));
    }

    // Verifies the byte swapped forms of the known ids are OTHER
    @Test
    void byteSwappedPciIdsAreOther() {
        for (int id : KNOWN_IDS) {
            assertSame(OTHER, pci(Integer.reverseBytes(id) >>> Short.SIZE));
            assertSame(OTHER, pci(Integer.reverseBytes(id)));
        }
    }

    // Verifies exactly the four known ids among all 16 bit ids classify as a vendor
    @Test
    void onlyTheKnownIdsAmongAll16BitIdsClassify() {
        int classified = 0;
        for (int id = 0; id <= 0xFFFF; id++) {
            GraphicsVendor vendor = pci(id);
            int known = indexOf(id);
            if (known >= 0) {
                assertSame(KNOWN_VENDORS[known], vendor);
                classified++;
            } else {
                assertSame(OTHER, vendor, Integer.toHexString(id));
            }
        }
        assertEquals(KNOWN_IDS.length, classified);
    }

    // Returns the index of the id in the known ids or minus one
    private static int indexOf(int id) {
        for (int k = 0; k < KNOWN_IDS.length; k++) {
            if (KNOWN_IDS[k] == id) {
                return k;
            }
        }
        return -1;
    }

    // Verifies ids spread across the whole int range are OTHER unless known
    @Test
    void pciIdsAcrossTheWholeIntRangeAreOther() {
        for (long id = Integer.MIN_VALUE; id <= Integer.MAX_VALUE; id += 65_521) {
            int value = (int) id;
            assertSame(indexOf(value) < 0 ? OTHER : KNOWN_VENDORS[indexOf(value)], pci(value), Integer.toHexString(value));
        }
    }

    // Verifies repeated calls with the same id return the same constant
    @Test
    void repeatedPciCallsReturnTheSameConstant() {
        for (int round = 0; round < 3; round++) {
            for (int k = 0; k < KNOWN_IDS.length; k++) {
                assertSame(KNOWN_VENDORS[k], pci(KNOWN_IDS[k]));
            }
            assertSame(OTHER, pci(0));
        }
    }

    // Verifies a null context is rejected with a NullPointerException
    @Test
    void nullContextIsRejected() {
        assertThrows(NullPointerException.class, () -> GraphicsVendor.fromContext(null));
    }

    // Verifies a null string reads as an empty string in every position
    @Test
    void nullStringsReadAsEmpty() {
        for (int mask = 0; mask < 8; mask++) {
            String vendor = (mask & 1) == 0 ? "" : null;
            String renderer = (mask & 2) == 0 ? "" : null;
            String version = (mask & 4) == 0 ? "" : null;
            assertSame(OTHER, classify(vendor, renderer, version), Integer.toString(mask));
        }
        assertSame(MESA, classify(null, "", "4.6 Mesa 24.0.5"));
        assertSame(MESA, classify(null, "Mesa Intel", null));
        assertSame(MESA, classify("NVIDIA", null, "Mesa"));
        assertSame(MESA, classify("X.Org", null, null));
        assertSame(NVIDIA, classify("NVIDIA Corporation", null, null));
        assertSame(AMD, classify("ATI Technologies Inc.", null, ""));
        assertSame(INTEL, classify("Intel", "", null));
    }

    // Verifies empty strings classify as OTHER
    @Test
    void emptyStringsAreOther() {
        assertSame(OTHER, classify("", "", ""));
    }

    // Verifies strings reported by real drivers classify as the driver vendor
    @Test
    void realDriverStringsClassify() {
        assertSame(NVIDIA, classify("NVIDIA Corporation", "NVIDIA GeForce RTX 5070/PCIe/SSE2", "4.6.0 NVIDIA 615.71.09"));
        assertSame(AMD, classify("ATI Technologies Inc.", "AMD Radeon RX 7800 XT", "4.6.0 Compatibility Profile Context 24.3.1.240216"));
        assertSame(AMD, classify("Advanced Micro Devices, Inc.", "AMD Radeon Pro W7900", "4.6.0 Compatibility Profile Context 23.40"));
        assertSame(AMD, classify("AMD", "AMD Radeon Graphics", "4.6.0"));
        assertSame(INTEL, classify("Intel", "Intel(R) UHD Graphics 770", "4.6.0 - Build 31.0.101.5186"));
        assertSame(INTEL, classify("Intel Corporation", "Intel(R) HD Graphics 4000", "4.0.0 - Build 10.18.10.4"));
        assertSame(MESA, classify("AMD", "AMD Radeon RX 7800 XT (radeonsi, navi32, LLVM 17.0.6, DRM 3.57, 6.8.0)",
                "4.6 (Compatibility Profile) Mesa 24.0.5"));
        assertSame(MESA, classify("Intel", "Mesa Intel(R) UHD Graphics 770 (ADL-S GT1)", "4.6 (Compatibility Profile) Mesa 24.0.5"));
        assertSame(MESA, classify("X.Org", "AMD Radeon RX 580 Series (polaris10, LLVM 15.0.7, DRM 3.49, 6.1.0)",
                "4.6 (Compatibility Profile) Mesa 22.3.6"));
        assertSame(MESA, classify("nouveau", "NV134", "4.3 (Compatibility Profile) Mesa 24.0.5"));
        assertSame(MESA, classify("Mesa", "llvmpipe (LLVM 17.0.6, 256 bits)", "4.5 (Compatibility Profile) Mesa 24.0.5"));
        assertSame(MESA, classify("Mesa/X.org", "llvmpipe (LLVM 3.4, 128 bits)", "3.0"));
        assertSame(MESA, classify("Microsoft Corporation", "D3D12 (NVIDIA GeForce RTX 5070)", "4.6 (Compatibility Profile) Mesa 24.0.5"));
        assertSame(MESA, classify("VMware, Inc.", "SVGA3D; build: RELEASE; LLVM;", "4.1 (Compatibility Profile) Mesa 24.0.5"));
        assertSame(OTHER, classify("Microsoft Corporation", "GDI Generic", "1.1.0"));
        assertSame(OTHER, classify("Apple", "Apple M2", "4.1 Metal - 88"));
        assertSame(OTHER, classify("Imagination Technologies", "PowerVR Rogue GE8320", "OpenGL ES 3.2 build 1.13"));
        assertSame(OTHER, classify("Qualcomm", "Adreno (TM) 740", "OpenGL ES 3.2 V@0676.0"));
    }

    // Verifies Mesa in the version or renderer outranks every vendor string
    @Test
    void mesaInTheVersionOrRendererOutranksEveryVendor() {
        for (String vendor : new String[]{"NVIDIA", "ATI", "AMD", "Advanced Micro Devices", "Intel", "Apple", ""}) {
            assertSame(MESA, classify(vendor, "", "Mesa"));
            assertSame(MESA, classify(vendor, "Mesa", ""));
            assertSame(MESA, classify(vendor, "Mesa", "Mesa"));
        }
    }

    // Verifies an X.Org vendor outranks every other vendor name wherever it sits
    @Test
    void xOrgVendorOutranksEveryOtherNameAnywhere() {
        assertSame(MESA, vendor("X.Org NVIDIA"));
        assertSame(MESA, vendor("NVIDIA X.Org"));
        assertSame(MESA, vendor("ATI AMD Intel x.org"));
        assertSame(MESA, vendor("intel x.org nvidia"));
        assertSame(MESA, vendor("Advanced Micro Devices X.Org"));
    }

    // Verifies NVIDIA outranks AMD and AMD outranks Intel in any order
    @Test
    void vendorNamesRankTheSameInAnyOrder() {
        assertSame(NVIDIA, vendor("NVIDIA AMD Intel"));
        assertSame(NVIDIA, vendor("Intel AMD NVIDIA"));
        assertSame(NVIDIA, vendor("AMD NVIDIA"));
        assertSame(NVIDIA, vendor("Intel NVIDIA"));
        assertSame(NVIDIA, vendor("NVIDIA NVIDIA"));
        assertSame(AMD, vendor("Intel AMD"));
        assertSame(AMD, vendor("AMD Intel"));
        assertSame(AMD, vendor("Intel ATI"));
        assertSame(AMD, vendor("Intel Advanced Micro Devices"));
        assertSame(AMD, vendor("AMD ATI"));
        assertSame(INTEL, vendor("Intel Intel"));
    }

    // Verifies every pattern matches with ASCII case ignored
    @Test
    void patternsMatchWithAsciiCaseIgnored() {
        assertSame(NVIDIA, vendor("nvidia"));
        assertSame(NVIDIA, vendor("NVIDIA"));
        assertSame(NVIDIA, vendor("nViDiA"));
        assertSame(AMD, vendor("aMd"));
        assertSame(AMD, vendor("AtI"));
        assertSame(AMD, vendor("ADVANCED MICRO DEVICES"));
        assertSame(AMD, vendor("advanced MICRO devices"));
        assertSame(INTEL, vendor("INTEL"));
        assertSame(INTEL, vendor("iNtEl"));
        assertSame(MESA, vendor("X.ORG"));
        assertSame(MESA, vendor("x.OrG"));
        assertSame(MESA, classify("", "", "MESA"));
        assertSame(MESA, classify("", "mEsA", ""));
    }

    // Verifies each pattern counts only in the string it belongs to
    @Test
    void patternsCountOnlyInTheirOwnString() {
        for (String name : new String[]{"NVIDIA", "ATI", "AMD", "Advanced Micro Devices", "Intel", "X.Org"}) {
            assertSame(OTHER, classify("", name, name), name);
        }
        assertSame(OTHER, vendor("Mesa"));
    }

    // Verifies a pattern without its first or last char is OTHER
    @Test
    void truncatedPatternsAreOther() {
        for (String pattern : VENDOR_PATTERNS) {
            assertSame(OTHER, vendor(pattern.substring(1)), pattern);
            assertSame(OTHER, vendor(pattern.substring(0, pattern.length() - 1)), pattern);
        }
        assertSame(OTHER, classify("", "mes", "esa"));
        assertSame(OTHER, classify("", "esa", "mes"));
    }

    // Verifies patterns match at every offset of a padded string and as the whole string
    @Test
    void patternsMatchAtEveryOffset() {
        for (int p = 0; p < VENDOR_PATTERNS.length; p++) {
            String pattern = VENDOR_PATTERNS[p];
            assertSame(PATTERN_VENDORS[p], vendor(pattern), pattern);
            for (int offset = 0; offset <= 8; offset++) {
                String padded = "-".repeat(offset) + pattern + "-".repeat(8 - offset);
                assertSame(PATTERN_VENDORS[p], vendor(padded), padded);
            }
        }
        for (int offset = 0; offset <= 8; offset++) {
            String padded = "-".repeat(offset) + MESA_PATTERN + "-".repeat(8 - offset);
            assertSame(MESA, classify("", padded, ""), padded);
            assertSame(MESA, classify("", "", padded), padded);
        }
        assertSame(MESA, classify("", "", MESA_PATTERN));
        assertSame(MESA, classify("", MESA_PATTERN, ""));
    }

    // Verifies ati classifies as AMD only when no ASCII letter touches it
    @Test
    void atiMatchesOnlyAsAWholeWord() {
        assertSame(AMD, vendor("ATI"));
        assertSame(AMD, vendor("ATI Technologies Inc."));
        assertSame(AMD, vendor("(ATI)"));
        assertSame(AMD, vendor("3ATI4"));
        assertSame(AMD, vendor("@ati["));
        assertSame(AMD, vendor("`ati{"));
        assertSame(AMD, vendor("ATI\u0307"));
        assertSame(AMD, vendor("Corporation ATI"));
        assertSame(OTHER, vendor("Microsoft Corporation"));
        assertSame(OTHER, vendor("Imagination Technologies"));
        assertSame(OTHER, vendor("Kati"));
        assertSame(OTHER, vendor("xati"));
        assertSame(OTHER, vendor("atix"));
        assertSame(OTHER, vendor("Zati"));
        assertSame(OTHER, vendor("atiZ"));
        assertSame(OTHER, vendor("aati"));
        assertSame(OTHER, vendor("atia"));
        assertSame(INTEL, vendor("Intel Corporation"));
        assertSame(NVIDIA, vendor("NVIDIA Corporation"));
    }

    // Verifies punctuation in a pattern matches only the same char
    @Test
    void punctuationInPatternsMatchesOnlyItself() {
        assertSame(MESA, vendor("x.org"));
        assertSame(OTHER, vendor("x\u000Eorg"));
        assertSame(OTHER, vendor("xNorg"));
        assertSame(OTHER, vendor("x,org"));
        assertSame(AMD, vendor("advanced micro devices"));
        assertSame(OTHER, vendor("advanced\0micro\0devices"));
        assertSame(OTHER, vendor("advanced  micro devices"));
        assertSame(OTHER, vendor("advanced\tmicro\tdevices"));
    }

    // Verifies letters outside ASCII never fold into a pattern except a capital I with dot above as the last i of ati
    @Test
    void lettersOutsideAsciiDoNotFoldIntoPatterns() {
        assertSame(OTHER, vendor("\uFF2E\uFF36\uFF29\uFF24\uFF29\uFF21"));
        assertSame(OTHER, vendor("\u0131ntel"));
        assertSame(OTHER, vendor("\u0130ntel"));
        assertSame(OTHER, vendor("nv\u0130dia"));
        assertSame(OTHER, vendor("nvid\u0130a"));
        assertSame(OTHER, vendor("Advanced M\u0130cro Devices"));
        assertSame(OTHER, vendor("\u212A"));
        assertSame(OTHER, classify("", "\u039CESA", "M\u0395SA"));
        assertSame(AMD, vendor("AT\u0130"));
        assertSame(AMD, vendor("at\u0130 Technologies"));
        assertSame(OTHER, vendor("AT\u0130x"));
    }

    // Verifies every char in every slot of every pattern classifies as the reference does
    @Test
    void everyCharInEveryPatternSlotMatchesTheReference() throws Throwable {
        MethodHandle fromContext = freshFromContext();

        for (String pattern : VENDOR_PATTERNS) {
            for (int slot = 0; slot < pattern.length(); slot++) {
                for (int c = Character.MIN_VALUE; c <= Character.MAX_VALUE; c++) {
                    assertMatchesReference(fromContext, withChar(pattern, slot, (char) c), "", "");
                }
            }
        }
        for (int slot = 0; slot < MESA_PATTERN.length(); slot++) {
            for (int c = Character.MIN_VALUE; c <= Character.MAX_VALUE; c++) {
                String text = withChar(MESA_PATTERN, slot, (char) c);
                assertMatchesReference(fromContext, "", text, "");
                assertMatchesReference(fromContext, "", "", text);
            }
        }
    }

    // Verifies random contexts joined from pattern fragments classify as the reference does
    @Test
    void randomContextsMatchTheReference() throws Throwable {
        MethodHandle fromContext = freshFromContext();
        var random = new Random(0x6E76_6964_6961L);

        for (int n = 0; n < RANDOM_CONTEXTS; n++) {
            assertMatchesReference(fromContext, randomText(random), rareText(random), rareText(random));
        }
    }

    // Verifies a pattern at the end of a very long string classifies and a long string without one is OTHER
    @Test
    void longStringsClassifyByAPatternAtTheirEnd() {
        String as = "a".repeat(LONG_LENGTH);
        String ats = "at".repeat(LONG_LENGTH / 2);
        String ms = "m".repeat(LONG_LENGTH);
        assertSame(OTHER, vendor(as));
        assertSame(NVIDIA, vendor(as + "nvidia"));
        assertSame(AMD, vendor(as + "md"));
        assertSame(OTHER, vendor(ats));
        assertSame(OTHER, vendor(ats + "i"));
        assertSame(AMD, vendor(ats + " ati"));
        assertSame(INTEL, vendor("i".repeat(LONG_LENGTH) + "intel"));
        assertSame(MESA, vendor("x".repeat(LONG_LENGTH) + "x.org"));
        assertSame(NVIDIA, vendor("n".repeat(LONG_LENGTH) + "nvidia"));
        assertSame(OTHER, classify("", ms, ms));
        assertSame(MESA, classify("", "", ms + "esa"));
        assertSame(MESA, classify("", ms + "esa", ms));
    }

    // Verifies repeated calls with the same context return the same constant
    @Test
    void repeatedContextCallsReturnTheSameConstant() {
        var context = new GlContextInfo("Intel", "Intel(R) UHD Graphics 770", "4.6.0 - Build 31.0.101.5186");
        for (int round = 0; round < 3; round++) {
            assertSame(INTEL, GraphicsVendor.fromContext(context));
        }
    }

    // Verifies fromContext allocates no heap memory for strings in mixed case
    @Test
    void fromContextAllocatesNoHeapMemory() {
        var threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        assumeTrue(threads.isThreadAllocatedMemorySupported() && threads.isThreadAllocatedMemoryEnabled());
        GlContextInfo[] contexts = {
                new GlContextInfo("NVIDIA Corporation", "NVIDIA GeForce RTX 5070/PCIe/SSE2", "4.6.0 NVIDIA 615.71.09"),
                new GlContextInfo("ATI Technologies Inc.", "AMD Radeon RX 7800 XT", "4.6.0 Compatibility Profile Context"),
                new GlContextInfo("Intel Corporation", "Intel(R) UHD Graphics 770", "4.6.0 - Build 31.0.101.5186"),
                new GlContextInfo("X.Org", "AMD Radeon RX 580", "4.6 (Compatibility Profile) Mesa 22.3.6")};

        long sink = threads.getCurrentThreadAllocatedBytes();
        for (int call = 0; call < WARM_CALLS; call++) {
            sink += GraphicsVendor.fromContext(contexts[call & 3]).ordinal();
        }
        long before = threads.getCurrentThreadAllocatedBytes();
        for (int call = 0; call < COUNTED_CALLS; call++) {
            sink += GraphicsVendor.fromContext(contexts[call & 3]).ordinal();
        }
        long allocated = threads.getCurrentThreadAllocatedBytes() - before;

        assertTrue(allocated < COUNTED_CALLS, allocated + " bytes");
        assertTrue(sink != 0);
    }

    // Verifies threads that classify at the same time all get the single thread results
    @Test
    void concurrentCallsAgreeWithSingleThreadResults() throws Exception {
        GlContextInfo[] contexts = {
                new GlContextInfo("NVIDIA Corporation", "", ""),
                new GlContextInfo("ATI Technologies Inc.", "", ""),
                new GlContextInfo("Intel Corporation", "", ""),
                new GlContextInfo("X.Org", "", ""),
                new GlContextInfo("Apple", "", "4.6 Mesa")};
        GraphicsVendor[] expected = new GraphicsVendor[contexts.length];
        for (int k = 0; k < contexts.length; k++) {
            expected[k] = GraphicsVendor.fromContext(contexts[k]);
        }

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int t = 0; t < THREADS; t++) {
                results.add(pool.submit(() -> {
                    int mismatches = 0;
                    for (int round = 0; round < ROUNDS; round++) {
                        for (int k = 0; k < contexts.length; k++) {
                            if (GraphicsVendor.fromContext(contexts[k]) != expected[k]) {
                                mismatches++;
                            }
                        }
                    }
                    return mismatches;
                }));
            }
            for (Future<Integer> result : results) {
                assertEquals(0, result.get());
            }
        } finally {
            pool.shutdownNow();
        }
    }

    // Verifies values lists the constants in declaration order in a fresh array on every call
    @Test
    void valuesListsTheConstantsInDeclarationOrder() {
        GraphicsVendor[] values = GraphicsVendor.values();
        assertArrayEquals(new GraphicsVendor[]{NVIDIA, AMD, INTEL, MESA, OTHER}, values);
        values[0] = null;
        assertSame(NVIDIA, GraphicsVendor.values()[0]);
        assertNotSame(GraphicsVendor.values(), GraphicsVendor.values());
    }

    // Verifies valueOf returns each constant by its exact name and rejects null and every other name
    @Test
    void valueOfReturnsConstantsByExactName() {
        for (GraphicsVendor vendor : GraphicsVendor.values()) {
            assertSame(vendor, GraphicsVendor.valueOf(vendor.name()));
        }
        assertThrows(NullPointerException.class, () -> GraphicsVendor.valueOf(null));
        assertThrows(IllegalArgumentException.class, () -> GraphicsVendor.valueOf(""));
        assertThrows(IllegalArgumentException.class, () -> GraphicsVendor.valueOf("nvidia"));
        assertThrows(IllegalArgumentException.class, () -> GraphicsVendor.valueOf("ATI"));
    }

    // Verifies the public methods are exactly the two classifiers and the enum methods
    @Test
    void publicMethodsAreTheClassifiersAndEnumMethods() {
        List<String> names = new ArrayList<>();
        for (Method method : GraphicsVendor.class.getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers())) {
                names.add(method.getName() + Arrays.toString(method.getParameterTypes()));
            }
        }
        names.sort(null);
        assertEquals(List.of(
                "fromContext[class com.bdmajora.impetus.engine.impl.compat.environment.GlContextInfo]",
                "fromPciVendorId[int]",
                "valueOf[class java.lang.String]",
                "values[]"), names);
    }
}
