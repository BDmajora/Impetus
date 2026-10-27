package com.bdmajora.impetus.engine.impl.compat.workarounds;

import com.bdmajora.impetus.engine.impl.compat.environment.GlContextInfo;
import com.bdmajora.impetus.engine.impl.compat.environment.OsKind;
import com.bdmajora.impetus.engine.impl.compat.probe.GraphicsAdapterInfo;
import com.bdmajora.impetus.engine.impl.compat.probe.GraphicsVendor;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Tests the no error context decision and the context mitigations of ContextCreationHints
class ContextCreationHintsTest {
    // Operating system kinds other than Windows
    private static final OsKind[] NOT_WINDOWS = {OsKind.LINUX, OsKind.MACOS, OsKind.UNKNOWN};
    // Intel adapter on a current driver
    private static final GraphicsAdapterInfo MODERN_INTEL = new GraphicsAdapterInfo(GraphicsVendor.INTEL, "Arc A770", "31.0.101.5186");
    // Intel adapter on a legacy driver
    private static final GraphicsAdapterInfo LEGACY_INTEL = new GraphicsAdapterInfo(GraphicsVendor.INTEL, "HD Graphics 4000", "10.18.10.4");
    // NVIDIA adapter whose driver version starts like a legacy Intel one
    private static final GraphicsAdapterInfo NVIDIA_ADAPTER = new GraphicsAdapterInfo(GraphicsVendor.NVIDIA, "RTX 5070", "10.18.13.5382");
    // AMD adapter whose driver version starts like a legacy Intel one
    private static final GraphicsAdapterInfo AMD_ADAPTER = new GraphicsAdapterInfo(GraphicsVendor.AMD, "Radeon HD 7970", "9.18.10.3324");
    // Context of the NVIDIA Windows driver
    private static final GlContextInfo NVIDIA_CONTEXT = new GlContextInfo("NVIDIA Corporation", "NVIDIA GeForce RTX 5070/PCIe/SSE2", "4.6.0 NVIDIA 580.97");
    // Session types that name Wayland with ASCII case ignored
    private static final String[] WAYLAND_SESSIONS = {"wayland", "Wayland", "WAYLAND", "wAyLaNd"};
    // Session types other than Wayland including near misses and a Cyrillic a in place of the Latin one
    private static final String[] OTHER_SESSIONS = {
            null, "", "x11", "tty", "mir", "unspecified", " wayland", "wayland ", "xwayland", "waylan", "waylands", "way land", "wayland\0",
            "w" + (char) 0x0430 + "yland"};
    // Intel driver versions of the legacy branch
    private static final String[] LEGACY_VERSIONS = {"9.", "9.17.10.4459", "10.", "10.18.10.4", "10.18.15.4281"};
    // Intel driver versions outside the legacy branch
    private static final String[] MODERN_VERSIONS = {"", "9", "10", "1.0", "09.1", "100.9", " 10.18", "11.0", "20.19.15.4531", "27.20.100.8681", "31.0.101.5186"};
    // Vendor strings that classify as NVIDIA
    private static final String[] NVIDIA_VENDORS = {"NVIDIA Corporation", "nvidia corporation", "Nvidia", "NVIDIA"};
    // Contexts that do not classify as NVIDIA
    private static final GlContextInfo[] OTHER_CONTEXTS = {
            new GlContextInfo("ATI Technologies Inc.", "AMD Radeon RX 7900 XTX", "4.6.0 Compatibility Profile Context 24.10.1"),
            new GlContextInfo("Advanced Micro Devices, Inc.", "Radeon", "4.6"),
            new GlContextInfo("Intel", "Intel(R) Arc(TM) A770 Graphics", "4.6.0 - Build 31.0.101.5186"),
            new GlContextInfo("Microsoft Corporation", "GDI Generic", "1.1.0"),
            new GlContextInfo("NVIDIA Corporation", "llvmpipe (LLVM 17.0.6, 256 bits)", "4.5 (Core Profile) Mesa 24.0.5"),
            new GlContextInfo("NVIDIA Corporation", "Mesa Intel(R) UHD Graphics 620", "4.6"),
            new GlContextInfo("X.Org", "NV137", "4.3 (Core Profile) Mesa 24.0.5"),
            new GlContextInfo("NVIDIA X.Org", "NV137", "4.3"),
            new GlContextInfo("", "NVIDIA GeForce RTX 5070", "4.6.0 NVIDIA 580.97"),
            new GlContextInfo("", "", ""),
            new GlContextInfo(null, null, null)};
    // Length of the long context strings in characters
    private static final int LONG_LENGTH = 1 << 22;
    // Number of adapters in the long adapter lists
    private static final int LONG_ADAPTER_COUNT = 1 << 20;

