package com.bdmajora.impetus.engine.impl.compat.environment;

import com.bdmajora.impetus.lwjgl.LWJGLService;
import com.bdmajora.testing.Statics;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Tests the driver string snapshot of GlContextInfo
class GlContextInfoTest {
    // GL enum value of the vendor string query
    private static final int GL_VENDOR = 0x1F00;
    // GL enum value of the renderer string query
    private static final int GL_RENDERER = 0x1F01;
    // GL enum value of the version string query
    private static final int GL_VERSION = 0x1F02;
    // Vendor string of a typical NVIDIA driver
    private static final String VENDOR = "NVIDIA Corporation";
    // Renderer string of a typical NVIDIA driver
    private static final String RENDERER = "NVIDIA GeForce RTX 5070/PCIe/SSE2";
    // Version string of a typical NVIDIA driver
    private static final String VERSION = "4.6.0 NVIDIA 580.95.05";
    // Length of the long driver strings in characters
    private static final int LONG_LENGTH = 1 << 20;

    // Stubs the vendor, renderer and version strings on the test GL
    private static void driver(String vendor, String renderer, String version) {
        LWJGLService gl = TestGl.gl();
        Mockito.doReturn(vendor).when(gl).glGetString(GL_VENDOR);
        Mockito.doReturn(renderer).when(gl).glGetString(GL_RENDERER);
        Mockito.doReturn(version).when(gl).glGetString(GL_VERSION);
    }

    // Returns an equal string that is a separate instance
    private static String copy(String value) {
        return new String(value);
    }

    // Verifies capture returns the driver strings without copying them
    @Test
    void captureReturnsDriverStrings() {
        driver(VENDOR, RENDERER, VERSION);
        GlContextInfo info = GlContextInfo.capture();
        assertSame(VENDOR, info.vendor());
        assertSame(RENDERER, info.renderer());
        assertSame(VERSION, info.version());
    }

    // Verifies capture replaces every null string with the shared empty string
    @Test
    void captureReplacesNullStrings() {
        driver(null, null, null);
        GlContextInfo info = GlContextInfo.capture();
        assertSame("", info.vendor());
        assertSame("", info.renderer());
        assertSame("", info.version());
        assertEquals(new GlContextInfo("", "", ""), info);
    }

    // Verifies capture replaces each null string on its own and keeps the other strings
    @Test
    void captureReplacesEachNullStringIndependently() {
        // Each bit of the mask marks one string as missing
        for (int mask = 0; mask < 8; mask++) {
            String vendor = (mask & 1) == 0 ? VENDOR : null;
            String renderer = (mask & 2) == 0 ? RENDERER : null;
            String version = (mask & 4) == 0 ? VERSION : null;
            driver(vendor, renderer, version);
            GlContextInfo info = GlContextInfo.capture();
            assertSame(vendor == null ? "" : vendor, info.vendor());
            assertSame(renderer == null ? "" : renderer, info.renderer());
            assertSame(version == null ? "" : version, info.version());
        }
    }

    // Verifies capture keeps empty and blank strings without trimming them
    @Test
    void captureKeepsEmptyAndBlankStrings() {
        String empty = copy("");
        String blank = " ";
        String whitespace = " \t\r\n ";
        driver(empty, blank, whitespace);
        GlContextInfo info = GlContextInfo.capture();
        assertSame(empty, info.vendor());
        assertSame(blank, info.renderer());
        assertSame(whitespace, info.version());
    }

    // Verifies capture keeps strings of a single character
    @Test
    void captureKeepsSingleCharacterStrings() {
        driver("A", "R", "4");
        assertEquals(new GlContextInfo("A", "R", "4"), GlContextInfo.capture());
    }

    // Verifies capture keeps control characters and surrogate characters unchanged
    @Test
    void captureKeepsUnusualCharacters() {
        String nul = (char) 0 + "vendor" + (char) 0;
        String astral = new String(Character.toChars(0x1F600)) + (char) 0xFFFF;
        String lone = (char) 0xD800 + "4.6" + (char) 0xDFFF;
        driver(nul, astral, lone);
        GlContextInfo info = GlContextInfo.capture();
        assertSame(nul, info.vendor());
        assertSame(astral, info.renderer());
        assertSame(lone, info.version());
        assertEquals(8, info.vendor().length());
        assertEquals(3, info.renderer().length());
        assertEquals(5, info.version().length());
    }

    // Verifies capture keeps strings longer than one million characters
    @Test
    void captureKeepsLongStrings() {
        String vendor = "v".repeat(LONG_LENGTH);
        String renderer = "r".repeat(LONG_LENGTH);
        String version = "e".repeat(LONG_LENGTH);
        driver(vendor, renderer, version);
        GlContextInfo info = GlContextInfo.capture();
        assertSame(vendor, info.vendor());
        assertSame(renderer, info.renderer());
        assertSame(version, info.version());
        assertEquals(new GlContextInfo(copy(vendor), copy(renderer), copy(version)), info);
    }

