package com.bdmajora.impetus.engine.impl.gl.attribute;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Tests of every constructor and accessor of the vertex attribute
class GlVertexAttributeTest {
    // Message of a null format
    private static final String FORMAT_NULL = "Format must not be null";
    // Message of a null name
    private static final String NAME_NULL = "Name must not be null";
    // Message of a count outside the range between 1 and 4
    private static final String COUNT_BAD = "Count must be between 1 and 4";
    // Message of a negative pointer
    private static final String POINTER_NEGATIVE = "Pointer must not be negative";
    // Message of a negative stride
    private static final String STRIDE_NEGATIVE = "Stride must not be negative";
    // Message of a size that is zero or negative
    private static final String SIZE_BAD = "Size must be positive";
    // Message of a size above the int range
    private static final String SIZE_LIMIT = "Size must not exceed 2147483647";
    // Message of an attribute that ends past the int range
    private static final String END_LIMIT = "Attribute end must not exceed 2147483647";
    // Start of the message of a pointer that is not a multiple of the component size
    private static final String POINTER_MISALIGNED = "Pointer must be a multiple of ";
    // Start of the message of a stride that is not a multiple of the component size
    private static final String STRIDE_MISALIGNED = "Stride must be a multiple of ";

    // Every built in format
    private static final List<GlVertexAttributeFormat> FORMATS = List.of(GlVertexAttributeFormat.FLOAT, GlVertexAttributeFormat.SHORT, GlVertexAttributeFormat.UNSIGNED_SHORT, GlVertexAttributeFormat.BYTE, GlVertexAttributeFormat.UNSIGNED_BYTE, GlVertexAttributeFormat.UNSIGNED_INT);

    // Creates an attribute through the public constructor with a fixed name and both flags clear
    private static GlVertexAttribute attribute(GlVertexAttributeFormat format, int count, int pointer, int stride) {
        return new GlVertexAttribute(format, "a", count, false, pointer, stride, false);
    }

    // Creates an attribute through the protected constructor with a fixed name and both flags clear
    private static GlVertexAttribute sized(GlVertexAttributeFormat format, int size, int count, int pointer, int stride) {
        return new GlVertexAttribute(format, size, count, "a", false, pointer, stride, false);
    }

    // Creates a format of the byte type with the given component size
    private static GlVertexAttributeFormat unit(int size) {
        return new GlVertexAttributeFormat(GlVertexAttributeFormat.BYTE.typeId(), size);
    }

    // Returns the built in format with the given constant name
    private static GlVertexAttributeFormat builtIn(String name) {
        return switch (name) {
            case "FLOAT" -> GlVertexAttributeFormat.FLOAT;
            case "SHORT" -> GlVertexAttributeFormat.SHORT;
            case "UNSIGNED_SHORT" -> GlVertexAttributeFormat.UNSIGNED_SHORT;
            case "BYTE" -> GlVertexAttributeFormat.BYTE;
            case "UNSIGNED_BYTE" -> GlVertexAttributeFormat.UNSIGNED_BYTE;
            case "UNSIGNED_INT" -> GlVertexAttributeFormat.UNSIGNED_INT;
            default -> throw new IllegalArgumentException(name);
        };
    }

    // Asserts that the action throws the exception type with the message
    private static void expect(Class<? extends RuntimeException> type, String message, Executable action) {
        assertEquals(message, assertThrows(type, action).getMessage());
    }

    // Returns every built in format with every component count
    static Stream<Arguments> formatsAndCounts() {
        List<Arguments> cases = new ArrayList<>();
        for (GlVertexAttributeFormat format : FORMATS) {
            for (int count = 1; count <= 4; count++) {
                cases.add(Arguments.of(format, count));
            }
        }
        return cases.stream();
    }

