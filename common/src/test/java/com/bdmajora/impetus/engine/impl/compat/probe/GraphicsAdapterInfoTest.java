package com.bdmajora.impetus.engine.impl.compat.probe;

import com.bdmajora.impetus.engine.impl.compat.environment.GlContextInfo;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Tests the display adapter record GraphicsAdapterInfo
class GraphicsAdapterInfoTest {
    // Name of a typical Windows NVIDIA adapter
    private static final String NAME = "NVIDIA GeForce RTX 5070";
    // Driver version of a typical Windows NVIDIA adapter
    private static final String DRIVER = "32.0.15.8142";
    // Name of a typical Linux adapter built from the PCI vendor and device ids
    private static final String PCI_NAME = "PCI 10de:2f04";
    // Driver module version of a typical Linux NVIDIA adapter
    private static final String LINUX_DRIVER = "615.71.09";
    // Length of the long adapter strings in characters
    private static final int LONG_LENGTH = 1 << 20;
    // String whose hash code is the smallest int
    private static final String MIN_HASH = "polygenelubricants";
    // String whose hash code is the largest int
    private static final String MAX_HASH = "4nafjwk";
    // Nonempty string whose hash code is zero
    private static final String ZERO_HASH = "f5a5a608";

    // Returns an equal string that is a separate instance
    private static String copy(String value) {
        return new String(value);
    }

    // Returns an adapter with the typical Windows NVIDIA components
    private static GraphicsAdapterInfo nvidia() {
        return new GraphicsAdapterInfo(GraphicsVendor.NVIDIA, NAME, DRIVER);
    }

    // Returns an adapter with the typical Windows NVIDIA components held in separate string instances
    private static GraphicsAdapterInfo nvidiaCopy() {
        return new GraphicsAdapterInfo(GraphicsVendor.NVIDIA, copy(NAME), copy(DRIVER));
    }

    // Verifies the constructor stores each component by identity
    @Test
    void constructorStoresComponents() {
        String name = copy(NAME);
        String driver = copy(DRIVER);
        GraphicsAdapterInfo info = new GraphicsAdapterInfo(GraphicsVendor.NVIDIA, name, driver);
        assertSame(GraphicsVendor.NVIDIA, info.vendor());
        assertSame(name, info.name());
        assertSame(driver, info.driverVersion());
        GraphicsAdapterInfo linux = new GraphicsAdapterInfo(GraphicsVendor.NVIDIA, PCI_NAME, LINUX_DRIVER);
        assertSame(PCI_NAME, linux.name());
        assertSame(LINUX_DRIVER, linux.driverVersion());
    }

    // Verifies the constructor accepts every vendor
    @Test
    void constructorAcceptsEveryVendor() {
        for (GraphicsVendor vendor : GraphicsVendor.values()) {
            GraphicsAdapterInfo info = new GraphicsAdapterInfo(vendor, NAME, DRIVER);
            assertSame(vendor, info.vendor());
            assertSame(NAME, info.name());
            assertSame(DRIVER, info.driverVersion());
        }
    }

    // Verifies the constructor keeps each null component and the components beside it
    @Test
    void constructorKeepsNullComponents() {
        // Each bit of the mask marks one component as null
        for (int mask = 0; mask < 8; mask++) {
            GraphicsVendor vendor = (mask & 1) == 0 ? GraphicsVendor.AMD : null;
            String name = (mask & 2) == 0 ? NAME : null;
            String driver = (mask & 4) == 0 ? DRIVER : null;
            GraphicsAdapterInfo info = new GraphicsAdapterInfo(vendor, name, driver);
            assertSame(vendor, info.vendor());
            assertSame(name, info.name());
            assertSame(driver, info.driverVersion());
        }
    }

    // Verifies the constructor keeps empty and blank strings without trimming them
    @Test
    void constructorKeepsEmptyAndBlankStrings() {
        String empty = copy("");
        String blank = " \t\r\n ";
        GraphicsAdapterInfo info = new GraphicsAdapterInfo(GraphicsVendor.OTHER, empty, blank);
        assertSame(empty, info.name());
        assertSame(blank, info.driverVersion());
        assertEquals(0, info.name().length());
        assertEquals(5, info.driverVersion().length());
    }

    // Verifies the constructor keeps strings of a single character
    @Test
    void constructorKeepsSingleCharacterStrings() {
        GraphicsAdapterInfo info = new GraphicsAdapterInfo(GraphicsVendor.INTEL, "n", "1");
        assertEquals("n", info.name());
        assertEquals("1", info.driverVersion());
        assertEquals(new GraphicsAdapterInfo(GraphicsVendor.INTEL, copy("n"), copy("1")), info);
    }

