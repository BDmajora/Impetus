package com.bdmajora.impetus.engine.impl.gl.attribute;

import com.bdmajora.impetus.engine.impl.gl.tessellation.TessellationBinding;
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
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Tests of every constructor and accessor of the vertex attribute binding
class GlVertexAttributeBindingTest {
    // Message of a null attribute
    private static final String ATTRIBUTE_NULL = "Attribute must not be null";
    // Message of a negative index
    private static final String INDEX_NEGATIVE = "Index must not be negative";
    // Message of a negative divisor
    private static final String DIVISOR_NEGATIVE = "Divisor must not be negative";
    // Message of a null format
    private static final String FORMAT_NULL = "Format must not be null";
    // Message of a null name
    private static final String NAME_NULL = "Name must not be null";
    // Message of a format whose component size is zero or negative
    private static final String UNIT_BAD = "Format size must be positive";
    // Message of a count outside the range between 1 and 4
    private static final String COUNT_BAD = "Count must be between 1 and 4";
    // Message of a negative pointer
    private static final String POINTER_NEGATIVE = "Pointer must not be negative";
    // Message of a negative stride
    private static final String STRIDE_NEGATIVE = "Stride must not be negative";
    // Message of a size that is zero or negative
    private static final String SIZE_BAD = "Size must be positive";
    // Message of an attribute that ends past the int range
    private static final String END_LIMIT = "Attribute end must not exceed 2147483647";
    // Start of the message of a pointer that is not a multiple of the component size
    private static final String POINTER_MISALIGNED = "Pointer must be a multiple of ";
    // Start of the message of a stride that is not a multiple of the component size
    private static final String STRIDE_MISALIGNED = "Stride must be a multiple of ";

    // Every built in format
    private static final List<GlVertexAttributeFormat> FORMATS = List.of(GlVertexAttributeFormat.FLOAT, GlVertexAttributeFormat.SHORT, GlVertexAttributeFormat.UNSIGNED_SHORT, GlVertexAttributeFormat.BYTE, GlVertexAttributeFormat.UNSIGNED_BYTE, GlVertexAttributeFormat.UNSIGNED_INT);

    // Attribute whose accessors return adjustable values and count every read
    private static final class Reporting extends GlVertexAttribute {
        // Format that getFormat returns
        GlVertexAttributeFormat format = GlVertexAttributeFormat.FLOAT;
        // Name that getName returns
        String name = "reported";
        // Size in bytes that getSize returns
        int size = 12;
        // Component count that getCount returns
        int count = 3;
        // Byte offset that getPointer returns
        int pointer = 4;
        // Stride in bytes that getStride returns
        int stride = 16;
        // Flag that isNormalized returns
        boolean normalized = true;
        // Flag that isIntType returns
        boolean intType = true;
        // Number of accessor reads
        int reads;

        // Creates a one byte attribute whose accessors report a three component float layout
        Reporting() {
            super(GlVertexAttributeFormat.BYTE, "base", 1, false, 0, 1, false);
        }

        // Returns the reported format and counts the read
        @Override
        public GlVertexAttributeFormat getFormat() {
            this.reads++;
            return this.format;
        }

        // Returns the reported name and counts the read
        @Override
        public String getName() {
            this.reads++;
            return this.name;
        }

        // Returns the reported size and counts the read
        @Override
        public int getSize() {
            this.reads++;
            return this.size;
        }

        // Returns the reported count and counts the read
        @Override
        public int getCount() {
            this.reads++;
            return this.count;
        }

        // Returns the reported pointer and counts the read
        @Override
        public int getPointer() {
            this.reads++;
            return this.pointer;
        }

        // Returns the reported stride and counts the read
        @Override
        public int getStride() {
            this.reads++;
            return this.stride;
        }

        // Returns the reported normalized flag and counts the read
        @Override
        public boolean isNormalized() {
            this.reads++;
            return this.normalized;
        }

        // Returns the reported integer flag and counts the read
        @Override
        public boolean isIntType() {
            this.reads++;
            return this.intType;
        }
    }

