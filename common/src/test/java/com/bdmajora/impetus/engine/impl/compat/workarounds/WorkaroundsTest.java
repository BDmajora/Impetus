package com.bdmajora.impetus.engine.impl.compat.workarounds;

import com.bdmajora.impetus.engine.impl.compat.environment.GlContextInfo;
import com.bdmajora.impetus.engine.impl.compat.environment.OsKind;
import com.bdmajora.impetus.engine.impl.compat.probe.GraphicsAdapterInfo;
import com.bdmajora.impetus.engine.impl.compat.probe.GraphicsVendor;
import com.bdmajora.testing.FreshClasses;
import com.bdmajora.testing.Statics;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Tests the issue registry and the legacy Intel adapter rule of Workarounds
class WorkaroundsTest {
    // Message of the warning init logs for the issues it finds
    private static final String WARNING = "Detected environment issues, mitigations/diagnostics enabled: {}";
    // Operating system kinds other than Windows
    private static final OsKind[] NOT_WINDOWS = {OsKind.LINUX, OsKind.MACOS, OsKind.UNKNOWN};
    // Context whose strings classify as no vendor
    private static final GlContextInfo EMPTY_CONTEXT = new GlContextInfo("", "", "");
    // Context of the NVIDIA Windows driver
    private static final GlContextInfo NVIDIA_CONTEXT = new GlContextInfo("NVIDIA Corporation", "NVIDIA GeForce RTX 5070/PCIe/SSE2", "4.6.0 NVIDIA 580.97");
    // Context of the Intel Windows driver
    private static final GlContextInfo INTEL_CONTEXT = new GlContextInfo("Intel", "Intel(R) HD Graphics 4000", "4.0.0 - Build 10.18.10.4");
    // Contexts that classify as NVIDIA
    private static final GlContextInfo[] NVIDIA_CONTEXTS = {
            NVIDIA_CONTEXT,
            new GlContextInfo("nvidia corporation", "", ""),
            new GlContextInfo("NVIDIA", null, null),
            new GlContextInfo("Nvidia Intel AMD", "GeForce", "4.6")};
    // Contexts that classify as Intel
    private static final GlContextInfo[] INTEL_CONTEXTS = {
            INTEL_CONTEXT,
            new GlContextInfo("Intel Corporation", "Intel(R) UHD Graphics 630", "4.6.0 - Build 31.0.101.5186"),
            new GlContextInfo("INTEL", null, null)};
    // Contexts that classify as neither NVIDIA nor Intel
    private static final GlContextInfo[] OTHER_CONTEXTS = {
            new GlContextInfo("ATI Technologies Inc.", "AMD Radeon RX 7900 XTX", "4.6.0 Compatibility Profile Context 24.10.1"),
            new GlContextInfo("Intel AMD", "Radeon", "4.6"),
            new GlContextInfo("Microsoft Corporation", "GDI Generic", "1.1.0"),
            new GlContextInfo("Intel", "Mesa Intel(R) UHD Graphics 620", "4.6"),
            new GlContextInfo("NVIDIA Corporation", "llvmpipe (LLVM 17.0.6, 256 bits)", "4.5 (Core Profile) Mesa 24.0.5"),
            new GlContextInfo("X.Org", "NV137", "4.3"),
            EMPTY_CONTEXT,
            new GlContextInfo(null, null, null)};
    // Intel adapter on a legacy driver
    private static final GraphicsAdapterInfo LEGACY_INTEL = new GraphicsAdapterInfo(GraphicsVendor.INTEL, "HD Graphics 4000", "10.18.10.4");
    // Intel adapter on a current driver
    private static final GraphicsAdapterInfo MODERN_INTEL = new GraphicsAdapterInfo(GraphicsVendor.INTEL, "Arc A770", "31.0.101.5186");
    // Intel adapter without a driver version
    private static final GraphicsAdapterInfo UNVERSIONED_INTEL = new GraphicsAdapterInfo(GraphicsVendor.INTEL, null, null);
    // NVIDIA adapter whose driver version starts like a legacy Intel one
    private static final GraphicsAdapterInfo NVIDIA_ADAPTER = new GraphicsAdapterInfo(GraphicsVendor.NVIDIA, "RTX 5070", "10.18.13.5382");
    // AMD adapter whose driver version starts like a legacy Intel one
    private static final GraphicsAdapterInfo AMD_ADAPTER = new GraphicsAdapterInfo(GraphicsVendor.AMD, "Radeon HD 7970", "9.18.10.3324");
    // Adapter without a vendor whose driver version starts like a legacy Intel one
    private static final GraphicsAdapterInfo UNNAMED_ADAPTER = new GraphicsAdapterInfo(null, null, "10.18.10.4");
    // Adapters that never count as a legacy Intel adapter
    private static final List<GraphicsAdapterInfo> NOT_LEGACY = List.of(MODERN_INTEL, UNVERSIONED_INTEL, NVIDIA_ADAPTER, AMD_ADAPTER, UNNAMED_ADAPTER);
    // Intel driver versions of the legacy branch
    private static final String[] LEGACY_VERSIONS = {"9.", "9.0", "9..", "9.17.10.4459", "10.", "10.0", "10.x", "10.18.10.4", "10.18.14.5074", "10.18.15.4281", "10." + "9".repeat(1 << 16)};
    // Intel driver versions outside the legacy branch including near misses
    private static final String[] MODERN_VERSIONS = {
            "", "9", "10", "1", "1.", "1.0", "0.", "8.", "8.15.10.2869", "11.", "11.0", "19.", "90.1", "99.", "100.", "100.9", "09.1", "010.18",
            " 9.17", " 10.18", "\t10.", "10,18", "9,17", "9-17", "10_18", "910.", "9" + (char) 0xFF0E, (char) 0xFF19 + ".", "1" + (char) 0xFF10 + ".",
            "20.19.15.4531", "26.20.100.7262", "27.20.100.8681", "31.0.101.5186", "1" + "0".repeat(1 << 16)};
    // Number of adapters in the long adapter lists
    private static final int LONG_ADAPTER_COUNT = 1 << 20;
    // Number of rounds of the concurrent mark tests
    private static final int ROUNDS = 2000;