    // Stores every argument of the public constructor and derives the size from the format
    @ParameterizedTest
    @MethodSource("formatsAndCounts")
    void storesEveryArgumentOfThePublicConstructor(GlVertexAttributeFormat format, int count) {
        int unit = format.size();
        String name = new String("attribute");
        GlVertexAttribute attribute = new GlVertexAttribute(format, name, count, true, unit * 2, unit * 8, false);
        assertSame(format, attribute.getFormat());
        assertSame(name, attribute.getName());
        assertEquals(count, attribute.getCount());
        assertEquals(unit * count, attribute.getSize());
        assertEquals(unit * 2, attribute.getPointer());
        assertEquals(unit * 8, attribute.getStride());
        assertTrue(attribute.isNormalized());
        assertFalse(attribute.isIntType());
    }

    // Stores every argument of the protected constructor and keeps the size as given
    @ParameterizedTest
    @MethodSource("formatsAndCounts")
    void storesEveryArgumentOfTheProtectedConstructor(GlVertexAttributeFormat format, int count) {
        int unit = format.size();
        String name = new String("attribute");
        GlVertexAttribute attribute = new GlVertexAttribute(format, unit * count + 3, count, name, false, unit, unit * 16, true);
        assertSame(format, attribute.getFormat());
        assertSame(name, attribute.getName());
        assertEquals(count, attribute.getCount());
        assertEquals(unit * count + 3, attribute.getSize());
        assertEquals(unit, attribute.getPointer());
        assertEquals(unit * 16, attribute.getStride());
        assertFalse(attribute.isNormalized());
        assertTrue(attribute.isIntType());
    }

    // Stores both flags in every combination through both constructors
    @ParameterizedTest
    @CsvSource({"false, false", "false, true", "true, false", "true, true"})
    void storesBothFlagsInEveryCombination(boolean normalized, boolean intType) {
        GlVertexAttribute derived = new GlVertexAttribute(GlVertexAttributeFormat.UNSIGNED_INT, "a", 1, normalized, 0, 4, intType);
        assertEquals(normalized, derived.isNormalized());
        assertEquals(intType, derived.isIntType());
        GlVertexAttribute given = new GlVertexAttribute(GlVertexAttributeFormat.UNSIGNED_INT, 4, 1, "a", normalized, 0, 4, intType);
        assertEquals(normalized, given.isNormalized());
        assertEquals(intType, given.isIntType());
    }

    // Rejects a null format in both constructors
    @Test
    void rejectsANullFormat() {
        expect(NullPointerException.class, FORMAT_NULL, () -> attribute(null, 1, 0, 0));
        expect(NullPointerException.class, FORMAT_NULL, () -> sized(null, 4, 1, 0, 0));
    }

    // Rejects a null name in both constructors
    @Test
    void rejectsANullName() {
        expect(NullPointerException.class, NAME_NULL, () -> new GlVertexAttribute(GlVertexAttributeFormat.FLOAT, null, 1, false, 0, 0, false));
        expect(NullPointerException.class, NAME_NULL, () -> new GlVertexAttribute(GlVertexAttributeFormat.FLOAT, 4, 1, null, false, 0, 0, false));
    }

    // Accepts an empty name and a single character name and stores each by identity
    @ParameterizedTest
    @ValueSource(strings = {"", "a"})
    void acceptsEmptyAndSingleCharacterNames(String name) {
        assertSame(name, new GlVertexAttribute(GlVertexAttributeFormat.FLOAT, name, 1, false, 0, 0, false).getName());
        assertSame(name, new GlVertexAttribute(GlVertexAttributeFormat.FLOAT, 4, 1, name, false, 0, 0, false).getName());
    }