    // Creates a three component float position attribute
    private static GlVertexAttribute position() {
        return new GlVertexAttribute(GlVertexAttributeFormat.FLOAT, "pos", 3, false, 0, 16, false);
    }

    // Creates a two component unsigned short attribute with both flags set
    private static GlVertexAttribute uv() {
        return new GlVertexAttribute(GlVertexAttributeFormat.UNSIGNED_SHORT, "uv", 2, true, 6, 18, true);
    }

    // Creates a format of the byte type with the given component size
    private static GlVertexAttributeFormat unit(int size) {
        return new GlVertexAttributeFormat(GlVertexAttributeFormat.BYTE.typeId(), size);
    }

    // Creates a format of a float position and a byte color and an integer light value
    private static GlVertexFormat threeAttributes() {
        return GlVertexFormat.builder(20)
                .addElement("pos", 0, GlVertexAttributeFormat.FLOAT, 3, false, false)
                .addElement("color", 12, GlVertexAttributeFormat.UNSIGNED_BYTE, 4, true, false)
                .addElement("light", 16, GlVertexAttributeFormat.UNSIGNED_INT, 1, false, true)
                .build();
    }

    // Asserts that the action throws the exception type with the message
    private static void expect(Class<? extends RuntimeException> type, String message, Executable action) {
        assertEquals(message, assertThrows(type, action).getMessage());
    }

    // Asserts that both attributes hold the same layout and share the format and name
    private static void assertLayout(GlVertexAttribute expected, GlVertexAttribute actual) {
        assertSame(expected.getFormat(), actual.getFormat());
        assertSame(expected.getName(), actual.getName());
        assertEquals(expected.getSize(), actual.getSize());
        assertEquals(expected.getCount(), actual.getCount());
        assertEquals(expected.getPointer(), actual.getPointer());
        assertEquals(expected.getStride(), actual.getStride());
        assertEquals(expected.isNormalized(), actual.isNormalized());
        assertEquals(expected.isIntType(), actual.isIntType());
    }

    // Creates the arguments of a reported layout change with the exception it causes
    private static Arguments invalid(Class<? extends RuntimeException> type, String message, Consumer<Reporting> change) {
        return Arguments.of(type, message, change);
    }

    // Returns every built in format with every component count and both values of each flag
    static Stream<Arguments> layouts() {
        List<Arguments> cases = new ArrayList<>();
        for (GlVertexAttributeFormat format : FORMATS) {
            for (int count = 1; count <= 4; count++) {
                for (int flags = 0; flags < 4; flags++) {
                    cases.add(Arguments.of(format, count, (flags & 1) != 0, (flags & 2) != 0));
                }
            }
        }
        return cases.stream();
    }