    // Clears the stubs and recorded calls of the test GL
    @AfterEach
    void reset() {
        TestGl.reset();
    }

    // Returns the decision for a requested context that is not a debug context
    private static boolean allows(OsKind os, String sessionType, List<GraphicsAdapterInfo> adapters) {
        return ContextCreationHints.allowsNoErrorContext(true, false, os, sessionType, adapters);
    }

    // Returns the answer of applyContextMitigations for the context and the system
    private static boolean apply(GlContextInfo context, OsKind os) {
        return ContextCreationHints.applyContextMitigations(context, os);
    }

    // Checks the class shape and the signatures of its members
    @Test
    void theClassIsAFinalHolderOfTwoStaticMethodsAndOneConstant() throws ReflectiveOperationException {
        Class<ContextCreationHints> type = ContextCreationHints.class;
        assertEquals(Modifier.PUBLIC | Modifier.FINAL, type.getModifiers());

        Method allows = type.getDeclaredMethod("allowsNoErrorContext", boolean.class, boolean.class, OsKind.class, String.class, List.class);
        Method apply = type.getDeclaredMethod("applyContextMitigations", GlContextInfo.class, OsKind.class);
        assertEquals(2, Arrays.stream(type.getDeclaredMethods()).filter(method -> !method.isSynthetic()).count());

        for (Method method : List.of(allows, apply)) {
            assertEquals(Modifier.PUBLIC | Modifier.STATIC, method.getModifiers());
            assertEquals(boolean.class, method.getReturnType());
            assertEquals(0, method.getExceptionTypes().length);
        }

        Field constant = type.getDeclaredField("DEBUG_OUTPUT_SYNCHRONOUS");
        assertEquals(Modifier.STATIC | Modifier.FINAL, constant.getModifiers());
        assertEquals(int.class, constant.getType());
        assertEquals(1, Arrays.stream(type.getDeclaredFields()).filter(field -> !field.isSynthetic()).count());
        assertEquals(0x8242, ContextCreationHints.DEBUG_OUTPUT_SYNCHRONOUS);
    }

    // Checks that the only constructor is private and takes no arguments and creates an instance when forced
    @Test
    void theOnlyConstructorIsPrivate() throws ReflectiveOperationException {
        Constructor<?>[] constructors = ContextCreationHints.class.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        assertTrue(Modifier.isPrivate(constructors[0].getModifiers()));
        assertEquals(0, constructors[0].getParameterCount());

        constructors[0].setAccessible(true);
        assertInstanceOf(ContextCreationHints.class, constructors[0].newInstance());
    }

    // Checks that a requested context is allowed where nothing rules it out
    @Test
    void aRequestedContextIsAllowedWhereNothingRulesItOut() {
        assertTrue(allows(OsKind.LINUX, "x11", List.of()));
        assertTrue(allows(OsKind.MACOS, null, List.of()));
        assertTrue(allows(OsKind.UNKNOWN, null, List.of()));
        assertTrue(allows(OsKind.WINDOWS, null, List.of(MODERN_INTEL)));
        assertTrue(allows(OsKind.WINDOWS, null, List.of(NVIDIA_ADAPTER)));
        assertTrue(allows(OsKind.WINDOWS, null, List.of(NVIDIA_ADAPTER, AMD_ADAPTER)));
        assertTrue(allows(OsKind.WINDOWS, null, List.of(MODERN_INTEL, NVIDIA_ADAPTER, AMD_ADAPTER)));
    }

    // Checks that a context off by option or alongside a debug context is refused on every system without reading the adapters
    @Test
    void aContextOffByOptionOrAlongsideADebugContextIsRefused() {
        for (OsKind os : OsKind.values()) {
            for (String session : new String[]{null, "x11", "wayland"}) {
                assertFalse(ContextCreationHints.allowsNoErrorContext(false, false, os, session, new Unreadable()));
                assertFalse(ContextCreationHints.allowsNoErrorContext(true, true, os, session, new Unreadable()));
                assertFalse(ContextCreationHints.allowsNoErrorContext(false, true, os, session, new Unreadable()));
            }
        }
    }

    // Checks that a Linux Wayland session in any ASCII case is refused without reading the adapters
    @Test
    void aLinuxWaylandSessionIsRefusedInAnyCase() {
        for (String session : WAYLAND_SESSIONS) {
            assertFalse(allows(OsKind.LINUX, session, new Unreadable()), session);
        }
    }