    // Classification of the running JVM before the test
    private final OsKind realOs = OsKind.current();

    // Restores the operating system and clears the registry
    @AfterEach
    void restore() {
        Statics.set(OsKind.class, "detected", realOs);
        Workarounds.init(EMPTY_CONTEXT, List.of());
    }

    // Sets the operating system that init reads
    private static void os(OsKind kind) {
        Statics.set(OsKind.class, "detected", kind);
    }

    // Returns the issues isActive reports as active
    private static Set<Workarounds.Issue> active() {
        var issues = EnumSet.noneOf(Workarounds.Issue.class);

        for (var issue : Workarounds.Issue.values()) {
            if (Workarounds.isActive(issue)) {
                issues.add(issue);
            }
        }

        return issues;
    }

    // Returns the set the registry stores
    private static Set<Workarounds.Issue> stored() {
        AtomicReference<? extends Set<Workarounds.Issue>> active = Statics.get(Workarounds.class, "ACTIVE");
        return active.get();
    }

    // Returns the adapter list with the adapter inserted at the index
    private static List<GraphicsAdapterInfo> with(List<GraphicsAdapterInfo> adapters, int index, GraphicsAdapterInfo adapter) {
        var list = new ArrayList<>(adapters);
        list.add(index, adapter);
        return list;
    }

    // Returns true when init under Windows with an Intel context marks the no error context unsafe for the adapters
    private static boolean marksIntel(List<GraphicsAdapterInfo> adapters) {
        os(OsKind.WINDOWS);
        Workarounds.init(INTEL_CONTEXT, adapters);
        return Workarounds.isActive(Workarounds.Issue.NO_ERROR_CONTEXT_UNSAFE);
    }

    // Returns the answer of the adapter rule under Windows
    private static boolean legacy(List<GraphicsAdapterInfo> adapters) {
        return Workarounds.hasLegacyIntelWindowsAdapter(OsKind.WINDOWS, adapters);
    }

    // Checks that the class is final and its methods keep their modifiers
    @Test
    void theClassIsFinalAndItsMethodsAreStatic() throws ReflectiveOperationException {
        Class<?> type = Workarounds.class;
        assertEquals(Modifier.PUBLIC | Modifier.FINAL, type.getModifiers());

        for (Method method : new Method[]{
                type.getDeclaredMethod("init", GlContextInfo.class, List.class),
                type.getDeclaredMethod("markActive", Workarounds.Issue.class),
                type.getDeclaredMethod("isActive", Workarounds.Issue.class)}) {
            assertEquals(Modifier.PUBLIC | Modifier.STATIC, method.getModifiers(), method.getName());
            assertEquals(0, method.getExceptionTypes().length, method.getName());
        }

        Method rule = type.getDeclaredMethod("hasLegacyIntelWindowsAdapter", OsKind.class, List.class);
        assertEquals(Modifier.STATIC, rule.getModifiers());
        assertEquals(boolean.class, rule.getReturnType());
    }

    // Checks that the only constructor is private and creates an instance through reflection
    @Test
    void theOnlyConstructorIsPrivate() throws ReflectiveOperationException {
        Constructor<?>[] constructors = Workarounds.class.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        assertTrue(Modifier.isPrivate(constructors[0].getModifiers()));
        assertEquals(0, constructors[0].getParameterCount());

        constructors[0].setAccessible(true);
        assertInstanceOf(Workarounds.class, constructors[0].newInstance());
    }

    // Checks the order and names of the issues and the lookups by name
    @Test
    void theIssuesKeepTheirOrderAndNames() {
        Workarounds.Issue[] issues = {
                Workarounds.Issue.NVIDIA_THREADED_OPTIMIZATIONS,
                Workarounds.Issue.NO_ERROR_CONTEXT_UNSAFE,
                Workarounds.Issue.FRAME_HOOK_OVERLAY_PRESENT};
        assertArrayEquals(issues, Workarounds.Issue.values());

        // Changes one copy of the constants and reads a fresh one
        Workarounds.Issue.values()[0] = null;
        assertArrayEquals(issues, Workarounds.Issue.values());

        for (var issue : issues) {
            assertSame(issue, Workarounds.Issue.valueOf(issue.name()));
        }

        assertThrows(NullPointerException.class, () -> Workarounds.Issue.valueOf(null));
        assertThrows(IllegalArgumentException.class, () -> Workarounds.Issue.valueOf(""));
        assertThrows(IllegalArgumentException.class, () -> Workarounds.Issue.valueOf("nvidia_threaded_optimizations"));
        assertThrows(IllegalArgumentException.class, () -> Workarounds.Issue.valueOf(" NO_ERROR_CONTEXT_UNSAFE"));
    }