    // Returns every reported layout change that the attribute constructor refuses with its exception
    static Stream<Arguments> invalidReports() {
        return Stream.of(
                invalid(NullPointerException.class, FORMAT_NULL, report -> report.format = null),
                invalid(NullPointerException.class, NAME_NULL, report -> report.name = null),
                invalid(NullPointerException.class, FORMAT_NULL, report -> {
                    report.format = null;
                    report.name = null;
                }),
                invalid(IllegalArgumentException.class, UNIT_BAD, report -> report.format = unit(0)),
                invalid(IllegalArgumentException.class, UNIT_BAD, report -> report.format = unit(-4)),
                invalid(IllegalArgumentException.class, UNIT_BAD, report -> report.format = unit(Integer.MIN_VALUE)),
                invalid(IllegalArgumentException.class, COUNT_BAD, report -> report.count = 0),
                invalid(IllegalArgumentException.class, COUNT_BAD, report -> report.count = 5),
                invalid(IllegalArgumentException.class, COUNT_BAD, report -> report.count = -1),
                invalid(IllegalArgumentException.class, COUNT_BAD, report -> report.count = Integer.MIN_VALUE),
                invalid(IllegalArgumentException.class, COUNT_BAD, report -> report.count = Integer.MAX_VALUE),
                invalid(IllegalArgumentException.class, POINTER_NEGATIVE, report -> report.pointer = -4),
                invalid(IllegalArgumentException.class, POINTER_NEGATIVE, report -> report.pointer = Integer.MIN_VALUE),
                invalid(IllegalArgumentException.class, STRIDE_NEGATIVE, report -> report.stride = -4),
                invalid(IllegalArgumentException.class, STRIDE_NEGATIVE, report -> report.stride = Integer.MIN_VALUE),
                invalid(IllegalArgumentException.class, SIZE_BAD, report -> report.size = 0),
                invalid(IllegalArgumentException.class, SIZE_BAD, report -> report.size = -4),
                invalid(IllegalArgumentException.class, SIZE_BAD, report -> report.size = Integer.MIN_VALUE),
                invalid(IllegalArgumentException.class, END_LIMIT, report -> report.pointer = Integer.MAX_VALUE - 3),
                invalid(IllegalArgumentException.class, END_LIMIT, report -> report.size = Integer.MAX_VALUE),
                invalid(IllegalArgumentException.class, POINTER_MISALIGNED + 4, report -> report.pointer = 1),
                invalid(IllegalArgumentException.class, POINTER_MISALIGNED + 4, report -> report.pointer = 2),
                invalid(IllegalArgumentException.class, STRIDE_MISALIGNED + 4, report -> report.stride = 18),
                invalid(IllegalArgumentException.class, STRIDE_MISALIGNED + 4, report -> report.stride = Integer.MAX_VALUE));
    }

    // Copies every field of every built in format and count and flag combination
    @ParameterizedTest
    @MethodSource("layouts")
    void copiesEveryFieldOfEveryLayout(GlVertexAttributeFormat format, int count, boolean normalized, boolean intType) {
        int unit = format.size();
        GlVertexAttribute source = new GlVertexAttribute(format, "attribute", count, normalized, unit * count, unit * 8, intType);
        GlVertexAttributeBinding binding = new GlVertexAttributeBinding(count * 7, source, count - 1);
        assertLayout(source, binding);
        assertEquals(count * 7, binding.getIndex());
        assertEquals(count - 1, binding.getDivisor());
    }

    // Uses a divisor of zero in the two argument constructor
    @Test
    void usesDivisorZeroInTheTwoArgumentConstructor() {
        GlVertexAttribute source = position();
        GlVertexAttributeBinding binding = new GlVertexAttributeBinding(5, source);
        assertLayout(source, binding);
        assertEquals(5, binding.getIndex());
        assertEquals(0, binding.getDivisor());
    }

    // Accepts every index and divisor at zero and one and the int limit
    @ParameterizedTest
    @CsvSource({"0,0", "0,1", "1,0", "1,1", "15,1", "16,1", "0,2147483647", "2147483647,0", "2147483647,2147483647"})
    void acceptsIndexAndDivisorAtTheIntLimits(int index, int divisor) {
        GlVertexAttribute source = position();
        GlVertexAttributeBinding binding = new GlVertexAttributeBinding(index, source, divisor);
        assertEquals(index, binding.getIndex());
        assertEquals(divisor, binding.getDivisor());
        assertLayout(source, binding);
        assertEquals(index, new GlVertexAttributeBinding(index, source).getIndex());
    }

    // Rejects a null attribute in both constructors before checking the index and divisor
    @Test
    void rejectsANullAttribute() {
        expect(NullPointerException.class, ATTRIBUTE_NULL, () -> new GlVertexAttributeBinding(0, null));
        expect(NullPointerException.class, ATTRIBUTE_NULL, () -> new GlVertexAttributeBinding(0, null, 0));
        expect(NullPointerException.class, ATTRIBUTE_NULL, () -> new GlVertexAttributeBinding(-1, null));
        expect(NullPointerException.class, ATTRIBUTE_NULL, () -> new GlVertexAttributeBinding(-1, null, -1));
        expect(NullPointerException.class, ATTRIBUTE_NULL, () -> new GlVertexAttributeBinding(Integer.MIN_VALUE, null, Integer.MIN_VALUE));
        expect(NullPointerException.class, ATTRIBUTE_NULL, () -> new GlVertexAttributeBinding(Integer.MAX_VALUE, null, Integer.MAX_VALUE));
    }