    // Verifies the constructor keeps control characters and surrogate characters unchanged
    @Test
    void constructorKeepsUnusualCharacters() {
        String nul = (char) 0 + "GPU" + (char) 0;
        String lone = (char) 0xD800 + "31.0" + (char) 0xDFFF;
        GraphicsAdapterInfo info = new GraphicsAdapterInfo(GraphicsVendor.OTHER, nul, lone);
        assertSame(nul, info.name());
        assertSame(lone, info.driverVersion());
        assertEquals(5, info.name().length());
        assertEquals(6, info.driverVersion().length());
        String astral = new String(Character.toChars(0x1F600)) + (char) 0xFFFF;
        GraphicsAdapterInfo other = new GraphicsAdapterInfo(null, astral, astral);
        assertSame(astral, other.name());
        assertSame(astral, other.driverVersion());
        assertEquals(3, other.name().length());
    }

    // Verifies the constructor keeps strings longer than one million characters by identity
    @Test
    void constructorKeepsLongStrings() {
        String name = "n".repeat(LONG_LENGTH);
        String driver = "9".repeat(LONG_LENGTH);
        GraphicsAdapterInfo info = new GraphicsAdapterInfo(GraphicsVendor.NVIDIA, name, driver);
        assertSame(name, info.name());
        assertSame(driver, info.driverVersion());
        assertEquals(new GraphicsAdapterInfo(GraphicsVendor.NVIDIA, copy(name), copy(driver)), info);
    }

    // Verifies repeated accessor calls return the same instances
    @Test
    void accessorsReturnSameInstancesOnRepeatedCalls() {
        GraphicsAdapterInfo info = nvidia();
        for (int i = 0; i < 1000; i++) {
            assertSame(GraphicsVendor.NVIDIA, info.vendor());
            assertSame(NAME, info.name());
            assertSame(DRIVER, info.driverVersion());
        }
    }

    // Verifies equals compares every component by value and in position
    @Test
    void equalsComparesEveryComponent() {
        GraphicsAdapterInfo info = nvidia();
        GraphicsAdapterInfo same = nvidiaCopy();
        GraphicsAdapterInfo third = nvidiaCopy();
        assertEquals(info, info);
        assertEquals(info, same);
        assertEquals(same, info);
        assertEquals(same, third);
        assertEquals(info, third);
        assertNotEquals(info, new GraphicsAdapterInfo(GraphicsVendor.AMD, NAME, DRIVER));
        assertNotEquals(info, new GraphicsAdapterInfo(GraphicsVendor.NVIDIA, "Radeon", DRIVER));
        assertNotEquals(info, new GraphicsAdapterInfo(GraphicsVendor.NVIDIA, NAME, "1.0"));
        assertNotEquals(info, new GraphicsAdapterInfo(GraphicsVendor.NVIDIA, DRIVER, NAME));
        assertNotEquals(info, new GraphicsAdapterInfo(GraphicsVendor.NVIDIA, NAME.toUpperCase(Locale.ROOT), DRIVER));
        assertNotEquals(info, new GraphicsAdapterInfo(GraphicsVendor.NVIDIA, NAME + " ", DRIVER));
        assertNotEquals(info, new GraphicsAdapterInfo(GraphicsVendor.NVIDIA, NAME.substring(0, NAME.length() - 1) + "1", DRIVER));
        assertNotEquals(info, new GraphicsAdapterInfo(GraphicsVendor.NVIDIA, NAME, DRIVER.substring(1)));
        assertFalse(info.equals(null));
        assertFalse(info.equals(NAME));
        assertFalse(info.equals(GraphicsVendor.NVIDIA));
        assertFalse(info.equals(info.toString()));
    }

    // Verifies equals tells every pair of vendors apart and tells each vendor from a null vendor
    @Test
    void equalsComparesEveryVendorPair() {
        for (GraphicsVendor left : GraphicsVendor.values()) {
            for (GraphicsVendor right : GraphicsVendor.values()) {
                assertEquals(left == right, new GraphicsAdapterInfo(left, NAME, DRIVER).equals(new GraphicsAdapterInfo(right, NAME, DRIVER)));
            }
            assertNotEquals(new GraphicsAdapterInfo(left, NAME, DRIVER), new GraphicsAdapterInfo(null, NAME, DRIVER));
            assertNotEquals(new GraphicsAdapterInfo(null, NAME, DRIVER), new GraphicsAdapterInfo(left, NAME, DRIVER));
        }
    }