    // Checks that the registry starts empty and reads a null issue as inactive
    @Test
    void anEmptyRegistryHasNoActiveIssue() {
        assertEquals(Set.of(), active());
        assertFalse(Workarounds.isActive(null));
    }

    // Checks that no context marks an issue off Windows and that the adapters are never read there
    @Test
    void initMarksNothingOffWindowsWithoutReadingTheAdapters() {
        var contexts = new ArrayList<GlContextInfo>();
        contexts.addAll(Arrays.asList(NVIDIA_CONTEXTS));
        contexts.addAll(Arrays.asList(INTEL_CONTEXTS));
        contexts.addAll(Arrays.asList(OTHER_CONTEXTS));

        for (OsKind os : NOT_WINDOWS) {
            os(os);

            for (GlContextInfo context : contexts) {
                Workarounds.markActive(Workarounds.Issue.FRAME_HOOK_OVERLAY_PRESENT);
                Workarounds.init(context, new Unreadable());
                assertEquals(Set.of(), active(), os + " " + context);

                Workarounds.init(context, List.of(LEGACY_INTEL));
                assertEquals(Set.of(), active(), os + " " + context);

                Workarounds.init(context, Collections.singletonList(null));
                assertEquals(Set.of(), active(), os + " " + context);
            }
        }
    }

    // Checks on private copies of Workarounds and GraphicsVendor that init classifies the vendor once under Windows and never on any other system
    @Test
    void initClassifiesTheVendorOnlyUnderWindows() throws Throwable {
        var loader = new FreshClasses(Workarounds.class, GraphicsVendor.class);
        Class<?> vendorType = loader.initialise(GraphicsVendor.class);
        Class<?> type = loader.initialise(Workarounds.class);
        MethodHandle init = MethodHandles.privateLookupIn(type, MethodHandles.lookup())
                .findStatic(type, "init", MethodType.methodType(void.class, GlContextInfo.class, List.class));
        Method fromContext = vendorType.getMethod("fromContext", GlContextInfo.class);

        // Mocks the copy alone and leaves the shared GraphicsVendor untouched
        try (MockedStatic<?> vendors = Mockito.mockStatic(vendorType, Mockito.CALLS_REAL_METHODS)) {
            for (OsKind os : NOT_WINDOWS) {
                os(os);
                init.invoke(NVIDIA_CONTEXT, List.<GraphicsAdapterInfo>of());
            }

            vendors.verify(() -> fromContext.invoke(null, Mockito.any(GlContextInfo.class)), Mockito.never());

            os(OsKind.WINDOWS);
            init.invoke(NVIDIA_CONTEXT, List.<GraphicsAdapterInfo>of());
            vendors.verify(() -> fromContext.invoke(null, NVIDIA_CONTEXT), Mockito.times(1));
        }
    }

    // Checks that an NVIDIA context under Windows marks threaded optimizations alone without reading the adapters
    @Test
    void anNvidiaContextUnderWindowsMarksThreadedOptimizations() {
        os(OsKind.WINDOWS);

        for (GlContextInfo context : NVIDIA_CONTEXTS) {
            Workarounds.init(context, new Unreadable());
            assertEquals(Set.of(Workarounds.Issue.NVIDIA_THREADED_OPTIMIZATIONS), active(), context.toString());

            Workarounds.init(context, List.of());
            assertEquals(Set.of(Workarounds.Issue.NVIDIA_THREADED_OPTIMIZATIONS), active(), context.toString());

            Workarounds.init(context, Collections.singletonList(null));
            assertEquals(Set.of(Workarounds.Issue.NVIDIA_THREADED_OPTIMIZATIONS), active(), context.toString());
        }
    }

    // Checks that a context of any other vendor under Windows marks nothing without reading the adapters
    @Test
    void anyOtherContextUnderWindowsMarksNothing() {
        os(OsKind.WINDOWS);

        for (GlContextInfo context : OTHER_CONTEXTS) {
            Workarounds.init(context, new Unreadable());
            assertEquals(Set.of(), active(), context.toString());

            Workarounds.init(context, List.of());
            assertEquals(Set.of(), active(), context.toString());

            Workarounds.init(context, List.of(LEGACY_INTEL));
            assertEquals(Set.of(), active(), context.toString());
        }
    }

    // Checks that an Intel context under Windows marks the no error context unsafe alone for a legacy adapter or no adapter
    @Test
    void anIntelContextUnderWindowsMarksTheNoErrorContextUnsafe() {
        os(OsKind.WINDOWS);

        for (GlContextInfo context : INTEL_CONTEXTS) {
            for (List<GraphicsAdapterInfo> adapters : List.<List<GraphicsAdapterInfo>>of(List.of(), List.of(LEGACY_INTEL), with(NOT_LEGACY, 2, LEGACY_INTEL))) {
                Workarounds.init(context, adapters);
                assertEquals(Set.of(Workarounds.Issue.NO_ERROR_CONTEXT_UNSAFE), active(), context + " " + adapters);
            }

            Workarounds.init(context, NOT_LEGACY);
            assertEquals(Set.of(), active(), context.toString());
        }
    }