    // Checks that a Linux session type other than Wayland is allowed without reading the adapters
    @Test
    void aLinuxSessionOtherThanWaylandIsAllowed() {
        for (String session : OTHER_SESSIONS) {
            assertTrue(allows(OsKind.LINUX, session, new Unreadable()), String.valueOf(session));
        }
    }

    // Checks that a Wayland session type counts only on Linux
    @Test
    void aWaylandSessionTypeCountsOnlyOnLinux() {
        for (String session : WAYLAND_SESSIONS) {
            assertTrue(allows(OsKind.MACOS, session, new Unreadable()), session);
            assertTrue(allows(OsKind.UNKNOWN, session, new Unreadable()), session);
            assertTrue(allows(OsKind.WINDOWS, session, List.of(MODERN_INTEL)), session);
            assertFalse(allows(OsKind.WINDOWS, session, List.of(LEGACY_INTEL)), session);
        }
    }

    // Checks that an Intel adapter on the legacy driver branch refuses the context on Windows
    @Test
    void aLegacyIntelDriverRefusesTheContextOnWindows() {
        for (String version : LEGACY_VERSIONS) {
            assertFalse(allows(OsKind.WINDOWS, null, List.of(new GraphicsAdapterInfo(GraphicsVendor.INTEL, "HD Graphics", version))), version);
        }
    }

    // Checks that an Intel driver version outside the legacy branch allows the context on Windows
    @Test
    void anIntelDriverOutsideTheLegacyBranchAllowsTheContext() {
        for (String version : MODERN_VERSIONS) {
            assertTrue(allows(OsKind.WINDOWS, null, List.of(new GraphicsAdapterInfo(GraphicsVendor.INTEL, "Arc", version))), version);
        }
    }

    // Checks that a legacy driver version on an adapter that is not Intel allows the context
    @Test
    void aLegacyVersionCountsOnlyForIntel() {
        for (GraphicsVendor vendor : new GraphicsVendor[]{GraphicsVendor.NVIDIA, GraphicsVendor.AMD, GraphicsVendor.MESA, GraphicsVendor.OTHER, null}) {
            for (String version : LEGACY_VERSIONS) {
                assertTrue(allows(OsKind.WINDOWS, null, List.of(new GraphicsAdapterInfo(vendor, null, version))), vendor + " " + version);
            }
        }
    }

    // Checks that a legacy Intel adapter is ignored off Windows without reading the adapters
    @Test
    void aLegacyIntelAdapterIsIgnoredOffWindows() {
        for (OsKind os : NOT_WINDOWS) {
            assertTrue(allows(os, null, List.of(LEGACY_INTEL)), os.name());
            assertTrue(allows(os, "x11", new Unreadable()), os.name());
        }
    }

    // Checks that Windows without adapter information refuses the context and every other system allows it
    @Test
    void noAdapterInformationRefusesTheContextOnlyOnWindows() {
        assertFalse(allows(OsKind.WINDOWS, null, List.of()));
        assertFalse(allows(OsKind.WINDOWS, null, new ArrayList<>()));
        assertFalse(allows(OsKind.WINDOWS, null, Collections.emptyList()));

        for (OsKind os : NOT_WINDOWS) {
            assertTrue(allows(os, null, List.of()), os.name());
        }
    }

    // Checks that one legacy Intel adapter refuses the context at every index of the list
    @Test
    void oneLegacyIntelAdapterRefusesTheContextAtAnyIndex() {
        List<GraphicsAdapterInfo> others = List.of(MODERN_INTEL, NVIDIA_ADAPTER, AMD_ADAPTER);

        for (int index = 0; index <= others.size(); index++) {
            var adapters = new ArrayList<>(others);
            adapters.add(index, LEGACY_INTEL);
            assertFalse(allows(OsKind.WINDOWS, null, adapters), "index " + index);
        }
    }

    // Checks that the scan stops at a leading legacy Intel adapter in lists of two and of the largest int size
    @Test
    void theScanStopsAtTheFirstLegacyIntelAdapter() {
        assertFalse(allows(OsKind.WINDOWS, null, new FirstOnly(LEGACY_INTEL, 2)));
        assertFalse(allows(OsKind.WINDOWS, null, new FirstOnly(LEGACY_INTEL, Integer.MAX_VALUE)));
    }

    // Checks that a long list is scanned to its end with and without a legacy Intel adapter at the last index
    @Test
    void aLongListIsScannedToItsEnd() {
        assertTrue(allows(OsKind.WINDOWS, null, Collections.nCopies(LONG_ADAPTER_COUNT, MODERN_INTEL)));

        var adapters = new ArrayList<>(Collections.nCopies(LONG_ADAPTER_COUNT - 1, MODERN_INTEL));
        adapters.add(LEGACY_INTEL);
        assertFalse(allows(OsKind.WINDOWS, null, adapters));
    }