    // Verifies equals matches null with null and tells null and empty components apart
    @Test
    void equalsHandlesNullComponents() {
        GraphicsAdapterInfo none = new GraphicsAdapterInfo(null, null, null);
        GraphicsAdapterInfo empty = new GraphicsAdapterInfo(GraphicsVendor.OTHER, "", "");
        assertEquals(none, none);
        assertEquals(none, new GraphicsAdapterInfo(null, null, null));
        assertNotEquals(none, empty);
        assertNotEquals(empty, none);
        assertNotEquals(empty, new GraphicsAdapterInfo(null, "", ""));
        assertNotEquals(empty, new GraphicsAdapterInfo(GraphicsVendor.OTHER, null, ""));
        assertNotEquals(empty, new GraphicsAdapterInfo(GraphicsVendor.OTHER, "", null));
        assertNotEquals(new GraphicsAdapterInfo(GraphicsVendor.OTHER, null, ""), empty);
        assertNotEquals(new GraphicsAdapterInfo(GraphicsVendor.OTHER, "", null), empty);
        assertFalse(none.equals(null));
    }

    // Verifies equals rejects another record type that holds the same strings
    @Test
    void equalsRejectsOtherRecordTypes() {
        GraphicsAdapterInfo info = new GraphicsAdapterInfo(null, NAME, DRIVER);
        GlContextInfo context = new GlContextInfo(null, NAME, DRIVER);
        assertFalse(info.equals(context));
        assertFalse(context.equals(info));
    }

    // Verifies equals compares long strings up to the last character
    @Test
    void equalsComparesLongStringsToTheEnd() {
        String name = "n".repeat(LONG_LENGTH);
        String last = "n".repeat(LONG_LENGTH - 1) + "m";
        GraphicsAdapterInfo info = new GraphicsAdapterInfo(GraphicsVendor.NVIDIA, name, name);
        assertEquals(info, new GraphicsAdapterInfo(GraphicsVendor.NVIDIA, copy(name), copy(name)));
        assertNotEquals(info, new GraphicsAdapterInfo(GraphicsVendor.NVIDIA, last, name));
        assertNotEquals(info, new GraphicsAdapterInfo(GraphicsVendor.NVIDIA, name, last));
        assertNotEquals(info, new GraphicsAdapterInfo(GraphicsVendor.NVIDIA, name.substring(1), name));
        assertNotEquals(info, new GraphicsAdapterInfo(GraphicsVendor.NVIDIA, name, name + "n"));
    }

    // Verifies hashCode agrees with equals and stays stable across calls
    @Test
    void hashCodeMatchesEquals() {
        GraphicsAdapterInfo info = nvidia();
        assertEquals(info.hashCode(), nvidiaCopy().hashCode());
        int first = info.hashCode();
        for (int i = 0; i < 1000; i++) {
            assertEquals(first, info.hashCode());
        }
        GraphicsAdapterInfo none = new GraphicsAdapterInfo(null, null, null);
        assertEquals(none.hashCode(), new GraphicsAdapterInfo(null, null, null).hashCode());
        for (GraphicsVendor vendor : GraphicsVendor.values()) {
            assertEquals(new GraphicsAdapterInfo(vendor, "", "").hashCode(), new GraphicsAdapterInfo(vendor, copy(""), copy("")).hashCode());
        }
        String huge = "x".repeat(LONG_LENGTH);
        assertEquals(new GraphicsAdapterInfo(GraphicsVendor.INTEL, huge, huge).hashCode(),
                new GraphicsAdapterInfo(GraphicsVendor.INTEL, copy(huge), copy(huge)).hashCode());
    }

    // Verifies hashCode wraps without failing when component hashes sit at the int limits and at zero
    @Test
    void hashCodeWrapsAtIntLimits() {
        assertEquals(Integer.MIN_VALUE, MIN_HASH.hashCode());
        assertEquals(Integer.MAX_VALUE, MAX_HASH.hashCode());
        assertEquals(0, ZERO_HASH.hashCode());
        String[] extremes = {MIN_HASH, MAX_HASH, ZERO_HASH, ""};
        GraphicsVendor[] vendors = {null, GraphicsVendor.NVIDIA, GraphicsVendor.OTHER};
        for (GraphicsVendor vendor : vendors) {
            for (String name : extremes) {
                for (String driver : extremes) {
                    GraphicsAdapterInfo info = new GraphicsAdapterInfo(vendor, name, driver);
                    GraphicsAdapterInfo same = new GraphicsAdapterInfo(vendor, copy(name), copy(driver));
                    assertEquals(info, same);
                    assertEquals(info.hashCode(), same.hashCode());
                    assertEquals(info.hashCode(), info.hashCode());
                }
            }
        }
    }