    // Checks that an empty adapter list counts as a legacy adapter whatever list type holds it and is answered from its size alone
    @Test
    void anEmptyAdapterListCountsAsALegacyAdapter() {
        for (List<GraphicsAdapterInfo> empty : List.<List<GraphicsAdapterInfo>>of(
                List.of(), new ArrayList<>(), new LinkedList<>(), Collections.emptyList(), new CopyOnWriteArrayList<>(),
                NOT_LEGACY.subList(2, 2), with(NOT_LEGACY, 0, LEGACY_INTEL).subList(0, 0), new SizeOnly(0))) {
            assertTrue(legacy(empty), empty.getClass().getName());
            assertTrue(marksIntel(empty), empty.getClass().getName());
        }
    }

    // Checks that a single adapter list is legacy only for an Intel adapter on a legacy driver
    @Test
    void aSingleAdapterIsLegacyOnlyForALegacyIntelDriver() {
        assertTrue(legacy(List.of(LEGACY_INTEL)));
        assertTrue(marksIntel(List.of(LEGACY_INTEL)));

        for (GraphicsAdapterInfo adapter : NOT_LEGACY) {
            assertFalse(legacy(List.of(adapter)), adapter.toString());
            assertFalse(marksIntel(List.of(adapter)), adapter.toString());
        }
    }

    // Checks that every driver version with major part 9 or 10 counts as legacy for an Intel adapter
    @Test
    void everyLegacyVersionCountsForAnIntelAdapter() {
        for (String version : LEGACY_VERSIONS) {
            var adapters = List.of(new GraphicsAdapterInfo(GraphicsVendor.INTEL, "HD Graphics", version));
            assertTrue(legacy(adapters), version.length() > 32 ? "long version" : version);
            assertTrue(marksIntel(adapters), version.length() > 32 ? "long version" : version);
        }
    }

    // Checks that driver versions outside the legacy branch never count including the near misses
    @Test
    void versionsOutsideTheLegacyBranchNeverCount() {
        for (String version : MODERN_VERSIONS) {
            var adapters = List.of(new GraphicsAdapterInfo(GraphicsVendor.INTEL, "HD Graphics", version));
            assertFalse(legacy(adapters), version.length() > 32 ? "long version" : version);
            assertFalse(marksIntel(adapters), version.length() > 32 ? "long version" : version);
        }
    }

    // Checks that a legacy version counts only for an Intel adapter and never for another vendor or a missing vendor
    @Test
    void aLegacyVersionCountsOnlyForAnIntelAdapter() {
        var vendors = new ArrayList<GraphicsVendor>();
        vendors.add(null);

        for (GraphicsVendor vendor : GraphicsVendor.values()) {
            if (vendor != GraphicsVendor.INTEL) {
                vendors.add(vendor);
            }
        }

        for (GraphicsVendor vendor : vendors) {
            for (String version : LEGACY_VERSIONS) {
                var adapters = List.of(new GraphicsAdapterInfo(vendor, "Adapter", version));
                assertFalse(legacy(adapters), vendor + " " + version.length());
                assertFalse(marksIntel(adapters), vendor + " " + version.length());
            }
        }
    }

    // Checks that an Intel adapter without a driver version reads like one with an empty version
    @Test
    void anIntelAdapterWithoutADriverVersionReadsAsAnEmptyVersion() {
        var empty = new GraphicsAdapterInfo(GraphicsVendor.INTEL, null, "");

        assertFalse(legacy(List.of(UNVERSIONED_INTEL)));
        assertEquals(legacy(List.of(empty)), legacy(List.of(UNVERSIONED_INTEL)));
        assertFalse(marksIntel(List.of(UNVERSIONED_INTEL, UNVERSIONED_INTEL)));
        assertTrue(legacy(List.of(UNVERSIONED_INTEL, LEGACY_INTEL)));
        assertTrue(legacy(List.of(LEGACY_INTEL, UNVERSIONED_INTEL)));
    }

    // Checks that one legacy Intel adapter counts at the first and last and every inner index
    @Test
    void oneLegacyIntelAdapterCountsAtAnyIndex() {
        for (int index = 0; index <= NOT_LEGACY.size(); index++) {
            var adapters = with(NOT_LEGACY, index, LEGACY_INTEL);
            assertTrue(legacy(adapters), "index " + index);
            assertTrue(marksIntel(adapters), "index " + index);
        }

        assertFalse(legacy(NOT_LEGACY));
        assertFalse(marksIntel(NOT_LEGACY));
    }