    // Checks that an adapter without a vendor or name counts as an adapter that is not Intel
    @Test
    void anAdapterWithoutAVendorIsNotIntel() {
        assertTrue(allows(OsKind.WINDOWS, null, List.of(new GraphicsAdapterInfo(null, null, "10.18.10.4"))));
        assertTrue(allows(OsKind.WINDOWS, null, List.of(new GraphicsAdapterInfo(null, null, null))));
    }

    // Checks that an Intel adapter without a driver version reads as an Intel adapter with an empty version
    @Test
    void anIntelAdapterWithoutADriverVersionReadsAsAnEmptyVersion() {
        var unversioned = new GraphicsAdapterInfo(GraphicsVendor.INTEL, null, null);
        assertTrue(allows(OsKind.WINDOWS, null, List.of(unversioned)));
        assertEquals(allows(OsKind.WINDOWS, null, List.of(new GraphicsAdapterInfo(GraphicsVendor.INTEL, null, ""))), allows(OsKind.WINDOWS, null, List.of(unversioned)));
        assertFalse(allows(OsKind.WINDOWS, null, List.of(unversioned, LEGACY_INTEL)));
        assertFalse(allows(OsKind.WINDOWS, null, List.of(LEGACY_INTEL, unversioned)));
    }

    // Checks that a null adapter throws on Windows where the adapters are read
    @Test
    void aNullAdapterThrowsWhereTheAdaptersAreRead() {
        assertThrows(NullPointerException.class, () -> allows(OsKind.WINDOWS, null, Collections.singletonList(null)));
        assertThrows(NullPointerException.class, () -> allows(OsKind.WINDOWS, null, Arrays.asList(MODERN_INTEL, null)));
    }

    // Checks that a null operating system is rejected whatever the other arguments
    @Test
    void aNullOperatingSystemIsRejectedForEveryFlag() {
        for (boolean requested : new boolean[]{false, true}) {
            for (boolean debug : new boolean[]{false, true}) {
                var thrown = assertThrows(NullPointerException.class,
                        () -> ContextCreationHints.allowsNoErrorContext(requested, debug, null, "wayland", new Unreadable()));
                assertEquals("Operating system must not be null", thrown.getMessage());

                thrown = assertThrows(NullPointerException.class,
                        () -> ContextCreationHints.allowsNoErrorContext(requested, debug, null, null, null));
                assertEquals("Operating system must not be null", thrown.getMessage());
            }
        }
    }

    // Checks that a null adapter list is rejected on every system and for every flag
    @Test
    void aNullAdapterListIsRejectedOnEverySystem() {
        for (OsKind os : OsKind.values()) {
            for (boolean requested : new boolean[]{false, true}) {
                for (boolean debug : new boolean[]{false, true}) {
                    var thrown = assertThrows(NullPointerException.class,
                            () -> ContextCreationHints.allowsNoErrorContext(requested, debug, os, "wayland", null));
                    assertEquals("Adapters must not be null", thrown.getMessage(), os + " " + requested + " " + debug);
                }
            }
        }
    }

    // Checks that repeated decisions agree and touch neither the adapters nor the GL
    @Test
    void repeatedDecisionsAgreeAndChangeNothing() {
        var adapters = new ArrayList<>(List.of(MODERN_INTEL, NVIDIA_ADAPTER));
        var before = List.copyOf(adapters);

        for (int call = 0; call < 3; call++) {
            assertTrue(allows(OsKind.WINDOWS, null, adapters));
            assertFalse(allows(OsKind.LINUX, "wayland", adapters));
            assertFalse(allows(OsKind.WINDOWS, null, List.of(LEGACY_INTEL)));
        }

        assertEquals(before, adapters);
        Mockito.verifyNoInteractions(TestGl.gl());
    }

    // Checks that an NVIDIA context on Windows with GL 4.3 gets synchronous debug output after one version query
    @Test
    void anNvidiaContextOnWindowsGetsSynchronousDebugOutput() {
        var gl = TestGl.gl();
        assertTrue(apply(NVIDIA_CONTEXT, OsKind.WINDOWS));

        InOrder order = Mockito.inOrder(gl);
        order.verify(gl).isOpenGLVersionSupported(4, 3);
        order.verify(gl).glEnable(0x8242);
        Mockito.verifyNoMoreInteractions(gl);
    }

