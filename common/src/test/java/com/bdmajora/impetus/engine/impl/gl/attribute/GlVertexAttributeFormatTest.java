package com.bdmajora.impetus.engine.impl.gl.attribute;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.IntSupplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

// Tests of the vertex attribute format record and its built in formats
class GlVertexAttributeFormatTest {
    // Message of a component size that is zero or negative
    private static final String SIZE_BAD = "Format size must be positive";
    // Calls of a measured method before allocation is counted
    private static final int WARM_CALLS = 20_000;
    // Calls of a measured method while allocation is counted
    private static final int COUNTED_CALLS = 100_000;
    // Type values at the int limits and around zero and at the float enum
    private static final int[] TYPES = {Integer.MIN_VALUE, Integer.MIN_VALUE + 1, -1, 0, 1, 5126, Integer.MAX_VALUE};
    // Positive sizes at the low end and the int limit and one that is not a power of two
    private static final int[] SIZES = {1, 2, 3, 4, 8, Integer.MAX_VALUE - 1, Integer.MAX_VALUE};
    // Sizes that are zero or negative down to the int limit
    private static final int[] BAD_SIZES = {Integer.MIN_VALUE, Integer.MIN_VALUE + 1, -4, -1, 0};
    // Every built in format in declaration order
    private static final List<GlVertexAttributeFormat> BUILT_INS = List.of(GlVertexAttributeFormat.FLOAT, GlVertexAttributeFormat.SHORT, GlVertexAttributeFormat.UNSIGNED_SHORT, GlVertexAttributeFormat.BYTE, GlVertexAttributeFormat.UNSIGNED_BYTE, GlVertexAttributeFormat.UNSIGNED_INT);

    // Sum of measured results that keeps the measured calls alive
    private static long sink;

    // Record of another type with the same components as the format
    private record Twin(int typeId, int size) {
    }

    // Returns every pairing of a type value with a positive size
    static Stream<Arguments> components() {
        List<Arguments> cases = new ArrayList<>();
        for (int typeId : TYPES) {
            for (int size : SIZES) {
                cases.add(Arguments.of(typeId, size));
            }
        }
        return cases.stream();
    }

    // Returns every pairing of a type value with a size that is zero or negative
    static Stream<Arguments> badComponents() {
        List<Arguments> cases = new ArrayList<>();
        for (int typeId : TYPES) {
            for (int size : BAD_SIZES) {
                cases.add(Arguments.of(typeId, size));
            }
        }
        return cases.stream();
    }

    // Returns the bytes the current thread allocates over the counted calls after the warm calls
    private static long allocatedBy(IntSupplier call) {
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        assumeTrue(bean instanceof com.sun.management.ThreadMXBean);
        com.sun.management.ThreadMXBean threads = (com.sun.management.ThreadMXBean) bean;
        assumeTrue(threads.isThreadAllocatedMemorySupported() && threads.isThreadAllocatedMemoryEnabled());

        long total = 0;
        for (int run = 0; run < WARM_CALLS; run++) {
            total += call.getAsInt();
        }
        long before = threads.getCurrentThreadAllocatedBytes();
        for (int run = 0; run < COUNTED_CALLS; run++) {
            total += call.getAsInt();
        }
        long allocated = threads.getCurrentThreadAllocatedBytes() - before;
        sink += total;
        return allocated;
    }

    // Holds the GL enum and the component width of every built in format
    @ParameterizedTest
    @CsvSource({"FLOAT, 5126, 4", "SHORT, 5122, 2", "UNSIGNED_SHORT, 5123, 2", "BYTE, 5120, 1", "UNSIGNED_BYTE, 5121, 1", "UNSIGNED_INT, 5125, 4"})
    void holdsTheGlEnumAndWidthOfEveryBuiltInFormat(String name, int typeId, int size) throws ReflectiveOperationException {
        GlVertexAttributeFormat format = (GlVertexAttributeFormat) GlVertexAttributeFormat.class.getField(name).get(null);
        assertEquals(typeId, format.typeId());
        assertEquals(size, format.size());
    }

    // Matches the width of the Java primitive of each built in component type
    @Test
    void matchesTheJavaPrimitiveWidths() {
        assertEquals(Float.BYTES, GlVertexAttributeFormat.FLOAT.size());
        assertEquals(Short.BYTES, GlVertexAttributeFormat.SHORT.size());
        assertEquals(Character.BYTES, GlVertexAttributeFormat.UNSIGNED_SHORT.size());
        assertEquals(Byte.BYTES, GlVertexAttributeFormat.BYTE.size());
        assertEquals(Byte.BYTES, GlVertexAttributeFormat.UNSIGNED_BYTE.size());
        assertEquals(Integer.BYTES, GlVertexAttributeFormat.UNSIGNED_INT.size());
    }