    // Verifies capture queries each string once in vendor then renderer then version order
    @Test
    void captureQueriesEachStringOnceInOrder() {
        LWJGLService gl = TestGl.gl();
        driver(VENDOR, RENDERER, VERSION);
        GlContextInfo.capture();
        InOrder order = Mockito.inOrder(gl);
        order.verify(gl).glGetString(GL_VENDOR);
        order.verify(gl).glGetString(GL_RENDERER);
        order.verify(gl).glGetString(GL_VERSION);
        Mockito.verifyNoMoreInteractions(gl);
    }

    // Verifies capture queries the driver on every call and returns a separate snapshot
    @Test
    void captureQueriesTheDriverOnEveryCall() {
        LWJGLService gl = TestGl.gl();
        driver(VENDOR, RENDERER, VERSION);
        GlContextInfo first = GlContextInfo.capture();
        GlContextInfo second = GlContextInfo.capture();
        assertEquals(first, second);
        assertNotSame(first, second);
        driver("Intel", "Mesa Intel Graphics", "4.6 Mesa 25.2.0");
        GlContextInfo third = GlContextInfo.capture();
        assertEquals(new GlContextInfo("Intel", "Mesa Intel Graphics", "4.6 Mesa 25.2.0"), third);
        assertEquals(new GlContextInfo(VENDOR, RENDERER, VERSION), first);
        Mockito.verify(gl, Mockito.times(3)).glGetString(GL_VENDOR);
        Mockito.verify(gl, Mockito.times(3)).glGetString(GL_RENDERER);
        Mockito.verify(gl, Mockito.times(3)).glGetString(GL_VERSION);
    }

    // Verifies a thousand captures against an unchanged driver return equal snapshots
    @Test
    void captureRepeatedCallsReturnEqualSnapshots() {
        driver(VENDOR, RENDERER, VERSION);
        GlContextInfo expected = new GlContextInfo(VENDOR, RENDERER, VERSION);
        for (int i = 0; i < 1000; i++) {
            assertEquals(expected, GlContextInfo.capture());
        }
        Mockito.verify(TestGl.gl(), Mockito.times(3000)).glGetString(Mockito.anyInt());
    }

    // Verifies capture rethrows a failure of any query and skips the queries after it
    @Test
    void capturePropagatesDriverFailureAtEveryQuery() {
        LWJGLService gl = TestGl.gl();
        int[] queries = {GL_VENDOR, GL_RENDERER, GL_VERSION};
        for (int failing = 0; failing < queries.length; failing++) {
            TestGl.reset();
            driver(VENDOR, RENDERER, VERSION);
            RuntimeException failure = new IllegalStateException("query " + failing);
            Mockito.doThrow(failure).when(gl).glGetString(queries[failing]);
            assertSame(failure, assertThrows(IllegalStateException.class, GlContextInfo::capture));
            for (int i = 0; i < queries.length; i++) {
                Mockito.verify(gl, Mockito.times(i <= failing ? 1 : 0)).glGetString(queries[i]);
            }
        }
    }

    // Verifies capture rethrows a native link error from the driver
    @Test
    void capturePropagatesNativeLinkError() {
        UnsatisfiedLinkError failure = new UnsatisfiedLinkError("glGetString");
        Mockito.doThrow(failure).when(TestGl.gl()).glGetString(GL_VENDOR);
        assertSame(failure, assertThrows(UnsatisfiedLinkError.class, GlContextInfo::capture));
    }

    // Verifies the constructor stores each component by identity
    @Test
    void constructorStoresComponents() {
        String vendor = copy(VENDOR);
        String renderer = copy(RENDERER);
        String version = copy(VERSION);
        GlContextInfo info = new GlContextInfo(vendor, renderer, version);
        assertSame(vendor, info.vendor());
        assertSame(renderer, info.renderer());
        assertSame(version, info.version());
    }

    // Verifies the constructor keeps null components without replacing them
    @Test
    void constructorKeepsNullComponents() {
        GlContextInfo none = new GlContextInfo(null, null, null);
        assertNull(none.vendor());
        assertNull(none.renderer());
        assertNull(none.version());
        GlContextInfo mixed = new GlContextInfo(null, RENDERER, null);
        assertNull(mixed.vendor());
        assertSame(RENDERER, mixed.renderer());
        assertNull(mixed.version());
    }