    // Checks that the rule reads the same through every list type and through views that cut the legacy adapter off at either end
    @Test
    void everyListTypeAndViewGivesTheSameAnswer() {
        List<Function<List<GraphicsAdapterInfo>, List<GraphicsAdapterInfo>>> types = List.of(
                ArrayList::new, LinkedList::new, CopyOnWriteArrayList::new, List::copyOf, Collections::unmodifiableList,
                list -> Arrays.asList(list.toArray(new GraphicsAdapterInfo[0])), Counting::new);

        for (var type : types) {
            for (int index = 0; index <= NOT_LEGACY.size(); index++) {
                var adapters = type.apply(with(NOT_LEGACY, index, LEGACY_INTEL));
                assertTrue(legacy(adapters), adapters.getClass().getName() + " " + index);
            }

            assertFalse(legacy(type.apply(NOT_LEGACY)), type.apply(NOT_LEGACY).getClass().getName());

            var first = type.apply(with(NOT_LEGACY, 0, LEGACY_INTEL));
            assertFalse(legacy(first.subList(1, first.size())));
            assertTrue(legacy(first.subList(0, 1)));

            var last = type.apply(with(NOT_LEGACY, NOT_LEGACY.size(), LEGACY_INTEL));
            assertFalse(legacy(last.subList(0, last.size() - 1)));
            assertTrue(legacy(last.subList(last.size() - 1, last.size())));
        }
    }

    // Checks that the scan reads each adapter once up to the first legacy Intel adapter and no further
    @Test
    void theScanReadsEachAdapterOnceUpToTheFirstMatch() {
        for (int index = 0; index <= NOT_LEGACY.size(); index++) {
            var adapters = new Counting(with(NOT_LEGACY, index, LEGACY_INTEL));
            assertTrue(legacy(adapters));
            assertEquals(index + 1, adapters.reads, "index " + index);
        }

        var none = new Counting(NOT_LEGACY);
        assertFalse(legacy(none));
        assertEquals(NOT_LEGACY.size(), none.reads);

        var twice = new Counting(List.of(LEGACY_INTEL, LEGACY_INTEL));
        assertTrue(legacy(twice));
        assertEquals(1, twice.reads);
    }

    // Checks that the scan stops at a leading legacy Intel adapter in lists of two and of the largest int size
    @Test
    void theScanStopsAtTheFirstLegacyIntelAdapter() {
        assertTrue(legacy(new FirstOnly(LEGACY_INTEL, 2)));
        assertTrue(legacy(new FirstOnly(LEGACY_INTEL, Integer.MAX_VALUE)));
        assertTrue(marksIntel(new FirstOnly(LEGACY_INTEL, Integer.MAX_VALUE)));
    }

    // Checks that a long list is scanned to its end with and without a legacy Intel adapter at the last index
    @Test
    void aLongListIsScannedToItsEnd() {
        var modern = Collections.nCopies(LONG_ADAPTER_COUNT, MODERN_INTEL);
        assertFalse(legacy(modern));
        assertFalse(marksIntel(modern));

        var adapters = new ArrayList<>(Collections.nCopies(LONG_ADAPTER_COUNT - 1, MODERN_INTEL));
        adapters.add(LEGACY_INTEL);
        assertTrue(legacy(adapters));
        assertTrue(marksIntel(adapters));
    }

    // Checks that a null adapter throws under Windows where it is read and is skipped after a match or off Windows
    @Test
    void aNullAdapterThrowsOnlyWhereItIsRead() {
        assertThrows(NullPointerException.class, () -> legacy(Collections.singletonList(null)));
        assertThrows(NullPointerException.class, () -> legacy(Arrays.asList(MODERN_INTEL, null)));
        assertThrows(NullPointerException.class, () -> marksIntel(Collections.singletonList(null)));
        assertThrows(NullPointerException.class, () -> marksIntel(Arrays.asList(MODERN_INTEL, null, LEGACY_INTEL)));

        assertTrue(legacy(Arrays.asList(LEGACY_INTEL, null)));
        assertTrue(marksIntel(Arrays.asList(LEGACY_INTEL, null)));

        for (OsKind os : NOT_WINDOWS) {
            assertFalse(Workarounds.hasLegacyIntelWindowsAdapter(os, Collections.singletonList(null)), os.name());
        }
    }

    // Checks that a throwing init leaves the stored issues as they were
    @Test
    void aThrowingInitLeavesTheIssuesUnchanged() {
        os(OsKind.WINDOWS);
        Workarounds.init(NVIDIA_CONTEXT, List.of());
        Workarounds.markActive(Workarounds.Issue.FRAME_HOOK_OVERLAY_PRESENT);
        var before = stored();

        assertThrows(NullPointerException.class, () -> marksIntel(Collections.singletonList(null)));
        assertThrows(NullPointerException.class, () -> Workarounds.init(null, List.of()));
        assertThrows(NullPointerException.class, () -> Workarounds.init(NVIDIA_CONTEXT, null));

        assertSame(before, stored());
        assertEquals(EnumSet.of(Workarounds.Issue.NVIDIA_THREADED_OPTIMIZATIONS, Workarounds.Issue.FRAME_HOOK_OVERLAY_PRESENT), active());
    }

    // Checks that init rejects a null context on every system whatever the adapters
    @Test
    void initRejectsANullContextOnEverySystem() {
        for (OsKind os : OsKind.values()) {
            os(os);

            for (List<GraphicsAdapterInfo> adapters : Arrays.<List<GraphicsAdapterInfo>>asList(List.of(), List.of(LEGACY_INTEL), new Unreadable(), null)) {
                var thrown = assertThrows(NullPointerException.class, () -> Workarounds.init(null, adapters));
                assertEquals("Context must not be null", thrown.getMessage(), os.name());
            }
        }
    }