    // Declares exactly the six built in formats as public constants
    @Test
    void declaresTheSixBuiltInFormatsAsPublicConstants() throws IllegalAccessException {
        List<String> names = new ArrayList<>();
        for (Field field : GlVertexAttributeFormat.class.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (field.isSynthetic() || !Modifier.isStatic(modifiers)) {
                continue;
            }
            assertTrue(Modifier.isPublic(modifiers) && Modifier.isFinal(modifiers), field.getName());
            assertEquals(GlVertexAttributeFormat.class, field.getType());
            assertTrue(BUILT_INS.contains((GlVertexAttributeFormat) field.get(null)), field.getName());
            names.add(field.getName());
        }
        assertEquals(6, names.size());
        assertEquals(Set.of("FLOAT", "SHORT", "UNSIGNED_SHORT", "BYTE", "UNSIGNED_BYTE", "UNSIGNED_INT"), Set.copyOf(names));
    }

    // Keeps every built in format distinct from every other one in type and equality and text
    @Test
    void keepsTheBuiltInFormatsPairwiseDistinct() {
        for (int left = 0; left < BUILT_INS.size(); left++) {
            for (int right = 0; right < BUILT_INS.size(); right++) {
                if (left == right) {
                    continue;
                }
                assertNotEquals(BUILT_INS.get(left).typeId(), BUILT_INS.get(right).typeId());
                assertNotEquals(BUILT_INS.get(left), BUILT_INS.get(right));
                assertNotEquals(BUILT_INS.get(left).toString(), BUILT_INS.get(right).toString());
            }
        }
    }

    // Equals a fresh copy of every built in format that is a different instance
    @Test
    void equalsAFreshCopyOfEveryBuiltInFormat() {
        for (GlVertexAttributeFormat format : BUILT_INS) {
            GlVertexAttributeFormat copy = new GlVertexAttributeFormat(format.typeId(), format.size());
            assertNotSame(format, copy);
            assertEquals(format, copy);
            assertEquals(copy, format);
            assertEquals(format.hashCode(), copy.hashCode());
            assertEquals(format.toString(), copy.toString());
        }
    }

    // Stores both components at the int limits and around zero
    @ParameterizedTest
    @MethodSource("components")
    void storesBothComponents(int typeId, int size) {
        GlVertexAttributeFormat format = new GlVertexAttributeFormat(typeId, size);
        assertEquals(typeId, format.typeId());
        assertEquals(size, format.size());
    }

