package com.bdmajora.impetus.engine.impl.gl.attribute;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceLinkedOpenHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.IntSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

// Tests of the vertex format and its builder
class GlVertexFormatTest {
    // Message of a builder that holds no attributes
    private static final String NO_ATTRIBUTES = "Attempted to construct vertex format with no attributes";
    // Message of an element that ends past the stride
    private static final String EXTENDS_OUTSIDE = "Element extends outside vertex format";
    // Message of a null map given to the public constructor
    private static final String MAP_NULL = "Attributes must not be null";
    // Message of a null component format
    private static final String FORMAT_NULL = "Format must not be null";
    // Message of a null attribute name
    private static final String NAME_NULL = "Name must not be null";
    // Message of a count that is not between 1 and 4
    private static final String COUNT_BAD = "Count must be between 1 and 4";
    // Message of a negative pointer
    private static final String POINTER_NEGATIVE = "Pointer must not be negative";
    // Message of a negative stride
    private static final String STRIDE_NEGATIVE = "Stride must not be negative";
    // Message of a size past the int range
    private static final String SIZE_LIMIT = "Size must not exceed " + Integer.MAX_VALUE;
    // Message of an attribute end past the int range
    private static final String END_LIMIT = "Attribute end must not exceed " + Integer.MAX_VALUE;
    // Message start of a pointer that is not a multiple of the component size
    private static final String POINTER_MISALIGNED = "Pointer must be a multiple of ";
    // Message start of a stride that is not a multiple of the component size
    private static final String STRIDE_MISALIGNED = "Stride must be a multiple of ";
    // Component format of eight bytes
    private static final GlVertexAttributeFormat DOUBLE = new GlVertexAttributeFormat(0x140A, 8);
    // Number of elements in each large layout
    private static final int MANY = 1 << 16;
    // Time limit of each large layout
    private static final Duration LARGE_LIMIT = Duration.ofSeconds(5);
    // Calls of a measured method before allocation is counted
    private static final int WARM_CALLS = 20_000;
    // Calls of a measured method while allocation is counted
    private static final int COUNTED_CALLS = 100_000;

    // Sum of measured results that keeps the measured calls alive
    private static long sink;

    // Returns the message of an element that starts at or past the stride
    private static String startsOutside(String name, int pointer, int stride) {
        return "Element " + name + " starts outside vertex format (" + pointer + ", stride is " + stride + ")";
    }

    // Returns the message of a name that the builder already holds
    private static String duplicate(String name) {
        return "Generic attribute " + name + " already defined in vertex format";
    }

    // Returns the message of an attribute that overlaps the one before it in pointer order
    private static String overlap(String later, String earlier) {
        return "Attribute " + later + " overlaps with " + earlier;
    }

    // Returns the message of a name that the format does not hold
    private static String missing(String name) {
        return "No attribute exists for " + name;
    }

    // Asserts that the action throws the exception type with the message and returns the exception
    private static <T extends Throwable> T expect(Class<T> type, String message, Executable action) {
        T thrown = assertThrows(type, action);
        assertEquals(message, thrown.getMessage());
        return thrown;
    }

    // Returns the attribute names of the format in iteration order
    private static List<String> names(GlVertexFormat format) {
        List<String> names = new ArrayList<>();
        for (GlVertexAttribute attribute : format.getAttributes()) {
            names.add(attribute.getName());
        }
        return names;
    }

    // Returns the attribute pointers of the format in iteration order
    private static List<Integer> pointers(GlVertexFormat format) {
        List<Integer> pointers = new ArrayList<>();
        for (GlVertexAttribute attribute : format.getAttributes()) {
            pointers.add(attribute.getPointer());
        }
        return pointers;
    }

    // Returns a builder of byte elements read from entries of a name and a pointer and a size
    private static GlVertexFormat.Builder bytes(int stride, String spec) {
        GlVertexFormat.Builder builder = GlVertexFormat.builder(stride);
        for (String entry : spec.split(" ")) {
            int at = entry.indexOf('@');
            int plus = entry.indexOf('+');
            builder.addElement(entry.substring(0, at), Integer.parseInt(entry.substring(at + 1, plus)), GlVertexAttributeFormat.BYTE, Integer.parseInt(entry.substring(plus + 1)), false, false);
        }
        return builder;
    }

    // Adds a probe byte at the next aligned pointer and returns the pointer it received
    private static int probeNextPointer(GlVertexFormat.Builder builder) {
        return builder.addElement("probe", GlVertexFormat.NEXT_ALIGNED_POINTER, GlVertexAttributeFormat.BYTE, 1, false, false).build().getAttribute("probe").getPointer();
    }

    // Returns a format of a float position and a byte color and an integer light value
    private static GlVertexFormat threeAttributes() {
        return GlVertexFormat.builder(20)
                .addElement("pos", 0, GlVertexAttributeFormat.FLOAT, 3, false, false)
                .addElement("color", 12, GlVertexAttributeFormat.UNSIGNED_BYTE, 4, true, false)
                .addElement("light", 16, GlVertexAttributeFormat.UNSIGNED_INT, 1, false, true)
                .build();
    }

    // Returns a map of the given attributes keyed by their names in the given order
    private static Reference2ReferenceLinkedOpenHashMap<String, GlVertexAttribute> keyed(GlVertexAttribute... attributes) {
        Reference2ReferenceLinkedOpenHashMap<String, GlVertexAttribute> map = new Reference2ReferenceLinkedOpenHashMap<>();
        for (GlVertexAttribute attribute : attributes) {
            map.put(attribute.getName(), attribute);
        }
        return map;
    }