    // Checks that init rejects a null adapter list on every system whatever the context
    @Test
    void initRejectsANullAdapterListOnEverySystem() {
        for (OsKind os : OsKind.values()) {
            os(os);

            for (GlContextInfo context : List.of(NVIDIA_CONTEXT, INTEL_CONTEXT, EMPTY_CONTEXT)) {
                var thrown = assertThrows(NullPointerException.class, () -> Workarounds.init(context, null));
                assertEquals("Adapters must not be null", thrown.getMessage(), os + " " + context);
            }
        }
    }

    // Checks that the adapter rule rejects a null operating system whatever the adapters
    @Test
    void theAdapterRuleRejectsANullOperatingSystem() {
        for (List<GraphicsAdapterInfo> adapters : Arrays.<List<GraphicsAdapterInfo>>asList(List.of(), List.of(LEGACY_INTEL), new Unreadable(), null)) {
            var thrown = assertThrows(NullPointerException.class, () -> Workarounds.hasLegacyIntelWindowsAdapter(null, adapters));
            assertEquals("Operating system must not be null", thrown.getMessage());
        }
    }

    // Checks that the adapter rule rejects a null adapter list on every system
    @Test
    void theAdapterRuleRejectsANullAdapterListOnEverySystem() {
        for (OsKind os : OsKind.values()) {
            var thrown = assertThrows(NullPointerException.class, () -> Workarounds.hasLegacyIntelWindowsAdapter(os, null));
            assertEquals("Adapters must not be null", thrown.getMessage(), os.name());
        }
    }

    // Checks that the adapter rule is false off Windows without reading the adapters
    @Test
    void theAdapterRuleIsFalseOffWindowsWithoutReadingTheAdapters() {
        for (OsKind os : NOT_WINDOWS) {
            for (List<GraphicsAdapterInfo> adapters : List.<List<GraphicsAdapterInfo>>of(
                    new Unreadable(), new SizeOnly(0), List.of(), List.of(LEGACY_INTEL), new FirstOnly(LEGACY_INTEL, Integer.MAX_VALUE))) {
                assertFalse(Workarounds.hasLegacyIntelWindowsAdapter(os, adapters), os + " " + adapters.getClass().getName());
            }
        }
    }

    // Checks that init with an Intel context under Windows agrees with the adapter rule for every list
    @Test
    void initAgreesWithTheAdapterRule() {
        var lists = new ArrayList<List<GraphicsAdapterInfo>>();
        lists.add(List.of());
        lists.add(NOT_LEGACY);

        for (int index = 0; index <= NOT_LEGACY.size(); index++) {
            lists.add(with(NOT_LEGACY, index, LEGACY_INTEL));
        }

        for (String version : MODERN_VERSIONS) {
            lists.add(List.of(new GraphicsAdapterInfo(GraphicsVendor.INTEL, "HD", version)));
        }

        for (var adapters : lists) {
            assertEquals(legacy(adapters), marksIntel(adapters), adapters.size() + " adapters");
        }
    }

    // Checks that each init replaces every earlier issue and that a repeated init gives the same issues
    @Test
    void initReplacesEveryEarlierIssue() {
        os(OsKind.WINDOWS);
        Workarounds.markActive(Workarounds.Issue.FRAME_HOOK_OVERLAY_PRESENT);
        Workarounds.markActive(Workarounds.Issue.NO_ERROR_CONTEXT_UNSAFE);

        Workarounds.init(NVIDIA_CONTEXT, List.of());
        assertEquals(Set.of(Workarounds.Issue.NVIDIA_THREADED_OPTIMIZATIONS), active());
        Workarounds.init(NVIDIA_CONTEXT, List.of());
        assertEquals(Set.of(Workarounds.Issue.NVIDIA_THREADED_OPTIMIZATIONS), active());

        Workarounds.init(INTEL_CONTEXT, List.of());
        assertEquals(Set.of(Workarounds.Issue.NO_ERROR_CONTEXT_UNSAFE), active());

        Workarounds.init(INTEL_CONTEXT, List.of(MODERN_INTEL));
        assertEquals(Set.of(), active());

        Workarounds.init(NVIDIA_CONTEXT, List.of());
        os(OsKind.LINUX);
        Workarounds.init(NVIDIA_CONTEXT, List.of());
        assertEquals(Set.of(), active());
    }

    // Checks that every init without an issue stores the same empty set and that a later mark leaves that set empty
    @Test
    void initWithoutAnIssueStoresOneSharedEmptySet() {
        Workarounds.init(EMPTY_CONTEXT, List.of());
        var empty = stored();
        assertEquals(Set.of(), empty);

        for (OsKind os : OsKind.values()) {
            os(os);
            Workarounds.markActive(Workarounds.Issue.FRAME_HOOK_OVERLAY_PRESENT);
            assertEquals(Set.of(), empty, os.name());

            Workarounds.init(OTHER_CONTEXTS[0], List.of());
            assertSame(empty, stored(), os.name());

            Workarounds.init(os == OsKind.WINDOWS ? INTEL_CONTEXT : NVIDIA_CONTEXT, List.of(MODERN_INTEL));
            assertSame(empty, stored(), os.name());
        }

        os(OsKind.WINDOWS);
        Workarounds.init(NVIDIA_CONTEXT, List.of());
        assertNotSame(empty, stored());
        assertEquals(Set.of(), empty);
    }