    // Rejects a count outside the range between 1 and 4 in both constructors
    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -4, -1, 0, 5, 6, 8, Integer.MAX_VALUE})
    void rejectsACountOutsideOneToFour(int count) {
        expect(IllegalArgumentException.class, COUNT_BAD, () -> attribute(GlVertexAttributeFormat.FLOAT, count, 0, 0));
        expect(IllegalArgumentException.class, COUNT_BAD, () -> sized(GlVertexAttributeFormat.FLOAT, 4, count, 0, 0));
    }

    // Accepts the smallest and the largest count in both constructors
    @ParameterizedTest
    @ValueSource(ints = {1, 4})
    void acceptsTheCountBoundaries(int count) {
        assertEquals(count * 4, attribute(GlVertexAttributeFormat.FLOAT, count, 0, 0).getSize());
        assertEquals(count, sized(GlVertexAttributeFormat.FLOAT, 4, count, 0, 0).getCount());
    }

    // Rejects a negative pointer in both constructors
    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -4, -1})
    void rejectsANegativePointer(int pointer) {
        expect(IllegalArgumentException.class, POINTER_NEGATIVE, () -> attribute(GlVertexAttributeFormat.FLOAT, 1, pointer, 0));
        expect(IllegalArgumentException.class, POINTER_NEGATIVE, () -> sized(GlVertexAttributeFormat.FLOAT, 4, 1, pointer, 0));
    }

    // Rejects a negative stride in both constructors
    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -4, -1})
    void rejectsANegativeStride(int stride) {
        expect(IllegalArgumentException.class, STRIDE_NEGATIVE, () -> attribute(GlVertexAttributeFormat.FLOAT, 1, 0, stride));
        expect(IllegalArgumentException.class, STRIDE_NEGATIVE, () -> sized(GlVertexAttributeFormat.FLOAT, 4, 1, 0, stride));
    }

    // Accepts a zero pointer and a zero stride for tightly packed vertices
    @Test
    void acceptsAZeroPointerAndAZeroStride() {
        GlVertexAttribute attribute = attribute(GlVertexAttributeFormat.FLOAT, 3, 0, 0);
        assertEquals(0, attribute.getPointer());
        assertEquals(0, attribute.getStride());
        assertEquals(12, attribute.getSize());
    }

    // Rejects a pointer that is not a multiple of the component size in both constructors
    @ParameterizedTest
    @CsvSource({"FLOAT, 1", "FLOAT, 2", "FLOAT, 3", "FLOAT, 5", "FLOAT, 2147483643", "SHORT, 1", "SHORT, 3", "UNSIGNED_SHORT, 7", "UNSIGNED_INT, 2", "UNSIGNED_INT, 6"})
    void rejectsAMisalignedPointer(String formatName, int pointer) {
        GlVertexAttributeFormat format = builtIn(formatName);
        expect(IllegalArgumentException.class, POINTER_MISALIGNED + format.size(), () -> attribute(format, 1, pointer, 0));
        expect(IllegalArgumentException.class, POINTER_MISALIGNED + format.size(), () -> sized(format, format.size(), 1, pointer, 0));
    }

    // Rejects a stride that is not a multiple of the component size in both constructors
    @ParameterizedTest
    @CsvSource({"FLOAT, 1", "FLOAT, 2", "FLOAT, 6", "FLOAT, 18", "FLOAT, 2147483647", "SHORT, 1", "SHORT, 3", "UNSIGNED_SHORT, 2147483647", "UNSIGNED_INT, 10"})
    void rejectsAMisalignedStride(String formatName, int stride) {
        GlVertexAttributeFormat format = builtIn(formatName);
        expect(IllegalArgumentException.class, STRIDE_MISALIGNED + format.size(), () -> attribute(format, 1, 0, stride));
        expect(IllegalArgumentException.class, STRIDE_MISALIGNED + format.size(), () -> sized(format, format.size(), 1, 0, stride));
    }

    // Accepts pointers and strides that are multiples of the component size up to the int limit
    @ParameterizedTest
    @CsvSource({"FLOAT, 4, 4", "FLOAT, 12, 2147483644", "SHORT, 2, 6", "SHORT, 2147483644, 2147483646", "UNSIGNED_SHORT, 6, 18", "UNSIGNED_INT, 24, 56"})
    void acceptsAlignedPointersAndStrides(String formatName, int pointer, int stride) {
        GlVertexAttributeFormat format = builtIn(formatName);
        GlVertexAttribute attribute = attribute(format, 1, pointer, stride);
        assertEquals(pointer, attribute.getPointer());
        assertEquals(stride, attribute.getStride());
    }

    // Accepts byte attributes at every pointer and stride
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 5, 7, 2147483646})
    void acceptsByteAttributesAtEveryOffset(int offset) {
        for (GlVertexAttributeFormat format : List.of(GlVertexAttributeFormat.BYTE, GlVertexAttributeFormat.UNSIGNED_BYTE)) {
            GlVertexAttribute attribute = attribute(format, 1, offset, offset);
            assertEquals(offset, attribute.getPointer());
            assertEquals(offset, attribute.getStride());
        }
    }

    // Aligns to a component size that is not a power of two
    @Test
    void alignsToAComponentSizeThatIsNotAPowerOfTwo() {
        GlVertexAttributeFormat three = unit(3);
        assertEquals(3, attribute(three, 1, 3, 6).getPointer());
        assertEquals(9, attribute(three, 2, 6, 9).getStride());
        assertEquals(12, attribute(three, 4, 12, 0).getSize());
        expect(IllegalArgumentException.class, POINTER_MISALIGNED + 3, () -> attribute(three, 1, 4, 6));
        expect(IllegalArgumentException.class, STRIDE_MISALIGNED + 3, () -> attribute(three, 1, 3, 4));
    }

    // Rejects a derived size above the int range
    @ParameterizedTest
    @CsvSource({"2147483647, 2", "2147483647, 4", "1073741824, 2", "536870912, 4", "715827883, 3"})
    void rejectsADerivedSizeAboveTheIntRange(int unitSize, int count) {
        expect(IllegalArgumentException.class, SIZE_LIMIT, () -> attribute(unit(unitSize), count, 0, 0));
    }

    // Accepts the largest derived sizes that fit the int range
    @ParameterizedTest
    @CsvSource({"2147483647, 1", "1073741823, 2", "536870911, 4", "715827882, 3"})
    void acceptsTheLargestDerivedSizes(int unitSize, int count) {
        GlVertexAttribute attribute = attribute(unit(unitSize), count, 0, 0);
        assertEquals((long) unitSize * count, attribute.getSize());
        assertEquals(0, attribute.getPointer());
    }

    // Rejects a given size that is zero or negative
    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -4, -1, 0})
    void rejectsAGivenSizeWithoutBytes(int size) {
        expect(IllegalArgumentException.class, SIZE_BAD, () -> sized(GlVertexAttributeFormat.FLOAT, size, 1, 0, 0));
    }

    // Accepts a given size that differs from the derived size up to the int limit
    @ParameterizedTest
    @ValueSource(ints = {1, 3, 16, Integer.MAX_VALUE})
    void acceptsAGivenSizeThatDiffersFromTheDerivedSize(int size) {
        assertEquals(size, sized(GlVertexAttributeFormat.FLOAT, size, 2, 0, 0).getSize());
    }

    // Rejects an attribute that ends past the int range in both constructors
    @ParameterizedTest
    @CsvSource({"BYTE, 1, 2147483647", "BYTE, 2, 2147483646", "UNSIGNED_BYTE, 4, 2147483644", "SHORT, 1, 2147483646", "FLOAT, 1, 2147483644", "FLOAT, 4, 2147483632"})
    void rejectsAnEndPastTheIntRange(String formatName, int count, int pointer) {
        GlVertexAttributeFormat format = builtIn(formatName);
        expect(IllegalArgumentException.class, END_LIMIT, () -> attribute(format, count, pointer, 0));
        expect(IllegalArgumentException.class, END_LIMIT, () -> sized(format, format.size() * count, count, pointer, 0));
    }

    // Accepts an attribute that ends at or before the int limit
    @ParameterizedTest
    @CsvSource({"BYTE, 1, 2147483646", "BYTE, 4, 2147483643", "SHORT, 1, 2147483644", "FLOAT, 1, 2147483640", "FLOAT, 4, 2147483628"})
    void acceptsAnEndAtTheIntLimit(String formatName, int count, int pointer) {
        GlVertexAttribute attribute = attribute(builtIn(formatName), count, pointer, 0);
        assertTrue((long) attribute.getPointer() + attribute.getSize() <= Integer.MAX_VALUE);
        assertEquals(pointer, attribute.getPointer());
    }

    // Rejects a given size that ends past the int range
    @Test
    void rejectsAGivenSizeThatEndsPastTheIntRange() {
        expect(IllegalArgumentException.class, END_LIMIT, () -> sized(GlVertexAttributeFormat.FLOAT, Integer.MAX_VALUE, 1, 4, 0));
        expect(IllegalArgumentException.class, END_LIMIT, () -> sized(GlVertexAttributeFormat.BYTE, 2, 1, Integer.MAX_VALUE - 1, 0));
        assertEquals(Integer.MAX_VALUE, sized(GlVertexAttributeFormat.BYTE, 1, 1, Integer.MAX_VALUE - 1, 0).getPointer() + 1);
    }

    // Reports the first refused argument in declaration order
    @Test
    void reportsTheFirstRefusedArgument() {
        expect(NullPointerException.class, FORMAT_NULL, () -> new GlVertexAttribute(null, null, 0, false, -1, -1, false));
        expect(NullPointerException.class, FORMAT_NULL, () -> new GlVertexAttribute(null, 0, 0, null, false, -1, -1, false));
        expect(NullPointerException.class, NAME_NULL, () -> new GlVertexAttribute(unit(1), null, 0, false, -1, -1, false));
        expect(IllegalArgumentException.class, COUNT_BAD, () -> new GlVertexAttribute(unit(Integer.MAX_VALUE), "a", Integer.MAX_VALUE, false, -1, -1, false));
        expect(IllegalArgumentException.class, POINTER_NEGATIVE, () -> attribute(GlVertexAttributeFormat.FLOAT, 1, -1, -1));
        expect(IllegalArgumentException.class, STRIDE_NEGATIVE, () -> attribute(GlVertexAttributeFormat.FLOAT, 1, 1, -1));
        expect(IllegalArgumentException.class, SIZE_BAD, () -> sized(GlVertexAttributeFormat.FLOAT, 0, 1, 1, 1));
        expect(IllegalArgumentException.class, SIZE_LIMIT, () -> attribute(unit(Integer.MAX_VALUE), 2, 1, 1));
        expect(IllegalArgumentException.class, END_LIMIT, () -> attribute(GlVertexAttributeFormat.FLOAT, 1, Integer.MAX_VALUE - 2, 1));
        expect(IllegalArgumentException.class, POINTER_MISALIGNED + 4, () -> attribute(GlVertexAttributeFormat.FLOAT, 1, 1, 1));
    }

    // Rejects a format element that ends past the int range inside the largest stride
    @Test
    void rejectsAFormatElementThatEndsPastTheIntRange() {
        GlVertexFormat.Builder builder = GlVertexFormat.builder(Integer.MAX_VALUE);
        expect(IllegalArgumentException.class, END_LIMIT, () -> builder.addElement("a", Integer.MAX_VALUE - 1, GlVertexAttributeFormat.BYTE, 2, false, false));
        GlVertexAttribute last = GlVertexFormat.builder(Integer.MAX_VALUE).addElement("a", Integer.MAX_VALUE - 1, GlVertexAttributeFormat.BYTE, 1, false, false).build().getAttribute("a");
        assertEquals(Integer.MAX_VALUE, last.getPointer() + last.getSize());
    }

    // Copies every field into a binding through the protected constructor
    @Test
    void copiesEveryFieldIntoABinding() {
        GlVertexAttribute source = new GlVertexAttribute(GlVertexAttributeFormat.UNSIGNED_SHORT, "uv", 2, true, 6, 18, true);
        GlVertexAttributeBinding binding = new GlVertexAttributeBinding(3, source, 2);
        assertSame(source.getFormat(), binding.getFormat());
        assertSame(source.getName(), binding.getName());
        assertEquals(4, binding.getSize());
        assertEquals(2, binding.getCount());
        assertEquals(6, binding.getPointer());
        assertEquals(18, binding.getStride());
        assertTrue(binding.isNormalized());
        assertTrue(binding.isIntType());
        assertEquals(3, binding.getIndex());
        assertEquals(2, binding.getDivisor());
    }

    // Returns the same values on repeated calls and creates a separate attribute on every construction
    @Test
    void returnsTheSameValuesOnRepeatedCalls() {
        GlVertexAttribute attribute = new GlVertexAttribute(GlVertexAttributeFormat.FLOAT, "pos", 3, false, 4, 16, true);
        for (int call = 0; call < 3; call++) {
            assertSame(GlVertexAttributeFormat.FLOAT, attribute.getFormat());
            assertEquals("pos", attribute.getName());
            assertEquals(3, attribute.getCount());
            assertEquals(12, attribute.getSize());
            assertEquals(4, attribute.getPointer());
            assertEquals(16, attribute.getStride());
            assertFalse(attribute.isNormalized());
            assertTrue(attribute.isIntType());
        }
        GlVertexAttribute twin = new GlVertexAttribute(GlVertexAttributeFormat.FLOAT, "pos", 3, false, 4, 16, true);
        assertNotSame(attribute, twin);
        assertEquals(attribute.getSize(), twin.getSize());
    }

    // Keeps the public class shape with its two constructors and eight accessors over final fields
    @Test
    void keepsThePublicShape() throws ReflectiveOperationException {
        Class<GlVertexAttribute> type = GlVertexAttribute.class;
        assertTrue(Modifier.isPublic(type.getModifiers()));
        assertFalse(Modifier.isFinal(type.getModifiers()));
        Constructor<GlVertexAttribute> derived = type.getDeclaredConstructor(GlVertexAttributeFormat.class, String.class, int.class, boolean.class, int.class, int.class, boolean.class);
        assertEquals(Modifier.PUBLIC, derived.getModifiers());
        Constructor<GlVertexAttribute> given = type.getDeclaredConstructor(GlVertexAttributeFormat.class, int.class, int.class, String.class, boolean.class, int.class, int.class, boolean.class);
        assertEquals(Modifier.PROTECTED, given.getModifiers());
        assertEquals(2, Arrays.stream(type.getDeclaredConstructors()).filter(constructor -> !Modifier.isPrivate(constructor.getModifiers())).count());
        Map<String, Class<?>> methods = new TreeMap<>();
        for (Method method : type.getDeclaredMethods()) {
            if (!Modifier.isPrivate(method.getModifiers()) && !method.isSynthetic()) {
                assertEquals(Modifier.PUBLIC, method.getModifiers());
                assertEquals(0, method.getParameterCount());
                methods.put(method.getName(), method.getReturnType());
            }
        }
        assertEquals(Map.of("getFormat", GlVertexAttributeFormat.class, "getCount", int.class, "getPointer", int.class, "getSize", int.class, "getStride", int.class, "getName", String.class, "isNormalized", boolean.class, "isIntType", boolean.class), methods);
        for (Field field : type.getDeclaredFields()) {
            if (!field.isSynthetic()) {
                assertTrue(Modifier.isPrivate(field.getModifiers()) && Modifier.isFinal(field.getModifiers()), field.getName());
            }
        }
    }
}