    // Returns the public methods that the type declares as name and parameter type text
    private static Set<String> publicMethods(Class<?> type) {
        Set<String> methods = new HashSet<>();
        for (Method method : type.getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers()) && !method.isSynthetic() && !method.isBridge()) {
                methods.add(method.getName() + Arrays.toString(Arrays.stream(method.getParameterTypes()).map(Class::getSimpleName).toArray()));
            }
        }
        return methods;
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

    // Exposes negative 1 as the public pointer that asks for the next aligned offset
    @Test
    void exposesNegativeOneAsTheNextAlignedPointer() throws NoSuchFieldException {
        assertEquals(-1, GlVertexFormat.NEXT_ALIGNED_POINTER);
        Field field = GlVertexFormat.class.getField("NEXT_ALIGNED_POINTER");
        int modifiers = field.getModifiers();
        assertTrue(Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers));
        assertSame(int.class, field.getType());
    }

    // Declares the public constructors and methods of the format and its builder
    @Test
    void declaresThePublicSurfaceOfTheFormatAndBuilder() throws NoSuchMethodException {
        assertEquals(Set.of("builder[int]", "getAttribute[String]", "getAttributes[]", "getStride[]", "toString[]"), publicMethods(GlVertexFormat.class));
        assertEquals(Set.of("addAllElements[GlVertexFormat]", "addElements[Collection]", "addElement[String, int, GlVertexAttributeFormat, int, boolean, boolean]", "build[]"), publicMethods(GlVertexFormat.Builder.class));

        Constructor<?>[] formatConstructors = GlVertexFormat.class.getConstructors();
        assertEquals(1, formatConstructors.length);
        assertEquals(List.of(Reference2ReferenceLinkedOpenHashMap.class, int.class), List.of(formatConstructors[0].getParameterTypes()));
        Constructor<?>[] builderConstructors = GlVertexFormat.Builder.class.getConstructors();
        assertEquals(1, builderConstructors.length);
        assertEquals(List.of(int.class), List.of(builderConstructors[0].getParameterTypes()));

        int formatModifiers = GlVertexFormat.class.getModifiers();
        assertTrue(Modifier.isPublic(formatModifiers) && !Modifier.isFinal(formatModifiers) && !Modifier.isAbstract(formatModifiers));
        int builderModifiers = GlVertexFormat.Builder.class.getModifiers();
        assertTrue(Modifier.isPublic(builderModifiers) && Modifier.isStatic(builderModifiers) && !Modifier.isFinal(builderModifiers));
        assertTrue(Modifier.isStatic(GlVertexFormat.class.getMethod("builder", int.class).getModifiers()));
        assertSame(GlVertexFormat.Builder.class, GlVertexFormat.class.getMethod("builder", int.class).getReturnType());
        assertSame(Collection.class, GlVertexFormat.class.getMethod("getAttributes").getReturnType());
    }

    // Holds only private final instance fields and one public constant
    @Test
    void holdsOnlyPrivateFinalInstanceFields() {
        for (Field field : GlVertexFormat.class.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (field.isSynthetic()) {
                continue;
            }
            if (Modifier.isPublic(modifiers)) {
                assertEquals("NEXT_ALIGNED_POINTER", field.getName());
                continue;
            }
            assertTrue(Modifier.isPrivate(modifiers) && Modifier.isFinal(modifiers), field.getName());
        }
    }

    // Creates a separate builder on every call of the factory and the constructor
    @Test
    void createsASeparateBuilderOnEveryCall() {
        GlVertexFormat.Builder first = GlVertexFormat.builder(8);
        GlVertexFormat.Builder second = GlVertexFormat.builder(8);
        assertNotSame(first, second);
        first.addElement("a", 0, GlVertexAttributeFormat.FLOAT, 1, false, false);
        second.addElement("a", 4, GlVertexAttributeFormat.FLOAT, 1, false, false);
        assertEquals(0, first.build().getAttribute("a").getPointer());
        assertEquals(4, second.build().getAttribute("a").getPointer());

        GlVertexFormat constructed = new GlVertexFormat.Builder(8).addElement("a", 4, GlVertexAttributeFormat.FLOAT, 1, false, false).build();
        assertEquals(8, constructed.getStride());
        assertEquals(4, constructed.getAttribute("a").getPointer());
    }

    // Stores every field of each element and the builder stride and keeps declaration order
    @Test
    void storesEveryFieldOfEachElement() {
        GlVertexFormat format = GlVertexFormat.builder(32)
                .addElement("pos", 0, GlVertexAttributeFormat.FLOAT, 3, false, false)
                .addElement("color", 12, GlVertexAttributeFormat.UNSIGNED_BYTE, 4, true, false)
                .addElement("uv", 16, GlVertexAttributeFormat.UNSIGNED_SHORT, 2, true, true)
                .addElement("normal", 20, GlVertexAttributeFormat.BYTE, 4, false, true)
                .addElement("light", 24, GlVertexAttributeFormat.UNSIGNED_INT, 1, false, true)
                .addElement("id", 28, GlVertexAttributeFormat.SHORT, 2, false, false)
                .build();
        assertEquals(32, format.getStride());
        assertEquals(List.of("pos", "color", "uv", "normal", "light", "id"), names(format));
        assertEquals(List.of(0, 12, 16, 20, 24, 28), pointers(format));

        GlVertexAttribute uv = format.getAttribute("uv");
        assertSame(GlVertexAttributeFormat.UNSIGNED_SHORT, uv.getFormat());
        assertEquals("uv", uv.getName());
        assertEquals(2, uv.getCount());
        assertEquals(4, uv.getSize());
        assertEquals(16, uv.getPointer());
        assertEquals(32, uv.getStride());
        assertTrue(uv.isNormalized());
        assertTrue(uv.isIntType());

        GlVertexAttribute pos = format.getAttribute("pos");
        assertEquals(12, pos.getSize());
        assertFalse(pos.isNormalized());
        assertFalse(pos.isIntType());
        assertTrue(format.getAttribute("color").isNormalized());
        assertFalse(format.getAttribute("color").isIntType());
        assertFalse(format.getAttribute("normal").isNormalized());
        assertTrue(format.getAttribute("normal").isIntType());
        for (GlVertexAttribute attribute : format.getAttributes()) {
            assertSame(GlVertexAttribute.class, attribute.getClass());
            assertEquals(32, attribute.getStride());
        }
    }

    // Returns the same builder from every adding call
    @Test
    void returnsTheSameBuilderFromEveryAddingCall() {
        GlVertexFormat.Builder builder = GlVertexFormat.builder(64);
        assertSame(builder, builder.addElement("a", 0, GlVertexAttributeFormat.FLOAT, 1, false, false));
        assertSame(builder, builder.addElement("b", GlVertexFormat.NEXT_ALIGNED_POINTER, GlVertexAttributeFormat.FLOAT, 1, false, false));
        assertSame(builder, builder.addElements(List.of()));
        assertSame(builder, builder.addAllElements(threeAttributes()));
    }

    // Places a next aligned element at the furthest end rounded up to a multiple of 4
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "''|0",
            "a@0+1|4",
            "a@0+2|4",
            "a@0+3|4",
            "a@0+4|4",
            "a@4+1|8",
            "a@0+4 b@4+4 c@8+1|12",
            "a@8+4 b@0+4|12",
            "a@20+3 b@0+1 c@4+1|24",
            "a@0+1 b@1+1 c@2+1 d@3+1|4",
            "a@59+1|60",
            "a@60+4|64"
    })
    void placesANextAlignedElementAfterTheFurthestEnd(String spec, int expected) {
        GlVertexFormat.Builder builder = spec.isEmpty() ? GlVertexFormat.builder(128) : bytes(128, spec);
        assertEquals(expected, probeNextPointer(builder));
    }

    // Treats a literal negative 1 pointer as the next aligned pointer
    @Test
    void treatsALiteralNegativeOneAsTheNextAlignedPointer() {
        GlVertexFormat format = GlVertexFormat.builder(16)
                .addElement("pos", 0, GlVertexAttributeFormat.SHORT, 3, false, false)
                .addElement("color", -1, GlVertexAttributeFormat.UNSIGNED_BYTE, 4, true, false)
                .addElement("light", -1, GlVertexAttributeFormat.UNSIGNED_INT, 1, false, true)
                .build();
        assertEquals(List.of(0, 8, 12), pointers(format));
    }

    // Rejects a next aligned element that starts at the stride
    @Test
    void rejectsANextAlignedElementThatStartsAtTheStride() {
        GlVertexFormat.Builder builder = GlVertexFormat.builder(8).addElement("a", 0, GlVertexAttributeFormat.FLOAT, 2, false, false);
        expect(IllegalArgumentException.class, startsOutside("b", 8, 8), () -> builder.addElement("b", GlVertexFormat.NEXT_ALIGNED_POINTER, GlVertexAttributeFormat.BYTE, 1, false, false));
        GlVertexFormat.Builder rounded = GlVertexFormat.builder(8).addElement("a", 0, GlVertexAttributeFormat.BYTE, 4, false, false).addElement("b", 4, GlVertexAttributeFormat.BYTE, 1, false, false);
        expect(IllegalArgumentException.class, startsOutside("c", 8, 8), () -> rounded.addElement("c", GlVertexFormat.NEXT_ALIGNED_POINTER, GlVertexAttributeFormat.BYTE, 1, false, false));
    }

    // Rejects a next aligned pointer that is not a multiple of a component wider than 4 bytes
    @Test
    void rejectsANextAlignedPointerMisalignedForAWideComponent() {
        GlVertexFormat.Builder builder = GlVertexFormat.builder(16).addElement("a", 0, GlVertexAttributeFormat.BYTE, 1, false, false);
        expect(IllegalArgumentException.class, POINTER_MISALIGNED + 8, () -> builder.addElement("b", GlVertexFormat.NEXT_ALIGNED_POINTER, DOUBLE, 1, false, false));
        assertEquals(8, builder.addElement("c", 8, DOUBLE, 1, false, false).build().getAttribute("c").getPointer());
    }

    // Rejects a next aligned element whose rounded pointer passes the int range with the end limit message
    @ParameterizedTest
    @ValueSource(ints = {Integer.MAX_VALUE - 2, Integer.MAX_VALUE - 1, Integer.MAX_VALUE})
    void rejectsANextAlignedPointerPastTheIntRange(int end) {
        GlVertexFormat.Builder builder = GlVertexFormat.builder(Integer.MAX_VALUE).addElement("a", end - 1, GlVertexAttributeFormat.BYTE, 1, false, false);
        expect(IllegalArgumentException.class, END_LIMIT, () -> builder.addElement("b", GlVertexFormat.NEXT_ALIGNED_POINTER, GlVertexAttributeFormat.BYTE, 1, false, false));
        expect(IllegalArgumentException.class, END_LIMIT, () -> builder.addElement("b", GlVertexFormat.NEXT_ALIGNED_POINTER, GlVertexAttributeFormat.UNSIGNED_BYTE, 4, true, false));
        expect(NullPointerException.class, NAME_NULL, () -> builder.addElement(null, GlVertexFormat.NEXT_ALIGNED_POINTER, GlVertexAttributeFormat.BYTE, 1, false, false));
        expect(NullPointerException.class, FORMAT_NULL, () -> builder.addElement("b", GlVertexFormat.NEXT_ALIGNED_POINTER, null, 1, false, false));
        expect(IllegalArgumentException.class, COUNT_BAD, () -> builder.addElement("b", GlVertexFormat.NEXT_ALIGNED_POINTER, GlVertexAttributeFormat.BYTE, 0, false, false));
        expect(IllegalArgumentException.class, SIZE_LIMIT, () -> builder.addElement("b", GlVertexFormat.NEXT_ALIGNED_POINTER, new GlVertexAttributeFormat(0x1400, 1 << 30), 2, false, false));
        assertEquals(List.of("a"), names(builder.build()));
    }

    // Places a next aligned element at the last multiple of 4 inside the int range
    @Test
    void placesANextAlignedElementAtTheLastAlignedIntOffset() {
        GlVertexFormat format = GlVertexFormat.builder(Integer.MAX_VALUE)
                .addElement("a", Integer.MAX_VALUE - 4, GlVertexAttributeFormat.BYTE, 1, false, false)
                .addElement("b", GlVertexFormat.NEXT_ALIGNED_POINTER, GlVertexAttributeFormat.BYTE, 3, false, false)
                .build();
        assertEquals(Integer.MAX_VALUE - 3, format.getAttribute("b").getPointer());
        assertEquals(Integer.MAX_VALUE, format.getAttribute("b").getPointer() + format.getAttribute("b").getSize());
    }

    // Rejects a null name or a null format of an element
    @Test
    void rejectsANullNameOrFormat() {
        GlVertexFormat.Builder builder = GlVertexFormat.builder(16);
        expect(NullPointerException.class, NAME_NULL, () -> builder.addElement(null, 0, GlVertexAttributeFormat.FLOAT, 1, false, false));
        expect(NullPointerException.class, FORMAT_NULL, () -> builder.addElement("a", 0, null, 1, false, false));
        expect(NullPointerException.class, FORMAT_NULL, () -> builder.addElement(null, 0, null, 1, false, false));
        expect(NullPointerException.class, NAME_NULL, () -> builder.addElement(null, GlVertexFormat.NEXT_ALIGNED_POINTER, GlVertexAttributeFormat.FLOAT, 1, false, false));
        expect(IllegalArgumentException.class, NO_ATTRIBUTES, builder::build);
    }

    // Rejects a count that is not between 1 and 4
    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 5, Integer.MAX_VALUE})
    void rejectsACountThatIsNotBetweenOneAndFour(int count) {
        GlVertexFormat.Builder builder = GlVertexFormat.builder(64);
        expect(IllegalArgumentException.class, COUNT_BAD, () -> builder.addElement("a", 0, GlVertexAttributeFormat.BYTE, count, false, false));
        expect(IllegalArgumentException.class, COUNT_BAD, () -> builder.addElement("a", GlVertexFormat.NEXT_ALIGNED_POINTER, GlVertexAttributeFormat.FLOAT, count, false, false));
    }

    // Accepts every count between 1 and 4 and sizes the element by the count
    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4})
    void acceptsEveryCountBetweenOneAndFour(int count) {
        GlVertexFormat format = GlVertexFormat.builder(16).addElement("a", 0, GlVertexAttributeFormat.FLOAT, count, false, false).build();
        assertEquals(count, format.getAttribute("a").getCount());
        assertEquals(4 * count, format.getAttribute("a").getSize());
    }

    // Rejects a negative pointer other than the next aligned pointer
    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, Integer.MIN_VALUE + 1, -4, -2})
    void rejectsANegativePointerOtherThanTheNextAlignedPointer(int pointer) {
        expect(IllegalArgumentException.class, POINTER_NEGATIVE, () -> GlVertexFormat.builder(16).addElement("a", pointer, GlVertexAttributeFormat.BYTE, 1, false, false));
    }

    // Rejects a pointer that is not a multiple of the component size
    @ParameterizedTest
    @CsvSource({"FLOAT, 1, 4", "FLOAT, 2, 4", "FLOAT, 3, 4", "FLOAT, 6, 4", "SHORT, 1, 2", "UNSIGNED_SHORT, 7, 2", "UNSIGNED_INT, 10, 4"})
    void rejectsAMisalignedPointer(String name, int pointer, int unit) throws ReflectiveOperationException {
        GlVertexAttributeFormat format = (GlVertexAttributeFormat) GlVertexAttributeFormat.class.getField(name).get(null);
        expect(IllegalArgumentException.class, POINTER_MISALIGNED + unit, () -> GlVertexFormat.builder(32).addElement("a", pointer, format, 1, false, false));
    }

    // Rejects a stride that is not a multiple of the component size
    @ParameterizedTest
    @CsvSource({"FLOAT, 6, 4", "FLOAT, 2, 4", "SHORT, 3, 2", "UNSIGNED_INT, 2147483647, 4"})
    void rejectsAStrideThatIsNotAMultipleOfTheComponentSize(String name, int stride, int unit) throws ReflectiveOperationException {
        GlVertexAttributeFormat format = (GlVertexAttributeFormat) GlVertexAttributeFormat.class.getField(name).get(null);
        expect(IllegalArgumentException.class, STRIDE_MISALIGNED + unit, () -> GlVertexFormat.builder(stride).addElement("a", 0, format, 1, false, false));
    }

    // Rejects every element of a negative stride and builds nothing
    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -8, -1})
    void rejectsEveryElementOfANegativeStride(int stride) {
        GlVertexFormat.Builder builder = GlVertexFormat.builder(stride);
        expect(IllegalArgumentException.class, STRIDE_NEGATIVE, () -> builder.addElement("a", 0, GlVertexAttributeFormat.BYTE, 1, false, false));
        expect(IllegalArgumentException.class, STRIDE_NEGATIVE, () -> builder.addElement("a", GlVertexFormat.NEXT_ALIGNED_POINTER, GlVertexAttributeFormat.BYTE, 1, false, false));
        expect(IllegalArgumentException.class, NO_ATTRIBUTES, builder::build);
    }

    // Rejects every element of a zero stride and builds nothing
    @Test
    void rejectsEveryElementOfAZeroStride() {
        GlVertexFormat.Builder builder = GlVertexFormat.builder(0);
        expect(IllegalArgumentException.class, startsOutside("a", 0, 0), () -> builder.addElement("a", 0, GlVertexAttributeFormat.BYTE, 1, false, false));
        expect(IllegalArgumentException.class, startsOutside("a", 0, 0), () -> builder.addElement("a", GlVertexFormat.NEXT_ALIGNED_POINTER, GlVertexAttributeFormat.FLOAT, 1, false, false));
        expect(IllegalArgumentException.class, NO_ATTRIBUTES, builder::build);
    }

    // Accepts an element that ends exactly at the stride
    @ParameterizedTest
    @CsvSource({"8, 4, 4", "16, 15, 1", "16, 12, 4", "1, 0, 1", "4, 0, 4"})
    void acceptsAnElementThatEndsAtTheStride(int stride, int pointer, int count) {
        GlVertexFormat format = GlVertexFormat.builder(stride).addElement("a", pointer, GlVertexAttributeFormat.BYTE, count, false, false).build();
        assertEquals(stride, format.getAttribute("a").getPointer() + format.getAttribute("a").getSize());
    }

    // Rejects an element that starts at or past the stride
    @ParameterizedTest
    @CsvSource({"16, 16", "16, 20", "16, 1000", "4, 4", "1, 1", "2147483646, 2147483646"})
    void rejectsAnElementThatStartsAtOrPastTheStride(int stride, int pointer) {
        expect(IllegalArgumentException.class, startsOutside("a", pointer, stride), () -> GlVertexFormat.builder(stride).addElement("a", pointer, GlVertexAttributeFormat.BYTE, 1, false, false));
    }

    // Rejects an element that starts inside the stride and ends past it
    @ParameterizedTest
    @CsvSource({"16, 13, 4", "16, 15, 2", "8, 7, 2", "2, 0, 3", "2147483646, 2147483645, 2"})
    void rejectsAnElementThatEndsPastTheStride(int stride, int pointer, int count) {
        expect(IllegalArgumentException.class, EXTENDS_OUTSIDE, () -> GlVertexFormat.builder(stride).addElement("a", pointer, GlVertexAttributeFormat.BYTE, count, false, false));
    }

    // Rejects an element whose end passes the int range before the stride check
    @Test
    void rejectsAnElementWhoseEndPassesTheIntRange() {
        GlVertexFormat.Builder builder = GlVertexFormat.builder(Integer.MAX_VALUE);
        expect(IllegalArgumentException.class, END_LIMIT, () -> builder.addElement("a", Integer.MAX_VALUE, GlVertexAttributeFormat.BYTE, 1, false, false));
        expect(IllegalArgumentException.class, END_LIMIT, () -> builder.addElement("a", Integer.MAX_VALUE - 1, GlVertexAttributeFormat.BYTE, 2, false, false));
        expect(IllegalArgumentException.class, END_LIMIT, () -> GlVertexFormat.builder(16).addElement("a", Integer.MAX_VALUE - 1, GlVertexAttributeFormat.BYTE, 4, false, false));
        expect(IllegalArgumentException.class, SIZE_LIMIT, () -> builder.addElement("a", 0, new GlVertexAttributeFormat(0x1400, 1 << 29), 4, false, false));
    }

    // Builds a layout whose last element ends at the int limit
    @Test
    void buildsALayoutThatEndsAtTheIntLimit() {
        GlVertexFormat format = GlVertexFormat.builder(Integer.MAX_VALUE)
                .addElement("a", 0, GlVertexAttributeFormat.BYTE, 4, false, false)
                .addElement("b", Integer.MAX_VALUE - 4, GlVertexAttributeFormat.UNSIGNED_BYTE, 4, true, false)
                .build();
        assertEquals(Integer.MAX_VALUE, format.getStride());
        assertEquals(Integer.MAX_VALUE, format.getAttribute("b").getPointer() + format.getAttribute("b").getSize());
        assertEquals("GlVertexFormat{attributes=2,stride=2147483647}", format.toString());
    }

    // Rejects a name that the builder already holds
    @Test
    void rejectsADuplicateName() {
        GlVertexFormat.Builder builder = GlVertexFormat.builder(16).addElement("a", 0, GlVertexAttributeFormat.FLOAT, 1, false, false);
        expect(IllegalStateException.class, duplicate("a"), () -> builder.addElement("a", 4, GlVertexAttributeFormat.FLOAT, 1, false, false));
        expect(IllegalStateException.class, duplicate("a"), () -> builder.addElement("a", GlVertexFormat.NEXT_ALIGNED_POINTER, GlVertexAttributeFormat.BYTE, 1, false, false));
        expect(IllegalStateException.class, duplicate("a"), () -> builder.addElement("a", 0, GlVertexAttributeFormat.FLOAT, 1, false, false));
    }

    // Rejects a name equal to a held name in a different string object
    @Test
    void rejectsAnEqualNameInADifferentString() {
        GlVertexFormat.Builder builder = GlVertexFormat.builder(16).addElement("color", 0, GlVertexAttributeFormat.FLOAT, 1, false, false);
        String copy = new String(new char[]{'c', 'o', 'l', 'o', 'r'});
        assertNotSame("color", copy);
        expect(IllegalStateException.class, duplicate("color"), () -> builder.addElement(copy, 8, GlVertexAttributeFormat.FLOAT, 1, false, false));
        expect(IllegalStateException.class, duplicate("color"), () -> builder.addElement("col" + "or".repeat(1), 8, GlVertexAttributeFormat.FLOAT, 1, false, false));
        assertEquals(List.of("color"), names(builder.build()));
    }

    // Keeps the first element and its end when a duplicate name is rejected
    @Test
    void keepsTheFirstElementWhenADuplicateIsRejected() {
        GlVertexFormat.Builder builder = GlVertexFormat.builder(32).addElement("a", 0, GlVertexAttributeFormat.FLOAT, 1, false, false);
        expect(IllegalStateException.class, duplicate("a"), () -> builder.addElement("a", 16, GlVertexAttributeFormat.FLOAT, 4, false, false));
        GlVertexFormat format = builder.build();
        assertEquals(0, format.getAttribute("a").getPointer());
        assertEquals(4, format.getAttribute("a").getSize());
        assertEquals(4, probeNextPointer(builder));
    }

    // Leaves the builder unchanged after every kind of rejected element
    @Test
    void leavesTheBuilderUnchangedAfterARejectedElement() {
        GlVertexFormat.Builder builder = GlVertexFormat.builder(16).addElement("a", 0, GlVertexAttributeFormat.FLOAT, 1, false, false);
        List<Executable> rejected = List.of(
                () -> builder.addElement(null, 4, GlVertexAttributeFormat.FLOAT, 1, false, false),
                () -> builder.addElement("b", 4, null, 1, false, false),
                () -> builder.addElement("b", 4, GlVertexAttributeFormat.FLOAT, 0, false, false),
                () -> builder.addElement("b", -2, GlVertexAttributeFormat.FLOAT, 1, false, false),
                () -> builder.addElement("b", 6, GlVertexAttributeFormat.FLOAT, 1, false, false),
                () -> builder.addElement("b", 16, GlVertexAttributeFormat.FLOAT, 1, false, false),
                () -> builder.addElement("b", 13, GlVertexAttributeFormat.BYTE, 4, false, false),
                () -> builder.addElement("b", GlVertexFormat.NEXT_ALIGNED_POINTER, DOUBLE, 1, false, false),
                () -> builder.addElement("a", 8, GlVertexAttributeFormat.FLOAT, 2, false, false),
                () -> builder.addElement(new String("a"), 8, GlVertexAttributeFormat.FLOAT, 2, false, false),
                () -> builder.addElements(null),
                () -> builder.addAllElements(null));
        for (Executable action : rejected) {
            assertThrows(RuntimeException.class, action);
            GlVertexFormat format = builder.build();
            assertEquals(List.of("a"), names(format));
            assertEquals(List.of(0), pointers(format));
        }
        assertEquals(4, probeNextPointer(builder));
    }

    // Accepts an empty name and finds it again
    @Test
    void acceptsAnEmptyName() {
        GlVertexFormat format = GlVertexFormat.builder(4).addElement("", 0, GlVertexAttributeFormat.FLOAT, 1, false, false).build();
        assertEquals("", format.getAttribute("").getName());
        expect(NullPointerException.class, missing("a"), () -> format.getAttribute("a"));
    }

    // Rejects a null collection of attributes
    @Test
    void rejectsANullCollection() {
        assertThrows(NullPointerException.class, () -> GlVertexFormat.builder(16).addElements(null));
    }

    // Adds nothing for an empty collection
    @Test
    void addsNothingForAnEmptyCollection() {
        GlVertexFormat.Builder builder = GlVertexFormat.builder(16).addElements(List.of()).addElements(new ArrayList<>());
        expect(IllegalArgumentException.class, NO_ATTRIBUTES, builder::build);
        builder.addElement("a", 0, GlVertexAttributeFormat.FLOAT, 1, false, false).addElements(List.of());
        assertEquals(List.of("a"), names(builder.build()));
    }

    // Copies each attribute in order as a new attribute with the stride of the builder
    @Test
    void copiesEachAttributeWithTheBuilderStride() {
        GlVertexFormat source = threeAttributes();
        GlVertexFormat copy = GlVertexFormat.builder(40).addElements(source.getAttributes()).build();
        assertEquals(40, copy.getStride());
        assertEquals(names(source), names(copy));
        Iterator<GlVertexAttribute> originals = source.getAttributes().iterator();
        for (GlVertexAttribute attribute : copy.getAttributes()) {
            GlVertexAttribute original = originals.next();
            assertNotSame(original, attribute);
            assertSame(GlVertexAttribute.class, attribute.getClass());
            assertSame(original.getFormat(), attribute.getFormat());
            assertSame(original.getName(), attribute.getName());
            assertEquals(original.getCount(), attribute.getCount());
            assertEquals(original.getSize(), attribute.getSize());
            assertEquals(original.getPointer(), attribute.getPointer());
            assertEquals(original.isNormalized(), attribute.isNormalized());
            assertEquals(original.isIntType(), attribute.isIntType());
            assertEquals(20, original.getStride());
            assertEquals(40, attribute.getStride());
        }
    }

    // Copies a binding and a sized subclass as plain attributes sized by format and count
    @Test
    void copiesSubclassesAsPlainAttributes() {
        GlVertexAttribute source = new GlVertexAttribute(GlVertexAttributeFormat.UNSIGNED_SHORT, "uv", 2, true, 4, 8, true);
        GlVertexAttribute binding = new GlVertexAttributeBinding(3, source, 2);
        // Subclass that holds a size of 12 bytes for one float component
        GlVertexAttribute sized = new GlVertexAttribute(GlVertexAttributeFormat.FLOAT, 12, 1, "wide", false, 8, 8, false) {
        };
        GlVertexFormat format = GlVertexFormat.builder(16).addElements(List.of(binding, sized)).build();
        GlVertexAttribute uv = format.getAttribute("uv");
        assertSame(GlVertexAttribute.class, uv.getClass());
        assertEquals(4, uv.getSize());
        assertEquals(4, uv.getPointer());
        assertTrue(uv.isNormalized() && uv.isIntType());
        GlVertexAttribute wide = format.getAttribute("wide");
        assertSame(GlVertexAttribute.class, wide.getClass());
        assertEquals(4, wide.getSize());
        assertEquals(16, wide.getStride());
    }

    // Stops at the first rejected copy and keeps the copies before it
    @Test
    void stopsAtTheFirstRejectedCopy() {
        GlVertexAttribute a = new GlVertexAttribute(GlVertexAttributeFormat.FLOAT, "a", 1, false, 0, 16, false);
        GlVertexAttribute b = new GlVertexAttribute(GlVertexAttributeFormat.FLOAT, "b", 1, false, 4, 16, false);
        GlVertexAttribute far = new GlVertexAttribute(GlVertexAttributeFormat.FLOAT, "far", 1, false, 12, 16, false);

        GlVertexFormat.Builder withNull = GlVertexFormat.builder(16);
        assertThrows(NullPointerException.class, () -> withNull.addElements(Arrays.asList(a, null, b)));
        assertEquals(List.of("a"), names(withNull.build()));

        GlVertexFormat.Builder withDuplicate = GlVertexFormat.builder(16);
        expect(IllegalStateException.class, duplicate("a"), () -> withDuplicate.addElements(List.of(a, b, a, far)));
        assertEquals(List.of("a", "b"), names(withDuplicate.build()));

        GlVertexFormat.Builder narrow = GlVertexFormat.builder(8);
        expect(IllegalArgumentException.class, startsOutside("far", 12, 8), () -> narrow.addElements(List.of(a, b, far)));
        assertEquals(List.of("a", "b"), names(narrow.build()));

        expect(IllegalArgumentException.class, STRIDE_MISALIGNED + 4, () -> GlVertexFormat.builder(6).addElements(List.of(a)));
        expect(IllegalArgumentException.class, STRIDE_NEGATIVE, () -> GlVertexFormat.builder(-4).addElements(List.of(a)));
    }

    // Rejects a null format to copy from
    @Test
    void rejectsANullFormatToCopy() {
        assertThrows(NullPointerException.class, () -> GlVertexFormat.builder(16).addAllElements(null));
    }

    // Copies every attribute of another format in order and accepts more elements after them
    @Test
    void copiesEveryAttributeOfAnotherFormat() {
        GlVertexFormat source = threeAttributes();
        GlVertexFormat copy = GlVertexFormat.builder(24).addAllElements(source).addElement("extra", GlVertexFormat.NEXT_ALIGNED_POINTER, GlVertexAttributeFormat.FLOAT, 1, false, false).build();
        assertEquals(List.of("pos", "color", "light", "extra"), names(copy));
        assertEquals(List.of(0, 12, 16, 20), pointers(copy));
        assertEquals(3, source.getAttributes().size());
        expect(NullPointerException.class, missing("extra"), () -> source.getAttribute("extra"));
    }

    // Rejects copying a format into the builder that built it and leaves the format unchanged
    @Test
    void rejectsCopyingAFormatIntoItsOwnBuilder() {
        GlVertexFormat.Builder builder = GlVertexFormat.builder(16).addElement("a", 0, GlVertexAttributeFormat.FLOAT, 1, false, false).addElement("b", 4, GlVertexAttributeFormat.FLOAT, 1, false, false);
        GlVertexFormat format = builder.build();
        expect(IllegalStateException.class, duplicate("a"), () -> builder.addAllElements(format));
        assertEquals(List.of("a", "b"), names(format));
        assertEquals(List.of("a", "b"), names(builder.build()));
    }

    // Rejects a builder without attributes on every call
    @Test
    void rejectsAnEmptyBuilderOnEveryCall() {
        GlVertexFormat.Builder builder = GlVertexFormat.builder(16);
        expect(IllegalArgumentException.class, NO_ATTRIBUTES, builder::build);
        expect(IllegalArgumentException.class, NO_ATTRIBUTES, builder::build);
        expect(IllegalArgumentException.class, NO_ATTRIBUTES, () -> GlVertexFormat.builder(Integer.MAX_VALUE).build());
        expect(IllegalArgumentException.class, NO_ATTRIBUTES, () -> GlVertexFormat.builder(Integer.MIN_VALUE).build());
    }

    // Builds a single element format
    @Test
    void buildsASingleElement() {
        GlVertexFormat format = GlVertexFormat.builder(4).addElement("a", 0, GlVertexAttributeFormat.FLOAT, 1, false, false).build();
        assertEquals(1, format.getAttributes().size());
        assertSame(format.getAttribute("a"), format.getAttributes().iterator().next());
        assertEquals("GlVertexFormat{attributes=1,stride=4}", format.toString());
    }

    // Reports the first overlapping pair in pointer order or builds when no elements overlap
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "a@0+4 b@2+2|b a",
            "a@0+2 b@0+2|b a",
            "b@0+2 a@0+2|a b",
            "a@0+4 b@1+1 c@2+1|b a",
            "c@2+1 b@1+1 a@0+4|b a",
            "a@0+4 b@2+1 c@1+1|c a",
            "a@4+4 b@0+4 c@6+1|c a",
            "a@10+4 b@4+4 c@12+1|c a",
            "a@8+1 b@8+1 c@0+4 d@2+2|d c",
            "a@4+1 b@0+1 c@4+1|c a",
            "a@8+4 b@0+4 c@4+4 d@12+4 e@13+1|e d",
            "a@8+4 b@0+4|''",
            "a@8+4 b@0+4 c@4+4|''",
            "a@0+1 b@1+1 c@2+1 d@3+1|''",
            "a@3+1 b@2+1 c@1+1 d@0+1|''",
            "a@0+4 b@31+1|''",
            "a@28+4 b@0+4 c@12+4 d@4+4|''"
    })
    void reportsTheFirstOverlapInPointerOrder(String spec, String pair) {
        GlVertexFormat.Builder builder = bytes(32, spec);
        if (pair.isEmpty()) {
            GlVertexFormat format = builder.build();
            assertEquals(spec.split(" ").length, format.getAttributes().size());
            return;
        }
        String[] names = pair.split(" ");
        expect(IllegalArgumentException.class, overlap(names[0], names[1]), builder::build);
    }

    // Reports an earlier overlap that a later out of order element creates
    @Test
    void reportsAnEarlierOverlapFromALaterElement() {
        GlVertexFormat.Builder builder = bytes(32, "a@4+4 b@8+4 c@12+4 d@14+1");
        expect(IllegalArgumentException.class, overlap("d", "c"), builder::build);
        builder.addElement("e", 6, GlVertexAttributeFormat.BYTE, 1, false, false);
        expect(IllegalArgumentException.class, overlap("e", "a"), builder::build);
        builder.addElement("f", 0, GlVertexAttributeFormat.BYTE, 4, false, false);
        expect(IllegalArgumentException.class, overlap("e", "a"), builder::build);
        builder.addElement("g", 2, GlVertexAttributeFormat.BYTE, 1, false, false);
        expect(IllegalArgumentException.class, overlap("g", "f"), builder::build);
        builder.addElement("h", 30, GlVertexAttributeFormat.BYTE, 1, false, false);
        expect(IllegalArgumentException.class, overlap("g", "f"), builder::build);
    }

    // Keeps rejecting an overlap on repeated builds and after later elements
    @Test
    void keepsRejectingAnOverlapOnRepeatedBuilds() {
        GlVertexFormat.Builder builder = bytes(32, "a@0+4 b@2+1");
        for (int run = 0; run < 3; run++) {
            expect(IllegalArgumentException.class, overlap("b", "a"), builder::build);
        }
        builder.addElement("c", 16, GlVertexAttributeFormat.BYTE, 1, false, false);
        expect(IllegalArgumentException.class, overlap("b", "a"), builder::build);
        builder.addElement("d", 8, GlVertexAttributeFormat.BYTE, 1, false, false);
        expect(IllegalArgumentException.class, overlap("b", "a"), builder::build);
    }

    // Builds again after an out of order check and checks later elements against the top element
    @Test
    void buildsAgainAfterAnOutOfOrderCheck() {
        GlVertexFormat.Builder builder = bytes(32, "a@8+4 b@0+4");
        assertEquals(List.of("a", "b"), names(builder.build()));
        assertEquals(List.of("a", "b"), names(builder.build()));
        builder.addElement("c", 12, GlVertexAttributeFormat.BYTE, 4, false, false);
        assertEquals(List.of("a", "b", "c"), names(builder.build()));
        builder.addElement("d", 14, GlVertexAttributeFormat.BYTE, 1, false, false);
        expect(IllegalArgumentException.class, overlap("d", "c"), builder::build);
    }

    // Keeps declaration order for elements added out of pointer order
    @Test
    void keepsDeclarationOrderForElementsOutOfPointerOrder() {
        GlVertexFormat format = bytes(32, "z@28+4 a@0+4 m@12+4 b@4+4").build();
        assertEquals(List.of("z", "a", "m", "b"), names(format));
        assertEquals(List.of(28, 0, 12, 4), pointers(format));
    }

    // Returns a separate format with the same layout from every build
    @Test
    void returnsASeparateFormatFromEveryBuild() {
        GlVertexFormat.Builder builder = bytes(16, "a@0+4 b@4+4");
        GlVertexFormat first = builder.build();
        GlVertexFormat second = builder.build();
        assertNotSame(first, second);
        assertNotEquals(first, second);
        assertEquals(names(first), names(second));
        assertSame(first.getAttribute("a"), second.getAttribute("a"));
        assertEquals(first.toString(), second.toString());
    }

    // Leaves a built format unchanged when its builder receives more elements
    @Test
    void leavesABuiltFormatUnchangedWhenTheBuilderGrows() {
        GlVertexFormat.Builder builder = GlVertexFormat.builder(16).addElement("a", 0, GlVertexAttributeFormat.FLOAT, 1, false, false);
        GlVertexFormat first = builder.build();
        builder.addElement("b", GlVertexFormat.NEXT_ALIGNED_POINTER, GlVertexAttributeFormat.FLOAT, 1, false, false);
        assertEquals(List.of("a"), names(first));
        assertEquals("GlVertexFormat{attributes=1,stride=16}", first.toString());
        expect(NullPointerException.class, missing("b"), () -> first.getAttribute("b"));

        GlVertexFormat second = builder.build();
        builder.addElements(List.of(new GlVertexAttribute(GlVertexAttributeFormat.FLOAT, "c", 1, false, 8, 16, false)));
        builder.addAllElements(GlVertexFormat.builder(16).addElement("d", 12, GlVertexAttributeFormat.FLOAT, 1, false, false).build());
        assertEquals(List.of("a"), names(first));
        assertEquals(List.of("a", "b"), names(second));
        assertEquals(List.of("a", "b", "c", "d"), names(builder.build()));
    }

    // Leaves every earlier format unchanged across many builds and additions
    @Test
    void leavesEveryEarlierFormatUnchangedAcrossManyBuilds() {
        GlVertexFormat.Builder builder = GlVertexFormat.builder(256);
        List<GlVertexFormat> formats = new ArrayList<>();
        for (int index = 0; index < 64; index++) {
            builder.addElement("e" + index, GlVertexFormat.NEXT_ALIGNED_POINTER, GlVertexAttributeFormat.FLOAT, 1, false, false);
            formats.add(builder.build());
        }
        for (int index = 0; index < formats.size(); index++) {
            GlVertexFormat format = formats.get(index);
            assertEquals(index + 1, format.getAttributes().size());
            assertEquals(4 * index, format.getAttribute("e" + index).getPointer());
            int next = index + 1;
            if (next < formats.size()) {
                expect(NullPointerException.class, missing("e" + next), () -> format.getAttribute("e" + next));
            }
        }
    }

    // Finds each attribute by name as the same object the view holds
    @Test
    void findsEachAttributeByName() {
        GlVertexFormat format = threeAttributes();
        for (GlVertexAttribute attribute : format.getAttributes()) {
            assertSame(attribute, format.getAttribute(attribute.getName()));
        }
    }

    // Finds an attribute by an equal name in a different string object
    @Test
    void findsAnAttributeByAnEqualNameInADifferentString() {
        GlVertexFormat format = threeAttributes();
        String copy = new String(new char[]{'c', 'o', 'l', 'o', 'r'});
        assertNotSame("color", copy);
        assertSame(format.getAttribute("color"), format.getAttribute(copy));
        assertSame(format.getAttribute("light"), format.getAttribute(new StringBuilder("lig").append("ht").toString()));
    }

    // Rejects a missing name or a null name or a name in a different case
    @Test
    void rejectsAMissingOrNullName() {
        GlVertexFormat format = threeAttributes();
        expect(NullPointerException.class, missing("missing"), () -> format.getAttribute("missing"));
        expect(NullPointerException.class, missing("null"), () -> format.getAttribute(null));
        expect(NullPointerException.class, missing(""), () -> format.getAttribute(""));
        expect(NullPointerException.class, missing("Color"), () -> format.getAttribute("Color"));
        expect(NullPointerException.class, missing("color "), () -> format.getAttribute("color "));
    }

    // Returns a read only view of the attributes in declaration order
    @Test
    void returnsAReadOnlyViewInDeclarationOrder() {
        GlVertexFormat format = threeAttributes();
        Collection<GlVertexAttribute> attributes = format.getAttributes();
        GlVertexAttribute pos = format.getAttribute("pos");
        assertEquals(3, attributes.size());
        assertFalse(attributes.isEmpty());
        assertTrue(attributes.contains(pos));
        assertFalse(attributes.contains(null));
        assertFalse(attributes.contains(new GlVertexAttribute(GlVertexAttributeFormat.FLOAT, "pos", 3, false, 0, 20, false)));
        assertEquals(List.of("pos", "color", "light"), names(format));
        assertEquals(3, attributes.toArray().length);
        assertSame(pos, attributes.toArray(new GlVertexAttribute[0])[0]);

        assertThrows(UnsupportedOperationException.class, attributes::clear);
        assertThrows(UnsupportedOperationException.class, () -> attributes.add(pos));
        assertThrows(UnsupportedOperationException.class, () -> attributes.remove(pos));
        assertThrows(UnsupportedOperationException.class, () -> attributes.addAll(List.of(pos)));
        assertThrows(UnsupportedOperationException.class, () -> attributes.removeAll(List.of(pos)));
        assertThrows(UnsupportedOperationException.class, () -> attributes.retainAll(List.of()));
        assertThrows(UnsupportedOperationException.class, () -> attributes.removeIf(attribute -> true));
        Iterator<GlVertexAttribute> iterator = attributes.iterator();
        iterator.next();
        assertThrows(UnsupportedOperationException.class, iterator::remove);
        assertEquals(3, format.getAttributes().size());
    }

    // Returns the same view and the same answers on repeated calls
    @Test
    void answersTheSameOnRepeatedCalls() {
        GlVertexFormat format = threeAttributes();
        assertSame(format.getAttributes(), format.getAttributes());
        assertSame(format.getAttribute("light"), format.getAttribute("light"));
        assertEquals(format.toString(), format.toString());
        assertEquals(format.getStride(), format.getStride());
    }

    // Reports the stride the builder received
    @ParameterizedTest
    @ValueSource(ints = {1, 4, 20, 32, 1 << 20, Integer.MAX_VALUE})
    void reportsTheStride(int stride) {
        assertEquals(stride, GlVertexFormat.builder(stride).addElement("a", 0, GlVertexAttributeFormat.BYTE, 1, false, false).build().getStride());
    }

    // Describes the attribute count and stride
    @Test
    void describesTheAttributeCountAndStride() {
        assertEquals("GlVertexFormat{attributes=3,stride=20}", threeAttributes().toString());
        assertEquals("GlVertexFormat{attributes=0,stride=-2147483648}", new GlVertexFormat(new Reference2ReferenceLinkedOpenHashMap<>(), Integer.MIN_VALUE).toString());
        assertEquals("GlVertexFormat{attributes=0,stride=0}", new GlVertexFormat(new Reference2ReferenceLinkedOpenHashMap<>(), 0).toString());
    }

    // Rejects a null map in the public constructor
    @Test
    void rejectsANullMapInThePublicConstructor() {
        expect(NullPointerException.class, MAP_NULL, () -> new GlVertexFormat(null, 16));
    }

    // Copies the map given to the public constructor and ignores later changes to the map
    @Test
    void copiesTheMapGivenToThePublicConstructor() {
        GlVertexAttribute a = new GlVertexAttribute(GlVertexAttributeFormat.FLOAT, "a", 1, false, 0, 8, false);
        GlVertexAttribute b = new GlVertexAttribute(GlVertexAttributeFormat.FLOAT, "b", 1, false, 4, 8, false);
        Reference2ReferenceLinkedOpenHashMap<String, GlVertexAttribute> map = keyed(a);
        GlVertexFormat format = new GlVertexFormat(map, 8);
        map.put("b", b);
        map.remove("a");
        assertEquals(List.of("a"), names(format));
        assertSame(a, format.getAttribute("a"));
        expect(NullPointerException.class, missing("b"), () -> format.getAttribute("b"));
        assertEquals(8, format.getStride());
    }

    // Keeps the keys and order and stride given to the public constructor without checking them
    @Test
    void keepsTheEntriesGivenToThePublicConstructor() {
        GlVertexAttribute a = new GlVertexAttribute(GlVertexAttributeFormat.FLOAT, "a", 1, false, 0, 8, false);
        GlVertexAttribute b = new GlVertexAttribute(GlVertexAttributeFormat.FLOAT, "b", 1, false, 0, 8, false);
        Reference2ReferenceLinkedOpenHashMap<String, GlVertexAttribute> map = new Reference2ReferenceLinkedOpenHashMap<>();
        map.put("z", b);
        map.put("key", a);
        map.put(null, b);
        map.put("empty", null);
        GlVertexFormat format = new GlVertexFormat(map, -3);
        assertEquals(-3, format.getStride());
        assertEquals(4, format.getAttributes().size());
        assertEquals(Arrays.asList(b, a, b, null), new ArrayList<>(format.getAttributes()));
        assertSame(a, format.getAttribute("key"));
        assertSame(b, format.getAttribute(null));
        assertTrue(format.getAttributes().contains(null));
        expect(NullPointerException.class, missing("a"), () -> format.getAttribute("a"));
        expect(NullPointerException.class, missing("empty"), () -> format.getAttribute("empty"));
        assertSame(a, format.getAttribute(new String(new char[]{'k', 'e', 'y'})));
        assertEquals("GlVertexFormat{attributes=4,stride=-3}", format.toString());
    }

    // Places many next aligned elements and builds them inside the time limit
    @Test
    void placesManyNextAlignedElementsInsideTheTimeLimit() {
        GlVertexFormat format = assertTimeoutPreemptively(LARGE_LIMIT, () -> {
            GlVertexFormat.Builder builder = GlVertexFormat.builder(4 * MANY);
            for (int index = 0; index < MANY; index++) {
                builder.addElement("e" + index, GlVertexFormat.NEXT_ALIGNED_POINTER, GlVertexAttributeFormat.UNSIGNED_BYTE, 1 + (index & 3), false, false);
            }
            return builder.build();
        });
        assertEquals(MANY, format.getAttributes().size());
        assertEquals(4 * (MANY - 1), format.getAttribute("e" + (MANY - 1)).getPointer());
        assertEquals(4 * 1000, format.getAttribute("e1000").getPointer());
    }

    // Checks many elements added in falling pointer order for overlap inside the time limit
    @Test
    void checksManyFallingElementsInsideTheTimeLimit() {
        GlVertexFormat.Builder builder = assertTimeoutPreemptively(LARGE_LIMIT, () -> {
            GlVertexFormat.Builder falling = GlVertexFormat.builder(4 * MANY);
            for (int index = MANY - 1; index >= 0; index--) {
                falling.addElement("e" + index, 4 * index, GlVertexAttributeFormat.FLOAT, 1, false, false);
            }
            assertEquals(MANY, falling.build().getAttributes().size());
            return falling;
        });
        builder.addElement("late", 4 * 777 + 2, GlVertexAttributeFormat.SHORT, 1, false, false);
        expect(IllegalArgumentException.class, overlap("late", "e777"), () -> assertTimeoutPreemptively(LARGE_LIMIT, builder::build));
    }

    // Allocates no heap memory in warm lookups and view reads and stride reads
    @Test
    void warmReadsAllocateNothing() {
        GlVertexFormat format = threeAttributes();
        String name = new String(new char[]{'c', 'o', 'l', 'o', 'r'});
        long lookupBytes = allocatedBy(() -> format.getAttribute("light").getPointer());
        long equalLookupBytes = allocatedBy(() -> format.getAttribute(name).getPointer());
        long viewBytes = allocatedBy(() -> System.identityHashCode(format.getAttributes()));
        long strideBytes = allocatedBy(format::getStride);
        assertTrue(lookupBytes < COUNTED_CALLS, lookupBytes + " bytes allocated by getAttribute");
        assertTrue(equalLookupBytes < COUNTED_CALLS, equalLookupBytes + " bytes allocated by getAttribute of an equal name");
        assertTrue(viewBytes < COUNTED_CALLS, viewBytes + " bytes allocated by getAttributes");
        assertTrue(strideBytes < COUNTED_CALLS, strideBytes + " bytes allocated by getStride");
        assertNotEquals(0, sink);
    }
}