    // Verifies equals compares every component by value and in position
    @Test
    void equalsComparesEveryComponent() {
        GlContextInfo info = new GlContextInfo(VENDOR, RENDERER, VERSION);
        GlContextInfo same = new GlContextInfo(copy(VENDOR), copy(RENDERER), copy(VERSION));
        GlContextInfo third = new GlContextInfo(copy(VENDOR), copy(RENDERER), copy(VERSION));
        assertEquals(info, info);
        assertEquals(info, same);
        assertEquals(same, info);
        assertEquals(same, third);
        assertEquals(info, third);
        assertNotEquals(info, new GlContextInfo("AMD", RENDERER, VERSION));
        assertNotEquals(info, new GlContextInfo(VENDOR, "AMD", VERSION));
        assertNotEquals(info, new GlContextInfo(VENDOR, RENDERER, "AMD"));
        assertNotEquals(info, new GlContextInfo(RENDERER, VENDOR, VERSION));
        assertNotEquals(info, new GlContextInfo(VENDOR.toUpperCase(Locale.ROOT), RENDERER, VERSION));
        assertNotEquals(info, new GlContextInfo(VENDOR + " ", RENDERER, VERSION));
        assertFalse(info.equals(null));
        assertFalse(info.equals(VENDOR));
        assertFalse(info.equals(info.toString()));
    }

    // Verifies equals matches null with null and tells null and empty components apart
    @Test
    void equalsHandlesNullComponents() {
        GlContextInfo none = new GlContextInfo(null, null, null);
        GlContextInfo empty = new GlContextInfo("", "", "");
        assertEquals(none, none);
        assertEquals(none, new GlContextInfo(null, null, null));
        assertNotEquals(none, empty);
        assertNotEquals(empty, none);
        assertNotEquals(empty, new GlContextInfo(null, "", ""));
        assertNotEquals(empty, new GlContextInfo("", null, ""));
        assertNotEquals(empty, new GlContextInfo("", "", null));
        assertNotEquals(new GlContextInfo(null, "", ""), empty);
        assertFalse(none.equals(null));
    }

    // Verifies hashCode agrees with equals and stays stable across calls
    @Test
    void hashCodeMatchesEquals() {
        GlContextInfo info = new GlContextInfo(VENDOR, RENDERER, VERSION);
        assertEquals(info.hashCode(), new GlContextInfo(copy(VENDOR), copy(RENDERER), copy(VERSION)).hashCode());
        int first = info.hashCode();
        for (int i = 0; i < 1000; i++) {
            assertEquals(first, info.hashCode());
        }
        GlContextInfo none = new GlContextInfo(null, null, null);
        assertEquals(none.hashCode(), new GlContextInfo(null, null, null).hashCode());
        assertEquals(new GlContextInfo("", "", "").hashCode(), GlContextInfo.capture().hashCode());
        String huge = "x".repeat(LONG_LENGTH);
        assertEquals(new GlContextInfo(huge, huge, huge).hashCode(), new GlContextInfo(copy(huge), copy(huge), copy(huge)).hashCode());
    }

    // Verifies toString names every component in declaration order
    @Test
    void toStringNamesEveryComponent() {
        assertEquals("GlContextInfo[vendor=" + VENDOR + ", renderer=" + RENDERER + ", version=" + VERSION + "]",
                new GlContextInfo(VENDOR, RENDERER, VERSION).toString());
        assertEquals("GlContextInfo[vendor=null, renderer=null, version=null]", new GlContextInfo(null, null, null).toString());
        assertEquals("GlContextInfo[vendor=, renderer=, version=]", new GlContextInfo("", "", "").toString());
    }

    // Verifies the record exposes three string components and a static capture method
    @Test
    void recordShapeIsStable() throws ReflectiveOperationException {
        assertTrue(GlContextInfo.class.isRecord());
        assertTrue(Modifier.isPublic(GlContextInfo.class.getModifiers()));
        assertTrue(Modifier.isFinal(GlContextInfo.class.getModifiers()));
        RecordComponent[] components = GlContextInfo.class.getRecordComponents();
        assertArrayEquals(new Object[]{"vendor", "renderer", "version"}, Arrays.stream(components).map(RecordComponent::getName).toArray());
        for (RecordComponent component : components) {
            assertSame(String.class, component.getType());
        }
        Method capture = GlContextInfo.class.getMethod("capture");
        assertTrue(Modifier.isStatic(capture.getModifiers()));
        assertSame(GlContextInfo.class, capture.getReturnType());
    }

    // Verifies safe returns the shared empty string for null and the same instance otherwise
    @Test
    void safeReturnsValueOrEmpty() {
        assertSame("", Statics.call(GlContextInfo.class, "safe", new Object[]{null}));
        String value = copy(VENDOR);
        assertSame(value, Statics.call(GlContextInfo.class, "safe", value));
        String empty = copy("");
        assertSame(empty, Statics.call(GlContextInfo.class, "safe", empty));
        String huge = "x".repeat(LONG_LENGTH);
        assertSame(huge, Statics.call(GlContextInfo.class, "safe", huge));
    }
}