    // Checks that marking each issue in every order adds exactly that issue and keeps the earlier ones
    @Test
    void markActiveAddsOneIssueInEveryOrder() {
        var issues = Workarounds.Issue.values();
        int[][] orders = {{0, 1, 2}, {0, 2, 1}, {1, 0, 2}, {1, 2, 0}, {2, 0, 1}, {2, 1, 0}};

        for (int[] order : orders) {
            Workarounds.init(EMPTY_CONTEXT, List.of());
            var expected = EnumSet.noneOf(Workarounds.Issue.class);

            for (int index : order) {
                Workarounds.markActive(issues[index]);
                expected.add(issues[index]);
                assertEquals(expected, active(), Arrays.toString(order));
            }
        }
    }

    // Checks that marking an active issue again keeps the stored set and a new issue never changes a stored set
    @Test
    void markActiveCopiesTheStoredSetOnlyForANewIssue() {
        os(OsKind.WINDOWS);
        Workarounds.init(NVIDIA_CONTEXT, List.of());
        var initial = stored();

        Workarounds.markActive(Workarounds.Issue.NVIDIA_THREADED_OPTIMIZATIONS);
        assertSame(initial, stored());

        Workarounds.markActive(Workarounds.Issue.FRAME_HOOK_OVERLAY_PRESENT);
        var marked = stored();
        assertNotSame(initial, marked);
        assertEquals(Set.of(Workarounds.Issue.NVIDIA_THREADED_OPTIMIZATIONS), initial);

        for (int repeat = 0; repeat < 3; repeat++) {
            Workarounds.markActive(Workarounds.Issue.FRAME_HOOK_OVERLAY_PRESENT);
            Workarounds.markActive(Workarounds.Issue.NVIDIA_THREADED_OPTIMIZATIONS);
            assertSame(marked, stored());
        }

        assertEquals(Set.of(Workarounds.Issue.NVIDIA_THREADED_OPTIMIZATIONS, Workarounds.Issue.FRAME_HOOK_OVERLAY_PRESENT), marked);
    }

    // Checks that markActive rejects a null issue and leaves the stored set as it was
    @Test
    void markActiveRejectsANullIssue() {
        for (var issues : List.of(EnumSet.noneOf(Workarounds.Issue.class), EnumSet.allOf(Workarounds.Issue.class))) {
            Workarounds.init(EMPTY_CONTEXT, List.of());

            for (var issue : issues) {
                Workarounds.markActive(issue);
            }

            var before = stored();
            var thrown = assertThrows(NullPointerException.class, () -> Workarounds.markActive(null));
            assertEquals("Issue must not be null", thrown.getMessage());
            assertSame(before, stored());
            assertEquals(issues, active());
        }
    }

    // Checks that isActive reads a null issue as inactive on an empty and on a full registry
    @Test
    void isActiveReadsANullIssueAsInactive() {
        assertFalse(Workarounds.isActive(null));

        for (var issue : Workarounds.Issue.values()) {
            Workarounds.markActive(issue);
        }

        assertEquals(EnumSet.allOf(Workarounds.Issue.class), active());
        assertFalse(Workarounds.isActive(null));
    }