    // Rejects a negative index in both constructors
    @ParameterizedTest
    @ValueSource(ints = {-1, -2, -16, Integer.MIN_VALUE + 1, Integer.MIN_VALUE})
    void rejectsANegativeIndex(int index) {
        expect(IllegalArgumentException.class, INDEX_NEGATIVE, () -> new GlVertexAttributeBinding(index, position()));
        expect(IllegalArgumentException.class, INDEX_NEGATIVE, () -> new GlVertexAttributeBinding(index, position(), 0));
        expect(IllegalArgumentException.class, INDEX_NEGATIVE, () -> new GlVertexAttributeBinding(index, position(), Integer.MAX_VALUE));
    }

    // Rejects a negative divisor
    @ParameterizedTest
    @ValueSource(ints = {-1, -2, -16, Integer.MIN_VALUE + 1, Integer.MIN_VALUE})
    void rejectsANegativeDivisor(int divisor) {
        expect(IllegalArgumentException.class, DIVISOR_NEGATIVE, () -> new GlVertexAttributeBinding(0, position(), divisor));
        expect(IllegalArgumentException.class, DIVISOR_NEGATIVE, () -> new GlVertexAttributeBinding(Integer.MAX_VALUE, position(), divisor));
    }

    // Rejects a negative index before a negative divisor
    @Test
    void rejectsTheIndexBeforeTheDivisor() {
        expect(IllegalArgumentException.class, INDEX_NEGATIVE, () -> new GlVertexAttributeBinding(-1, position(), -1));
        expect(IllegalArgumentException.class, INDEX_NEGATIVE, () -> new GlVertexAttributeBinding(Integer.MIN_VALUE, position(), Integer.MIN_VALUE));
    }

    // Reads no accessor of the attribute when the index or divisor is rejected
    @Test
    void readsNoAccessorWhenAnArgumentIsRejected() {
        Reporting attribute = new Reporting();
        assertThrows(IllegalArgumentException.class, () -> new GlVertexAttributeBinding(-1, attribute));
        assertThrows(IllegalArgumentException.class, () -> new GlVertexAttributeBinding(-1, attribute, 0));
        assertThrows(IllegalArgumentException.class, () -> new GlVertexAttributeBinding(0, attribute, -1));
        assertEquals(0, attribute.reads);
    }

    // Reads every accessor of the attribute once per binding
    @Test
    void readsEveryAccessorOncePerBinding() {
        Reporting attribute = new Reporting();
        new GlVertexAttributeBinding(0, attribute, 0);
        assertEquals(8, attribute.reads);
        new GlVertexAttributeBinding(1, attribute);
        assertEquals(16, attribute.reads);
    }

    // Copies the layout that the accessors report instead of the fields the attribute was created with
    @Test
    void copiesTheLayoutThatTheAccessorsReport() {
        Reporting attribute = new Reporting();
        GlVertexAttributeBinding binding = new GlVertexAttributeBinding(2, attribute, 1);
        assertSame(GlVertexAttributeFormat.FLOAT, binding.getFormat());
        assertSame(attribute.name, binding.getName());
        assertEquals(12, binding.getSize());
        assertEquals(3, binding.getCount());
        assertEquals(4, binding.getPointer());
        assertEquals(16, binding.getStride());
        assertTrue(binding.isNormalized());
        assertTrue(binding.isIntType());
        assertEquals(2, binding.getIndex());
        assertEquals(1, binding.getDivisor());
    }

    // Rejects an invalid layout that the accessors report with the message of the attribute constructor
    @ParameterizedTest
    @MethodSource("invalidReports")
    void rejectsAnInvalidReportedLayout(Class<? extends RuntimeException> type, String message, Consumer<Reporting> change) {
        Reporting attribute = new Reporting();
        change.accept(attribute);
        expect(type, message, () -> new GlVertexAttributeBinding(0, attribute));
        expect(type, message, () -> new GlVertexAttributeBinding(Integer.MAX_VALUE, attribute, Integer.MAX_VALUE));
    }