    // Checks every spelling of the NVIDIA vendor and an NVIDIA context without renderer and version strings
    @Test
    void everyNvidiaVendorSpellingGetsTheMitigation() {
        for (String vendor : NVIDIA_VENDORS) {
            assertTrue(apply(new GlContextInfo(vendor, "GeForce", "4.6"), OsKind.WINDOWS), vendor);
        }

        assertTrue(apply(new GlContextInfo("NVIDIA Corporation", null, null), OsKind.WINDOWS));
        Mockito.verify(TestGl.gl(), Mockito.times(NVIDIA_VENDORS.length + 1)).glEnable(ContextCreationHints.DEBUG_OUTPUT_SYNCHRONOUS);
    }

    // Checks that an NVIDIA context off Windows is left alone without any GL call
    @Test
    void anNvidiaContextOffWindowsIsLeftAlone() {
        for (OsKind os : NOT_WINDOWS) {
            assertFalse(apply(NVIDIA_CONTEXT, os), os.name());
        }

        Mockito.verifyNoInteractions(TestGl.gl());
    }

    // Checks that a context that does not classify as NVIDIA is left alone on Windows without any GL call
    @Test
    void aContextOtherThanNvidiaIsLeftAlone() {
        for (GlContextInfo context : OTHER_CONTEXTS) {
            assertFalse(apply(context, OsKind.WINDOWS), String.valueOf(context));
        }

        Mockito.verifyNoInteractions(TestGl.gl());
    }

    // Checks that an NVIDIA context below GL 4.3 is left alone after one version query
    @Test
    void anNvidiaContextBelowGl43IsLeftAlone() {
        var gl = TestGl.gl();
        Mockito.doReturn(false).when(gl).isOpenGLVersionSupported(4, 3);

        assertFalse(apply(NVIDIA_CONTEXT, OsKind.WINDOWS));
        Mockito.verify(gl).isOpenGLVersionSupported(4, 3);
        Mockito.verifyNoMoreInteractions(gl);
    }

    // Checks that a failing version query propagates and enables nothing
    @Test
    void aFailingVersionQueryPropagates() {
        var gl = TestGl.gl();
        var failure = new IllegalStateException("No current context");
        Mockito.doThrow(failure).when(gl).isOpenGLVersionSupported(4, 3);

        assertSame(failure, assertThrows(IllegalStateException.class, () -> apply(NVIDIA_CONTEXT, OsKind.WINDOWS)));
        Mockito.verify(gl, Mockito.never()).glEnable(Mockito.anyInt());
    }

    // Checks that every call enables the state again
    @Test
    void everyCallAppliesTheMitigationAgain() {
        var gl = TestGl.gl();

        for (int call = 0; call < 3; call++) {
            assertTrue(apply(NVIDIA_CONTEXT, OsKind.WINDOWS));
        }

        Mockito.verify(gl, Mockito.times(3)).isOpenGLVersionSupported(4, 3);
        Mockito.verify(gl, Mockito.times(3)).glEnable(ContextCreationHints.DEBUG_OUTPUT_SYNCHRONOUS);
        Mockito.verifyNoMoreInteractions(gl);
    }

    // Checks that a null context or operating system is rejected on every system before any GL call
    @Test
    void aNullContextOrOperatingSystemIsRejected() {
        for (OsKind os : OsKind.values()) {
            var thrown = assertThrows(NullPointerException.class, () -> apply(null, os));
            assertEquals("Context must not be null", thrown.getMessage(), os.name());
        }

        assertEquals("Operating system must not be null", assertThrows(NullPointerException.class, () -> apply(NVIDIA_CONTEXT, null)).getMessage());
        assertEquals("Operating system must not be null", assertThrows(NullPointerException.class, () -> apply(OTHER_CONTEXTS[0], null)).getMessage());
        assertEquals("Context must not be null", assertThrows(NullPointerException.class, () -> apply(null, null)).getMessage());
        Mockito.verifyNoInteractions(TestGl.gl());
    }

    // Checks that long context strings are scanned to their ends
    @Test
    void longContextStringsAreScannedToTheirEnds() {
        String padding = " ".repeat(LONG_LENGTH);

        assertTrue(apply(new GlContextInfo(padding + "NVIDIA", padding, padding), OsKind.WINDOWS));
        assertFalse(apply(new GlContextInfo("NVIDIA" + padding, padding, padding + "Mesa"), OsKind.WINDOWS));
        assertFalse(apply(new GlContextInfo("NVIDIA" + padding, padding + "Mesa", padding), OsKind.WINDOWS));
        Mockito.verify(TestGl.gl()).glEnable(ContextCreationHints.DEBUG_OUTPUT_SYNCHRONOUS);
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
}