    // Checks that marks from one thread per issue started together are never lost
    @Test
    void concurrentMarksOfDifferentIssuesAreNeverLost() throws Exception {
        var issues = Workarounds.Issue.values();
        var barrier = new CyclicBarrier(issues.length);
        ExecutorService pool = Executors.newFixedThreadPool(issues.length);

        try {
            for (int round = 0; round < ROUNDS; round++) {
                Workarounds.init(EMPTY_CONTEXT, List.of());
                var tasks = new ArrayList<Future<?>>();

                for (var issue : issues) {
                    tasks.add(pool.submit(() -> {
                        barrier.await();
                        Workarounds.markActive(issue);
                        return null;
                    }));
                }

                for (var task : tasks) {
                    task.get(10, TimeUnit.SECONDS);
                }

                assertEquals(EnumSet.allOf(Workarounds.Issue.class), active(), "round " + round);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    // Checks that many threads marking every issue in rotated orders end with every issue active
    @Test
    void concurrentMarksOfTheSameIssuesEndWithEveryIssue() throws Exception {
        var issues = Workarounds.Issue.values();
        int threads = 8;
        var barrier = new CyclicBarrier(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        try {
            for (int round = 0; round < ROUNDS / 4; round++) {
                Workarounds.init(EMPTY_CONTEXT, List.of());
                var tasks = new ArrayList<Future<?>>();

                for (int thread = 0; thread < threads; thread++) {
                    int offset = thread;
                    tasks.add(pool.submit(() -> {
                        barrier.await();

                        for (int index = 0; index < issues.length; index++) {
                            Workarounds.markActive(issues[(index + offset) % issues.length]);
                        }

                        return null;
                    }));
                }

                for (var task : tasks) {
                    task.get(10, TimeUnit.SECONDS);
                }

                assertEquals(EnumSet.allOf(Workarounds.Issue.class), active(), "round " + round);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    // Checks that init logs one warning with the issues it finds
    @Test
    void initLogsTheIssuesItFinds() throws Throwable {
        var fresh = new Fresh();
        os(OsKind.WINDOWS);

        fresh.init.invoke(NVIDIA_CONTEXT, List.<GraphicsAdapterInfo>of());
        fresh.init.invoke(INTEL_CONTEXT, List.<GraphicsAdapterInfo>of());

        ArgumentCaptor<Object> issues = ArgumentCaptor.forClass(Object.class);
        Mockito.verify(fresh.logger, Mockito.times(2)).warn(Mockito.eq(WARNING), issues.capture());
        assertEquals(List.of("[NVIDIA_THREADED_OPTIMIZATIONS]", "[NO_ERROR_CONTEXT_UNSAFE]"), issues.getAllValues().stream().map(String::valueOf).toList());
        Mockito.verifyNoMoreInteractions(fresh.logger);
    }

    // Checks that init without an issue and markActive log nothing
    @Test
    void initWithoutAnIssueAndMarkActiveLogNothing() throws Throwable {
        var fresh = new Fresh();

        for (OsKind os : NOT_WINDOWS) {
            os(os);
            fresh.init.invoke(NVIDIA_CONTEXT, List.of(LEGACY_INTEL));
        }

        os(OsKind.WINDOWS);
        fresh.init.invoke(OTHER_CONTEXTS[0], List.<GraphicsAdapterInfo>of());
        fresh.init.invoke(INTEL_CONTEXT, List.of(MODERN_INTEL));

        for (Object issue : fresh.issueType.getEnumConstants()) {
            fresh.markActive.invoke(issue);
        }

        assertDoesNotThrow(() -> fresh.markActive.invoke(fresh.issueType.getEnumConstants()[0]));
        Mockito.verifyNoInteractions(fresh.logger);
    }

    // Copy of Workarounds with its own registry and a mock logger stored before its first call
    private static final class Fresh {
        // Mock logger of the copy
        final Logger logger = Mockito.mock(Logger.class);
        // Issue type of the copy
        final Class<?> issueType;
        // Handle of init on the copy
        final MethodHandle init;
        // Handle of markActive on the copy
        final MethodHandle markActive;

        // Loads the copy and stores the mock logger in it
        Fresh() throws ReflectiveOperationException {
            var loader = new FreshClasses(Workarounds.class);
            Class<?> type = loader.initialise(Workarounds.class);
            issueType = loader.load(Workarounds.Issue.class);
            Statics.set(type, "LOGGER", logger);

            var lookup = MethodHandles.privateLookupIn(type, MethodHandles.lookup());
            init = lookup.findStatic(type, "init", MethodType.methodType(void.class, GlContextInfo.class, List.class));
            markActive = lookup.findStatic(type, "markActive", MethodType.methodType(void.class, issueType));
        }
    }

    // Adapter list that throws an assertion error on any read
    private static final class Unreadable extends AbstractList<GraphicsAdapterInfo> {
        // Throws an assertion error for every index
        @Override
        public GraphicsAdapterInfo get(int index) {
            throw new AssertionError("Adapter " + index + " was read");
        }

        // Throws an assertion error
        @Override
        public int size() {
            throw new AssertionError("Adapter list size was read");
        }
    }

    // Adapter list of a fixed size whose iterator and adapters throw an assertion error
    private static final class SizeOnly extends AbstractList<GraphicsAdapterInfo> {
        // Number of adapters the list reports
        private final int size;

        // Creates a list that reports the size
        SizeOnly(int size) {
            this.size = size;
        }

        // Throws an assertion error for every index
        @Override
        public GraphicsAdapterInfo get(int index) {
            throw new AssertionError("Adapter " + index + " was read");
        }

        // Returns the reported number of adapters
        @Override
        public int size() {
            return size;
        }

        // Throws an assertion error
        @Override
        public Iterator<GraphicsAdapterInfo> iterator() {
            throw new AssertionError("Adapter list was iterated");
        }
    }

    // Adapter list whose first adapter is readable and whose later adapters throw an assertion error
    private static final class FirstOnly extends AbstractList<GraphicsAdapterInfo> {
        // Adapter at index 0
        private final GraphicsAdapterInfo first;
        // Number of adapters the list reports
        private final int size;

        // Creates a list of the size that holds the adapter at index 0
        FirstOnly(GraphicsAdapterInfo first, int size) {
            this.first = first;
            this.size = size;
        }

        // Returns the first adapter for index 0 and throws an assertion error for any other index
        @Override
        public GraphicsAdapterInfo get(int index) {
            if (index != 0) {
                throw new AssertionError("Adapter " + index + " was read");
            }

            return first;
        }

        // Returns the reported number of adapters
        @Override
        public int size() {
            return size;
        }
    }

    // Adapter list that counts the adapters read from a backing list
    private static final class Counting extends AbstractList<GraphicsAdapterInfo> {
        // List that holds the adapters
        private final List<GraphicsAdapterInfo> adapters;
        // Number of adapters read so far
        int reads;

        // Creates a counting view of the list
        Counting(List<GraphicsAdapterInfo> adapters) {
            this.adapters = adapters;
        }

        // Counts the read and returns the adapter at the index
        @Override
        public GraphicsAdapterInfo get(int index) {
            reads++;
            return adapters.get(index);
        }

        // Returns the number of adapters
        @Override
        public int size() {
            return adapters.size();
        }
    }
}