    // Copies layouts that end at the int limit and a tightly packed layout with a zero stride
    @Test
    void copiesLayoutsAtTheLimits() {
        GlVertexAttribute bytes = new GlVertexAttribute(GlVertexAttributeFormat.BYTE, "b", 1, false, Integer.MAX_VALUE - 1, Integer.MAX_VALUE, false);
        GlVertexAttributeBinding lastByte = new GlVertexAttributeBinding(Integer.MAX_VALUE, bytes, Integer.MAX_VALUE);
        assertLayout(bytes, lastByte);
        assertEquals(Integer.MAX_VALUE, lastByte.getPointer() + lastByte.getSize());
        assertEquals(Integer.MAX_VALUE - 1L, (long) lastByte.getPointer());

        GlVertexAttribute floats = new GlVertexAttribute(GlVertexAttributeFormat.FLOAT, "f", 4, false, Integer.MAX_VALUE - 19, Integer.MAX_VALUE - 3, false);
        GlVertexAttributeBinding lastFloats = new GlVertexAttributeBinding(0, floats);
        assertLayout(floats, lastFloats);
        assertEquals(Integer.MAX_VALUE - 3, lastFloats.getPointer() + lastFloats.getSize());

        GlVertexAttribute packed = new GlVertexAttribute(GlVertexAttributeFormat.UNSIGNED_INT, "p", 1, false, 0, 0, true);
        GlVertexAttributeBinding tight = new GlVertexAttributeBinding(0, packed, 0);
        assertLayout(packed, tight);
        assertEquals(0, tight.getStride());
        assertEquals(0, tight.getPointer());
    }