    // Rejects a component size that is zero or negative with any type value
    @ParameterizedTest
    @MethodSource("badComponents")
    void rejectsASizeThatIsZeroOrNegative(int typeId, int size) {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> new GlVertexAttributeFormat(typeId, size));
        assertSame(IllegalArgumentException.class, thrown.getClass());
        assertEquals(SIZE_BAD, thrown.getMessage());
    }

    // Rejects a size that is zero or negative through the canonical constructor found by reflection
    @Test
    void rejectsABadSizeThroughReflection() throws NoSuchMethodException {
        Constructor<GlVertexAttributeFormat> constructor = GlVertexAttributeFormat.class.getDeclaredConstructor(int.class, int.class);
        for (int size : BAD_SIZES) {
            Throwable thrown = assertThrows(ReflectiveOperationException.class, () -> constructor.newInstance(5126, size)).getCause();
            assertSame(IllegalArgumentException.class, thrown.getClass());
            assertEquals(SIZE_BAD, thrown.getMessage());
        }
    }

    // Compares equal to itself and to fresh formats with the same components in both directions
    @ParameterizedTest
    @MethodSource("components")
    void equalsIsReflexiveSymmetricAndTransitive(int typeId, int size) {
        GlVertexAttributeFormat first = new GlVertexAttributeFormat(typeId, size);
        GlVertexAttributeFormat second = new GlVertexAttributeFormat(typeId, size);
        GlVertexAttributeFormat third = new GlVertexAttributeFormat(typeId, size);
        assertTrue(first.equals(first));
        assertTrue(first.equals(second) && second.equals(first));
        assertTrue(second.equals(third) && first.equals(third));
        assertEquals(first.hashCode(), second.hashCode());
        assertEquals(second.hashCode(), third.hashCode());
    }

    // Tells apart formats that differ in one component or in both or in component order
    @ParameterizedTest
    @CsvSource({
            "5126, 4, 5125, 4",
            "5126, 4, 5126, 2",
            "5126, 4, 5125, 2",
            "5126, 4, 4, 5126",
            "5126, 4, 70662, 4",
            "0, 1, -2147483648, 1",
            "-1, 1, 1, 1",
            "-2147483648, 2147483647, 2147483647, 2147483647",
            "-2147483648, 1, -2147483648, 2147483647",
            "2147483647, 2147483646, 2147483647, 2147483647"})
    void tellsApartFormatsWithDifferentComponents(int leftType, int leftSize, int rightType, int rightSize) {
        GlVertexAttributeFormat left = new GlVertexAttributeFormat(leftType, leftSize);
        GlVertexAttributeFormat right = new GlVertexAttributeFormat(rightType, rightSize);
        assertFalse(left.equals(right));
        assertFalse(right.equals(left));
        assertNotEquals(left.toString(), right.toString());
    }

    // Compares unequal to null and to objects of other types that carry the same values
    @Test
    void comparesUnequalToNullAndOtherTypes() {
        GlVertexAttributeFormat format = GlVertexAttributeFormat.FLOAT;
        assertFalse(format.equals(null));
        assertFalse(format.equals(new Twin(5126, 4)));
        assertFalse(format.equals(5126));
        assertFalse(format.equals(new int[]{5126, 4}));
        assertFalse(format.equals(format.toString()));
        assertFalse(new Twin(5126, 4).equals(format));
    }

    // Names the record and both components in its text
    @ParameterizedTest
    @CsvSource({
            "5126, 4, 'GlVertexAttributeFormat[typeId=5126, size=4]'",
            "0, 3, 'GlVertexAttributeFormat[typeId=0, size=3]'",
            "-1, 1, 'GlVertexAttributeFormat[typeId=-1, size=1]'",
            "-2147483648, 2147483647, 'GlVertexAttributeFormat[typeId=-2147483648, size=2147483647]'"})
    void namesTheRecordAndBothComponents(int typeId, int size, String text) {
        assertEquals(text, new GlVertexAttributeFormat(typeId, size).toString());
    }

    // Returns the same components and hash code and text and equality on repeated calls
    @Test
    void answersTheSameOnRepeatedCalls() {
        GlVertexAttributeFormat format = new GlVertexAttributeFormat(Integer.MIN_VALUE, Integer.MAX_VALUE);
        GlVertexAttributeFormat same = new GlVertexAttributeFormat(Integer.MIN_VALUE, Integer.MAX_VALUE);
        int hash = format.hashCode();
        String text = format.toString();
        for (int call = 0; call < 1_000; call++) {
            assertEquals(Integer.MIN_VALUE, format.typeId());
            assertEquals(Integer.MAX_VALUE, format.size());
            assertEquals(hash, format.hashCode());
            assertEquals(text, format.toString());
            assertTrue(format.equals(same));
            assertFalse(format.equals(GlVertexAttributeFormat.BYTE));
        }
    }

    // Declares a public final record of an int type and an int size in that order
    @Test
    void isAPublicFinalRecordOfTwoIntComponents() throws NoSuchMethodException {
        Class<GlVertexAttributeFormat> type = GlVertexAttributeFormat.class;
        assertTrue(type.isRecord());
        assertTrue(Modifier.isPublic(type.getModifiers()));
        assertTrue(Modifier.isFinal(type.getModifiers()));

        RecordComponent[] components = type.getRecordComponents();
        assertEquals(2, components.length);
        assertEquals("typeId", components[0].getName());
        assertSame(int.class, components[0].getType());
        assertEquals("size", components[1].getName());
        assertSame(int.class, components[1].getType());

        assertEquals(1, type.getDeclaredConstructors().length);
        assertTrue(Modifier.isPublic(type.getDeclaredConstructor(int.class, int.class).getModifiers()));
        assertTrue(GlVertexAttributeFormat.FLOAT instanceof GlVertexAttributeFormat(int typeId, int size) && typeId == 5126 && size == 4);
    }

    // Allocates no heap memory in warm calls of equals and hashCode and the accessors
    @Test
    void warmCallsAllocateNothing() {
        GlVertexAttributeFormat format = new GlVertexAttributeFormat(5126, 4);
        GlVertexAttributeFormat same = new GlVertexAttributeFormat(5126, 4);
        GlVertexAttributeFormat other = GlVertexAttributeFormat.UNSIGNED_INT;

        long equalBytes = allocatedBy(() -> format.equals(same) ? 1 : 0);
        long unequalBytes = allocatedBy(() -> format.equals(other) ? 1 : 0);
        long hashBytes = allocatedBy(format::hashCode);
        long accessorBytes = allocatedBy(() -> format.typeId() ^ format.size());

        assertTrue(equalBytes < COUNTED_CALLS, equalBytes + " bytes allocated by equals of equal formats");
        assertTrue(unequalBytes < COUNTED_CALLS, unequalBytes + " bytes allocated by equals of different formats");
        assertTrue(hashBytes < COUNTED_CALLS, hashBytes + " bytes allocated by hashCode");
        assertTrue(accessorBytes < COUNTED_CALLS, accessorBytes + " bytes allocated by the accessors");
        assertNotEquals(0, sink);
    }
}