    // Verifies hash sets and hash maps treat equal adapters as one key
    @Test
    void hashCollectionsCollapseEqualAdapters() {
        Set<GraphicsAdapterInfo> set = new HashSet<>();
        assertTrue(set.add(nvidia()));
        assertFalse(set.add(nvidiaCopy()));
        assertTrue(set.add(new GraphicsAdapterInfo(GraphicsVendor.AMD, NAME, DRIVER)));
        assertTrue(set.add(new GraphicsAdapterInfo(null, null, null)));
        assertFalse(set.add(new GraphicsAdapterInfo(null, null, null)));
        assertEquals(3, set.size());
        Map<GraphicsAdapterInfo, String> map = new HashMap<>();
        map.put(nvidia(), "first");
        map.put(nvidiaCopy(), "second");
        assertEquals(1, map.size());
        assertEquals("second", map.get(nvidia()));
        assertTrue(List.of(nvidia()).contains(nvidiaCopy()));
    }

    // Verifies toString names every component in declaration order
    @Test
    void toStringNamesEveryComponent() {
        assertEquals("GraphicsAdapterInfo[vendor=NVIDIA, name=" + NAME + ", driverVersion=" + DRIVER + "]", nvidia().toString());
        assertEquals("GraphicsAdapterInfo[vendor=null, name=null, driverVersion=null]", new GraphicsAdapterInfo(null, null, null).toString());
        assertEquals("GraphicsAdapterInfo[vendor=OTHER, name=, driverVersion=]", new GraphicsAdapterInfo(GraphicsVendor.OTHER, "", "").toString());
        for (GraphicsVendor vendor : GraphicsVendor.values()) {
            assertEquals("GraphicsAdapterInfo[vendor=" + vendor.name() + ", name=n, driverVersion=d]", new GraphicsAdapterInfo(vendor, "n", "d").toString());
        }
    }

    // Verifies toString keeps long and unusual components whole and repeats the same text
    @Test
    void toStringKeepsLongAndUnusualComponents() {
        String huge = "x".repeat(LONG_LENGTH);
        String text = new GraphicsAdapterInfo(GraphicsVendor.MESA, huge, huge).toString();
        assertEquals("GraphicsAdapterInfo[vendor=MESA, name=, driverVersion=]".length() + 2 * LONG_LENGTH, text.length());
        assertTrue(text.startsWith("GraphicsAdapterInfo[vendor=MESA, name=xxx"));
        assertTrue(text.endsWith("xxx]"));
        String nul = (char) 0 + "GPU";
        assertEquals("GraphicsAdapterInfo[vendor=INTEL, name=" + nul + ", driverVersion=]", new GraphicsAdapterInfo(GraphicsVendor.INTEL, nul, "").toString());
        GraphicsAdapterInfo info = nvidia();
        assertEquals(info.toString(), info.toString());
        assertEquals(info.toString(), nvidiaCopy().toString());
    }

    // Verifies the record exposes three final components and one public canonical constructor
    @Test
    void recordShapeIsStable() {
        Class<GraphicsAdapterInfo> type = GraphicsAdapterInfo.class;
        assertTrue(type.isRecord());
        assertTrue(Modifier.isPublic(type.getModifiers()));
        assertTrue(Modifier.isFinal(type.getModifiers()));
        RecordComponent[] components = type.getRecordComponents();
        assertArrayEquals(new Object[]{"vendor", "name", "driverVersion"}, Arrays.stream(components).map(RecordComponent::getName).toArray());
        assertArrayEquals(new Object[]{GraphicsVendor.class, String.class, String.class}, Arrays.stream(components).map(RecordComponent::getType).toArray());
        Constructor<?>[] constructors = type.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        assertTrue(Modifier.isPublic(constructors[0].getModifiers()));
        assertArrayEquals(new Class<?>[]{GraphicsVendor.class, String.class, String.class}, constructors[0].getParameterTypes());
        Field[] fields = Arrays.stream(type.getDeclaredFields()).filter(field -> !Modifier.isStatic(field.getModifiers())).toArray(Field[]::new);
        assertEquals(3, fields.length);
        for (Field field : fields) {
            assertTrue(Modifier.isPrivate(field.getModifiers()));
            assertTrue(Modifier.isFinal(field.getModifiers()));
        }
        // Counts the static methods other than the synthetic ones that coverage agents add
        assertEquals(0, Arrays.stream(type.getDeclaredMethods()).filter(method -> Modifier.isStatic(method.getModifiers()) && !method.isSynthetic()).count());
    }
}