    // Shares the name of the attribute for an empty and a single character and a long name
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 1 << 20})
    void sharesTheNameWithoutCopying(int length) {
        String name = "n".repeat(length);
        GlVertexAttribute source = new GlVertexAttribute(GlVertexAttributeFormat.FLOAT, name, 1, false, 0, 4, false);
        assertSame(name, new GlVertexAttributeBinding(0, source).getName());
        assertSame(name, new GlVertexAttributeBinding(0, source, 1).getName());
    }

    // Binds a binding with its own index and divisor and leaves the inner binding unchanged
    @Test
    void bindsABinding() {
        GlVertexAttributeBinding inner = new GlVertexAttributeBinding(3, uv(), 2);
        GlVertexAttributeBinding outer = new GlVertexAttributeBinding(5, inner);
        assertLayout(inner, outer);
        assertEquals(5, outer.getIndex());
        assertEquals(0, outer.getDivisor());
        assertEquals(3, inner.getIndex());
        assertEquals(2, inner.getDivisor());
    }

    // Rebinds a binding many times and keeps the original layout
    @Test
    void rebindsRepeatedlyWithoutDrift() {
        GlVertexAttribute source = uv();
        GlVertexAttributeBinding binding = new GlVertexAttributeBinding(0, source);
        for (int round = 1; round <= 10_000; round++) {
            binding = new GlVertexAttributeBinding(round, binding, round & 7);
            assertEquals(round, binding.getIndex());
            assertEquals(round & 7, binding.getDivisor());
        }
        assertLayout(source, binding);
    }

    // Returns the same values on repeated calls and creates a separate binding on every construction
    @Test
    void returnsTheSameValuesOnRepeatedCalls() {
        GlVertexAttribute source = uv();
        GlVertexAttributeBinding first = new GlVertexAttributeBinding(4, source, 1);
        for (int call = 0; call < 3; call++) {
            assertEquals(4, first.getIndex());
            assertEquals(1, first.getDivisor());
            assertLayout(source, first);
        }
        GlVertexAttributeBinding second = new GlVertexAttributeBinding(4, source, 1);
        assertNotSame(first, second);
        assertLayout(first, second);
        assertEquals(second.getIndex(), first.getIndex());
        assertEquals(second.getDivisor(), first.getDivisor());
        assertEquals("uv", source.getName());
        assertEquals(4, source.getSize());
        assertEquals(6, source.getPointer());
        assertEquals(18, source.getStride());
    }

    // Indexes every attribute of a format from the first index through the tessellation factory
    @Test
    void indexesEveryAttributeOfAFormat() {
        GlVertexFormat format = threeAttributes();
        GlVertexAttributeBinding[] bindings = TessellationBinding.forVertexBuffer(null, format, 7, 3).attributeBindings();
        assertEquals(3, bindings.length);
        Iterator<GlVertexAttribute> attributes = format.getAttributes().iterator();
        for (int position = 0; position < bindings.length; position++) {
            assertLayout(attributes.next(), bindings[position]);
            assertEquals(7 + position, bindings[position].getIndex());
            assertEquals(3, bindings[position].getDivisor());
        }
        GlVertexAttributeBinding[] top = TessellationBinding.forVertexBuffer(null, format, Integer.MAX_VALUE - 2, Integer.MAX_VALUE).attributeBindings();
        assertEquals(Integer.MAX_VALUE, top[2].getIndex());
        assertEquals(Integer.MAX_VALUE, top[2].getDivisor());
    }

    // Rejects a first index that overflows the int range or is negative and a negative divisor in the tessellation factory
    @Test
    void rejectsAnIndexThatOverflowsInTheTessellationFactory() {
        GlVertexFormat format = threeAttributes();
        expect(IllegalArgumentException.class, INDEX_NEGATIVE, () -> TessellationBinding.forVertexBuffer(null, format, Integer.MAX_VALUE - 1, 0));
        expect(IllegalArgumentException.class, INDEX_NEGATIVE, () -> TessellationBinding.forVertexBuffer(null, format, Integer.MAX_VALUE, 0));
        expect(IllegalArgumentException.class, INDEX_NEGATIVE, () -> TessellationBinding.forVertexBuffer(null, format, -1, 0));
        expect(IllegalArgumentException.class, DIVISOR_NEGATIVE, () -> TessellationBinding.forVertexBuffer(null, format, 0, -1));
    }

    // Declares two public constructors and two accessors and holds two private final int fields
    @Test
    void declaresAFixedPublicSurfaceAndImmutableState() throws ReflectiveOperationException {
        Class<GlVertexAttributeBinding> type = GlVertexAttributeBinding.class;
        assertSame(GlVertexAttribute.class, type.getSuperclass());
        assertFalse(AutoCloseable.class.isAssignableFrom(type));

        List<String> fields = new ArrayList<>();
        for (Field field : type.getDeclaredFields()) {
            if (field.isSynthetic()) {
                continue;
            }
            int modifiers = field.getModifiers();
            assertTrue(Modifier.isPrivate(modifiers) && Modifier.isFinal(modifiers) && !Modifier.isStatic(modifiers), field.getName());
            assertSame(int.class, field.getType());
            fields.add(field.getName());
        }
        fields.sort(null);
        assertEquals(List.of("divisor", "index"), fields);

        assertEquals(2, type.getConstructors().length);
        Constructor<GlVertexAttributeBinding> two = type.getConstructor(int.class, GlVertexAttribute.class);
        Constructor<GlVertexAttributeBinding> three = type.getConstructor(int.class, GlVertexAttribute.class, int.class);
        assertTrue(Modifier.isPublic(two.getModifiers()) && Modifier.isPublic(three.getModifiers()));

        List<String> methods = new ArrayList<>();
        for (Method method : type.getDeclaredMethods()) {
            if (method.isSynthetic() || !Modifier.isPublic(method.getModifiers())) {
                continue;
            }
            assertSame(int.class, method.getReturnType());
            assertEquals(0, method.getParameterCount());
            methods.add(method.getName());
        }
        methods.sort(null);
        assertEquals(List.of("getDivisor", "getIndex"), methods);
    }
}
