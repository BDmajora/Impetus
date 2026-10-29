package com.bdmajora.impetus.engine.impl.gl.shader.uniform;

import com.bdmajora.impetus.engine.impl.gl.shader.GlProgram;
import com.bdmajora.impetus.lwjgl.LWJGLService;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Tests of the float array uniform constructor and both uploads
class GlUniformFloatArrayTest {
    // Message of a location below minus one
    private static final String BAD_LOCATION = "Uniform location must not be less than -1";
    // Message of a null array
    private static final String NULL_VALUES = "Values must not be null";
    // Message of a null buffer
    private static final String NULL_BUFFER = "Buffer must not be null";
    // Message of a buffer outside native memory
    private static final String HEAP_BUFFER = "Buffer must be a direct buffer";
    // Message of a direct buffer in the foreign byte order
    private static final String FOREIGN_ORDER = "Buffer must use the native byte order";
    // Byte order that differs from the native byte order
    private static final ByteOrder FOREIGN = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
    // Float bit patterns at the edges of every float class including signaling NaNs
    private static final int[] BOUNDARY_BITS = {
            0x0000_0000, 0x8000_0000, 0x0000_0001, 0x8000_0001, 0x007F_FFFF, 0x807F_FFFF, 0x0080_0000, 0x8080_0000,
            0x3F80_0000, 0xBF80_0000, 0x4B80_0000, 0x4B80_0001, 0x7F7F_FFFF, 0xFF7F_FFFF, 0x7F80_0000, 0xFF80_0000,
            0x7FC0_0000, 0x7FC0_0001, 0x7FFF_FFFF, 0xFFC0_0000, 0xFFFF_FFFF, 0x7F80_0001, 0x7FA0_0000, 0x7FBF_FFFF,
            0xFF80_0001, 0xFFBF_FFFF};
    // Number of random locations in the sampled constructor test
    private static final int LOCATION_SAMPLES = 100_000;
    // Number of random uploads in each sampled upload test
    private static final int VALUE_SAMPLES = 2_000;
    // Longest array or buffer in the sampled upload tests
    private static final int MAX_SAMPLE_LENGTH = 300;
    // Seed of the sampled tests
    private static final long SEED = 0x5EED_F1A7L;

    // Shared GL mock that the reset extension clears before each test
    private final LWJGLService gl = TestGl.gl();
    // Uploads in the order GL received them
    private final List<Upload> uploads = new ArrayList<>();

    // Upload with its location and argument and the raw bits GL read and the thread stack state during the call
    private record Upload(int location, Object argument, int[] bits, long stackPointer, int stackFrame) {}

    // Array uniform that records every array it receives instead of calling GL
    private static final class Recording extends GlUniformFloatArray {
        // Arrays in the order the array upload received them
        private final List<float[]> values = new ArrayList<>();

        // Creates a recording uniform at the location
        private Recording(int index) {
            super(index);
        }

        // Records the array
        @Override
        public void set(float[] value) {
            this.values.add(value);
        }
    }

    // Records every upload of both overloads together with what GL reads during the call
    @BeforeEach
    void recordUploads() {
        Mockito.doAnswer(this::recordArray).when(this.gl).glUniform1fv(Mockito.anyInt(), Mockito.any(float[].class));
        Mockito.doAnswer(this::recordBuffer).when(this.gl).glUniform1fv(Mockito.anyInt(), Mockito.any(FloatBuffer.class));
    }

    // Records an array upload with the bits of every element
    private Object recordArray(InvocationOnMock invocation) {
        int location = invocation.getArgument(0);
        float[] value = invocation.getArgument(1);
        this.record(location, value, bitsOf(value));

        return null;
    }

    // Records a buffer upload with the bits that native memory holds between the position and the limit
    private Object recordBuffer(InvocationOnMock invocation) {
        int location = invocation.getArgument(0);
        FloatBuffer value = invocation.getArgument(1);
        this.record(location, value, value.isDirect() ? nativeBits(value) : null);

        return null;
    }

    // Stores the upload with the thread stack state at the time of the call
    private void record(int location, Object argument, int[] bits) {
        MemoryStack stack = MemoryStack.stackGet();
        this.uploads.add(new Upload(location, argument, bits, stack.getPointerAddress(), stack.getFrameIndex()));
    }

    // Returns the uploads after checking that GL received exactly the counted calls of each overload and nothing else
    private List<Upload> uploads(int arrays, int buffers) {
        Mockito.verify(this.gl, Mockito.times(arrays)).glUniform1fv(Mockito.anyInt(), Mockito.any(float[].class));
        Mockito.verify(this.gl, Mockito.times(buffers)).glUniform1fv(Mockito.anyInt(), Mockito.any(FloatBuffer.class));
        Mockito.verifyNoMoreInteractions(this.gl);
        assertEquals(arrays + buffers, this.uploads.size());

        return this.uploads;
    }

    // Returns the raw bits of every element between the position and the limit as GL reads them from native memory
    private static int[] nativeBits(FloatBuffer buffer) {
        long address = MemoryUtil.memAddress(buffer);
        int[] bits = new int[buffer.remaining()];

        for (int i = 0; i < bits.length; i++) {
            bits[i] = MemoryUtil.memGetInt(address + (long) Float.BYTES * i);
        }

        return bits;
    }

    // Returns the raw bits of each value
    private static int[] bitsOf(float... values) {
        int[] bits = new int[values.length];

        for (int i = 0; i < values.length; i++) {
            bits[i] = Float.floatToRawIntBits(values[i]);
        }

        return bits;
    }

    // Returns the floats that hold the raw bits
    private static float[] floatsOf(int... bits) {
        float[] values = new float[bits.length];

        for (int i = 0; i < bits.length; i++) {
            values[i] = Float.intBitsToFloat(bits[i]);
        }

        return values;
    }

    // Returns distinct values with the length
    private static float[] sequence(int length) {
        float[] values = new float[length];

        for (int i = 0; i < length; i++) {
            values[i] = i * 0.5f - 3f;
        }

        return values;
    }

    // Returns a direct buffer in the native byte order that holds the raw bits
    private static FloatBuffer directBits(int... bits) {
        ByteBuffer bytes = ByteBuffer.allocateDirect(Float.BYTES * bits.length).order(ByteOrder.nativeOrder());

        for (int i = 0; i < bits.length; i++) {
            bytes.putInt(Float.BYTES * i, bits[i]);
        }

        return bytes.asFloatBuffer();
    }

    // Returns a direct buffer in the native byte order that holds the values
    private static FloatBuffer directOf(float... values) {
        return directBits(bitsOf(values));
    }

    // Stores every accepted location exactly without a GL call
    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1, 2, 1023, 1024, 65535, 65536, Integer.MAX_VALUE - 1, Integer.MAX_VALUE})
    void constructorStoresEveryAcceptedLocation(int location) {
        assertEquals(location, new GlUniformFloatArray(location).index);
        Mockito.verifyNoInteractions(this.gl);
    }

    // Rejects every location below minus one with the same message and without a GL call
    @ParameterizedTest
    @ValueSource(ints = {-2, -3, -1024, -65536, Integer.MIN_VALUE + 2, Integer.MIN_VALUE + 1, Integer.MIN_VALUE})
    void constructorRejectsEveryLocationBelowMinusOne(int location) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> new GlUniformFloatArray(location));
        assertEquals(BAD_LOCATION, error.getMessage());
        assertNull(error.getCause());
        Mockito.verifyNoInteractions(this.gl);
    }

    // Accepts a location exactly when it is at least minus one over random samples of the whole int range
    @Test
    void constructorAcceptsExactlyTheLocationsFromMinusOneUp() {
        SplittableRandom random = new SplittableRandom(SEED);

        for (int i = 0; i < LOCATION_SAMPLES; i++) {
            int location = random.nextInt();

            if (location >= -1) {
                assertEquals(location, new GlUniformFloatArray(location).index);
            } else {
                assertThrows(IllegalArgumentException.class, () -> new GlUniformFloatArray(location));
            }
        }

        Mockito.verifyNoInteractions(this.gl);
    }

    // Skips the subclass constructor body when the location is rejected
    @Test
    void rejectedConstructionSkipsTheSubclassBody() {
        List<Integer> bodies = new ArrayList<>();

        // Array uniform that records when its own constructor body runs
        final class Tracked extends GlUniformFloatArray {
            // Creates a tracked uniform and records its location
            Tracked(int index) {
                super(index);
                bodies.add(index);
            }
        }

        assertThrows(IllegalArgumentException.class, () -> new Tracked(-2));
        assertTrue(bodies.isEmpty());
        new Tracked(-1);
        assertEquals(List.of(-1), bodies);
    }

    // Creates independent instances for the same location and recovers after a rejection
    @Test
    void repeatedConstructionCreatesIndependentInstances() {
        assertThrows(IllegalArgumentException.class, () -> new GlUniformFloatArray(-2));
        GlUniformFloatArray first = new GlUniformFloatArray(7);
        GlUniformFloatArray second = new GlUniformFloatArray(7);
        assertNotSame(first, second);
        assertEquals(first.index, second.index);
        first.set(new float[]{1f, 2f});
        second.set(new float[]{3f});
        List<Upload> sent = uploads(2, 0);
        assertArrayEquals(bitsOf(1f, 2f), sent.get(0).bits());
        assertArrayEquals(bitsOf(3f), sent.get(1).bits());
        assertEquals(7, sent.get(0).location());
        assertEquals(7, sent.get(1).location());
    }

    // Rejects a null array with a message before any GL call and stays usable
    @Test
    void setArrayRejectsNullBeforeAnyGlCall() {
        GlUniformFloatArray uniform = new GlUniformFloatArray(4);

        for (int i = 0; i < 3; i++) {
            NullPointerException error = assertThrows(NullPointerException.class, () -> uniform.set((float[]) null));
            assertEquals(NULL_VALUES, error.getMessage());
        }

        Mockito.verifyNoInteractions(this.gl);
        float[] value = {1f, 2f};
        uniform.set(value);
        assertSame(value, uploads(1, 0).get(0).argument());
    }

    // Uploads every element of an array of each length in one call that receives the array itself
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 7, 8, 15, 16, 17, 255, 256, 257, 1023, 1024, 4095, 4096})
    void setArrayUploadsEveryElement(int length) {
        float[] value = sequence(length);
        int[] expected = bitsOf(value);
        new GlUniformFloatArray(3).set(value);
        Upload upload = uploads(1, 0).get(0);
        assertEquals(3, upload.location());
        assertSame(value, upload.argument());
        assertArrayEquals(expected, upload.bits());
    }

    // Uploads arrays around and past the thread stack capacity in one call each that receives the array itself
    @Test
    void setArrayUploadsArraysLongerThanTheThreadStack() {
        int capacity = MemoryStack.stackGet().getSize() / Float.BYTES;
        int[] lengths = {capacity - 1, capacity, capacity + 1, 4 * capacity, 1 << 21};
        GlUniformFloatArray uniform = new GlUniformFloatArray(11);
        List<float[]> values = new ArrayList<>();
        List<int[]> expected = new ArrayList<>();

        for (int length : lengths) {
            float[] value = sequence(length);
            values.add(value);
            expected.add(bitsOf(value));
            uniform.set(value);
        }

        List<Upload> sent = uploads(lengths.length, 0);

        for (int i = 0; i < lengths.length; i++) {
            assertSame(values.get(i), sent.get(i).argument());
            assertArrayEquals(expected.get(i), sent.get(i).bits());
        }
    }

    // Leaves the thread stack untouched before and during and after the array upload
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 64, 4096})
    void setArrayUsesNoThreadStackMemory(int length) {
        MemoryStack stack = MemoryStack.stackGet();
        long pointer = stack.getPointerAddress();
        int frame = stack.getFrameIndex();
        float[] value = new float[length];
        new GlUniformFloatArray(1).set(value);
        Upload upload = uploads(1, 0).get(0);
        assertSame(value, upload.argument());
        assertEquals(pointer, upload.stackPointer());
        assertEquals(frame, upload.stackFrame());
        assertEquals(pointer, stack.getPointerAddress());
        assertEquals(frame, stack.getFrameIndex());
    }

    // Leaves the array contents untouched
    @Test
    void setArrayNeverWritesTheArray() {
        float[] value = floatsOf(BOUNDARY_BITS);
        int[] before = bitsOf(value);
        GlUniformFloatArray uniform = new GlUniformFloatArray(1);
        uniform.set(value);
        uniform.set(value);
        assertArrayEquals(before, bitsOf(value));
    }

    // Hands GL the exact bits of every boundary pattern in every element position
    @Test
    void setArrayForwardsBoundaryBitsExactly() {
        GlUniformFloatArray uniform = new GlUniformFloatArray(2);
        List<int[]> expected = new ArrayList<>();

        for (int bits : BOUNDARY_BITS) {
            for (int position = 0; position < 4; position++) {
                float[] value = {0f, 1f, -1f, 2f};
                value[position] = Float.intBitsToFloat(bits);
                expected.add(bitsOf(value));
                uniform.set(value);
            }
        }

        List<Upload> sent = uploads(expected.size(), 0);

        for (int i = 0; i < expected.size(); i++) {
            assertArrayEquals(expected.get(i), sent.get(i).bits());
        }
    }

    // Hands GL random arrays of random lengths with their exact bits
    @Test
    void setArrayForwardsRandomArraysExactly() {
        SplittableRandom random = new SplittableRandom(SEED);
        GlUniformFloatArray uniform = new GlUniformFloatArray(9);
        List<int[]> expected = new ArrayList<>();

        for (int i = 0; i < VALUE_SAMPLES; i++) {
            float[] value = new float[random.nextInt(MAX_SAMPLE_LENGTH + 1)];

            for (int e = 0; e < value.length; e++) {
                value[e] = Float.intBitsToFloat(random.nextInt());
            }

            expected.add(bitsOf(value));
            uniform.set(value);
        }

        List<Upload> sent = uploads(VALUE_SAMPLES, 0);

        for (int i = 0; i < VALUE_SAMPLES; i++) {
            assertArrayEquals(expected.get(i), sent.get(i).bits());
        }
    }

    // Hands GL the int and long limits as the nearest float and overflow as infinity and underflow as zero
    @Test
    void numericLimitsArriveAsTheConvertedFloat() {
        float[] value = {
                Integer.MAX_VALUE, Integer.MIN_VALUE, Long.MAX_VALUE, Long.MIN_VALUE,
                (float) Double.MAX_VALUE, (float) -Double.MAX_VALUE, (float) Double.MIN_VALUE, (float) -Double.MIN_VALUE,
                Float.MAX_VALUE * 2f, -Float.MAX_VALUE * 2f, Float.MIN_VALUE / 2f, Float.MAX_VALUE,
                Integer.MAX_VALUE + 1, Long.MAX_VALUE + 1, (1 << 24) + 1, Float.MIN_NORMAL};
        int[] expected = {
                0x4F00_0000, 0xCF00_0000, 0x5F00_0000, 0xDF00_0000,
                0x7F80_0000, 0xFF80_0000, 0x0000_0000, 0x8000_0000,
                0x7F80_0000, 0xFF80_0000, 0x0000_0000, 0x7F7F_FFFF,
                0xCF00_0000, 0xDF00_0000, 0x4B80_0000, 0x0080_0000};
        GlUniformFloatArray uniform = new GlUniformFloatArray(0);
        uniform.set(value);
        uniform.set(directOf(value));
        List<Upload> sent = uploads(1, 1);
        assertArrayEquals(expected, sent.get(0).bits());
        assertArrayEquals(expected, sent.get(1).bits());
    }

    // Hands GL the current contents on every call with the same array
    @Test
    void reusedArraySendsItsCurrentContents() {
        GlUniformFloatArray uniform = new GlUniformFloatArray(8);
        float[] value = {1f, 2f, 3f};
        uniform.set(value);
        value[1] = -2f;
        uniform.set(value);
        uniform.set(value);
        value[0] = 10f;
        value[2] = 30f;
        uniform.set(value);
        Arrays.fill(value, 99f);
        List<Upload> sent = uploads(4, 0);
        assertArrayEquals(bitsOf(1f, 2f, 3f), sent.get(0).bits());
        assertArrayEquals(bitsOf(1f, -2f, 3f), sent.get(1).bits());
        assertArrayEquals(bitsOf(1f, -2f, 3f), sent.get(2).bits());
        assertArrayEquals(bitsOf(10f, -2f, 30f), sent.get(3).bits());
    }

    // Sends every repeated array and buffer again in call order at the int limit location
    @Test
    void repeatedCallsSendEveryValue() {
        GlUniformFloatArray uniform = new GlUniformFloatArray(Integer.MAX_VALUE);
        List<int[]> expected = new ArrayList<>();

        for (int i = 0; i < 1000; i++) {
            float[] value = {i, -i};
            expected.add(bitsOf(value));
            uniform.set(value);
        }

        FloatBuffer shared = directOf(7f, 8f);
        uniform.set(shared);
        uniform.set(shared);
        expected.add(bitsOf(7f, 8f));
        expected.add(bitsOf(7f, 8f));
        List<Upload> sent = uploads(1000, 2);

        for (int i = 0; i < expected.size(); i++) {
            assertEquals(Integer.MAX_VALUE, sent.get(i).location());
            assertArrayEquals(expected.get(i), sent.get(i).bits());
        }
    }

    // Keeps each instance on its own location when array and buffer uploads interleave
    @Test
    void interleavedInstancesKeepTheirLocations() {
        GlUniformFloatArray first = new GlUniformFloatArray(7);
        GlUniformFloatArray second = new GlUniformFloatArray(7);
        GlUniformFloatArray other = new GlUniformFloatArray(8);
        float[] one = {1f};
        FloatBuffer two = directOf(2f, 3f);
        float[] three = {4f, 5f, 6f};
        FloatBuffer four = directOf(7f);
        first.set(one);
        other.set(two);
        second.set(three);
        first.set(four);
        other.set(one);

        InOrder order = Mockito.inOrder(this.gl);
        order.verify(this.gl).glUniform1fv(Mockito.eq(7), Mockito.same(one));
        order.verify(this.gl).glUniform1fv(Mockito.eq(8), Mockito.same(two));
        order.verify(this.gl).glUniform1fv(Mockito.eq(7), Mockito.same(three));
        order.verify(this.gl).glUniform1fv(Mockito.eq(7), Mockito.same(four));
        order.verify(this.gl).glUniform1fv(Mockito.eq(8), Mockito.same(one));
        Mockito.verifyNoMoreInteractions(this.gl);
    }

    // Forwards minus one to GL from both uploads
    @Test
    void minusOneLocationStillReachesGl() {
        GlUniformFloatArray uniform = new GlUniformFloatArray(-1);
        float[] value = {3f, 4f};
        FloatBuffer buffer = directOf(5f);
        uniform.set(value);
        uniform.set(buffer);
        Mockito.verify(this.gl).glUniform1fv(Mockito.eq(-1), Mockito.same(value));
        Mockito.verify(this.gl).glUniform1fv(Mockito.eq(-1), Mockito.same(buffer));
        Mockito.verifyNoMoreInteractions(this.gl);
    }

    // Passes a GL failure on the array upload through unchanged and keeps the thread stack and the uniform intact
    @Test
    void arrayGlFailurePassesThrough() {
        IllegalStateException failure = new IllegalStateException("lost context");
        Mockito.doThrow(failure).doThrow(failure).doAnswer(this::recordArray).when(this.gl)
                .glUniform1fv(Mockito.anyInt(), Mockito.any(float[].class));
        MemoryStack stack = MemoryStack.stackGet();
        long pointer = stack.getPointerAddress();
        int frame = stack.getFrameIndex();
        GlUniformFloatArray uniform = new GlUniformFloatArray(5);
        float[] value = {1f, 2f, 3f};
        assertSame(failure, assertThrows(IllegalStateException.class, () -> uniform.set(value)));
        assertSame(failure, assertThrows(IllegalStateException.class, () -> uniform.set(value)));
        assertEquals(pointer, stack.getPointerAddress());
        assertEquals(frame, stack.getFrameIndex());
        uniform.set(value);
        Mockito.verify(this.gl, Mockito.times(3)).glUniform1fv(Mockito.eq(5), Mockito.same(value));
        Mockito.verifyNoMoreInteractions(this.gl);
        assertEquals(1, this.uploads.size());
        assertArrayEquals(bitsOf(1f, 2f, 3f), this.uploads.get(0).bits());
        assertArrayEquals(bitsOf(1f, 2f, 3f), bitsOf(value));
    }

    // Passes a GL failure on the buffer upload through unchanged and keeps the buffer and the uniform intact
    @Test
    void bufferGlFailurePassesThrough() {
        IllegalStateException failure = new IllegalStateException("lost context");
        Mockito.doThrow(failure).doThrow(failure).doAnswer(this::recordBuffer).when(this.gl)
                .glUniform1fv(Mockito.anyInt(), Mockito.any(FloatBuffer.class));
        GlUniformFloatArray uniform = new GlUniformFloatArray(5);
        FloatBuffer buffer = directOf(1f, 2f, 3f, 4f);
        buffer.position(1).limit(3);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> uniform.set(buffer)));
        assertSame(failure, assertThrows(IllegalStateException.class, () -> uniform.set(buffer)));
        assertEquals(1, buffer.position());
        assertEquals(3, buffer.limit());
        uniform.set(buffer);
        Mockito.verify(this.gl, Mockito.times(3)).glUniform1fv(Mockito.eq(5), Mockito.same(buffer));
        Mockito.verifyNoMoreInteractions(this.gl);
        assertEquals(1, this.uploads.size());
        assertArrayEquals(bitsOf(2f, 3f), this.uploads.get(0).bits());
    }

    // Passes a GL error through unchanged from both uploads
    @Test
    void glErrorPassesThrough() {
        StackOverflowError failure = new StackOverflowError();
        Mockito.doThrow(failure).when(this.gl).glUniform1fv(Mockito.anyInt(), Mockito.any(float[].class));
        Mockito.doThrow(failure).when(this.gl).glUniform1fv(Mockito.anyInt(), Mockito.any(FloatBuffer.class));
        GlUniformFloatArray uniform = new GlUniformFloatArray(0);
        assertSame(failure, assertThrows(StackOverflowError.class, () -> uniform.set(new float[4])));
        assertSame(failure, assertThrows(StackOverflowError.class, () -> uniform.set(directOf(1f))));
    }

    // Rejects a value of another type through the raw base type before any GL call
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void rawSetRejectsOtherTypesBeforeAnyGlCall() {
        GlUniform raw = new GlUniformFloatArray(1);
        assertThrows(ClassCastException.class, () -> raw.set(1f));
        assertThrows(ClassCastException.class, () -> raw.set(new double[]{1, 2}));
        assertThrows(ClassCastException.class, () -> raw.set(new int[]{1, 2}));
        assertThrows(ClassCastException.class, () -> raw.set(new Float[]{1f, 2f}));
        assertThrows(ClassCastException.class, () -> raw.set(new float[][]{{1f, 2f}}));
        assertThrows(ClassCastException.class, () -> raw.set(directOf(1f, 2f)));
        assertThrows(ClassCastException.class, () -> raw.set(FloatBuffer.wrap(new float[]{1f, 2f})));
        assertThrows(ClassCastException.class, () -> raw.set("1 2"));
        assertEquals(NULL_VALUES, assertThrows(NullPointerException.class, () -> raw.set(null)).getMessage());
        Mockito.verifyNoInteractions(this.gl);
        float[] value = {1f, 2f};
        raw.set(value);
        assertSame(value, uploads(1, 0).get(0).argument());
    }

    // Dispatches through the base type to the array upload
    @Test
    void setThroughTheBaseTypeReachesTheArrayUpload() {
        GlUniform<float[]> uniform = new GlUniformFloatArray(6);
        float[] value = {0.25f, 0.5f};
        uniform.set(value);
        Upload upload = uploads(1, 0).get(0);
        assertEquals(6, upload.location());
        assertSame(value, upload.argument());
    }

    // Routes a call through the base type to an override of the array upload and leaves the buffer upload on GL
    @Test
    void arrayUploadIsOverridable() {
        Recording uniform = new Recording(3);
        GlUniform<float[]> base = uniform;
        float[] value = {2.5f, -0f, Float.NaN};
        base.set(value);
        uniform.set((float[]) null);
        assertEquals(2, uniform.values.size());
        assertSame(value, uniform.values.get(0));
        assertNull(uniform.values.get(1));
        Mockito.verifyNoInteractions(this.gl);
        FloatBuffer buffer = directOf(1f);
        uniform.set(buffer);
        assertSame(buffer, uploads(0, 1).get(0).argument());
    }

    // Rejects a null buffer with a message before any GL call and stays usable
    @Test
    void setBufferRejectsNullBeforeAnyGlCall() {
        GlUniformFloatArray uniform = new GlUniformFloatArray(4);

        for (int i = 0; i < 3; i++) {
            NullPointerException error = assertThrows(NullPointerException.class, () -> uniform.set((FloatBuffer) null));
            assertEquals(NULL_BUFFER, error.getMessage());
        }

        Mockito.verifyNoInteractions(this.gl);
        FloatBuffer buffer = directOf(1f, 2f);
        uniform.set(buffer);
        assertSame(buffer, uploads(0, 1).get(0).argument());
    }

    // Rejects every buffer outside native memory with a message before any GL call and leaves it untouched
    @Test
    void setBufferRejectsHeapBuffers() {
        List<FloatBuffer> heap = List.of(
                FloatBuffer.allocate(0),
                FloatBuffer.allocate(4),
                FloatBuffer.wrap(new float[]{1f, 2f, 3f}),
                FloatBuffer.wrap(new float[8], 2, 3),
                FloatBuffer.allocate(8).slice(2, 4),
                FloatBuffer.allocate(4).asReadOnlyBuffer(),
                ByteBuffer.allocate(16).asFloatBuffer(),
                ByteBuffer.allocate(16).order(ByteOrder.nativeOrder()).asFloatBuffer(),
                ByteBuffer.allocate(16).order(FOREIGN).asFloatBuffer(),
                ByteBuffer.allocate(17).slice(1, 16).order(ByteOrder.nativeOrder()).asFloatBuffer());
        GlUniformFloatArray uniform = new GlUniformFloatArray(2);

        for (FloatBuffer buffer : heap) {
            int position = buffer.position();
            int limit = buffer.limit();
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> uniform.set(buffer));
            assertEquals(HEAP_BUFFER, error.getMessage());
            assertNull(error.getCause());
            assertEquals(position, buffer.position());
            assertEquals(limit, buffer.limit());
        }

        Mockito.verifyNoInteractions(this.gl);
    }

    // Rejects every direct buffer in the foreign byte order with a message before any GL call
    @Test
    void setBufferRejectsTheForeignByteOrder() {
        ByteBuffer bytes = ByteBuffer.allocateDirect(64).order(FOREIGN);
        List<FloatBuffer> foreign = List.of(
                bytes.asFloatBuffer(),
                ByteBuffer.allocateDirect(0).order(FOREIGN).asFloatBuffer(),
                bytes.asFloatBuffer().asReadOnlyBuffer(),
                bytes.asFloatBuffer().slice(3, 5),
                bytes.asFloatBuffer().duplicate(),
                bytes.slice(1, 32).order(FOREIGN).asFloatBuffer());
        GlUniformFloatArray uniform = new GlUniformFloatArray(2);

        for (FloatBuffer buffer : foreign) {
            assertTrue(buffer.isDirect());
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> uniform.set(buffer));
            assertEquals(FOREIGN_ORDER, error.getMessage());
            assertNull(error.getCause());
        }

        Mockito.verifyNoInteractions(this.gl);
        FloatBuffer buffer = directOf(1f);
        uniform.set(buffer);
        assertSame(buffer, uploads(0, 1).get(0).argument());
    }

    // Uploads every element of a direct buffer of each length in one call that receives the buffer itself
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 7, 16, 255, 256, 257, 4096, 16383, 16384, 16385, 1 << 20})
    void setBufferUploadsEveryRemainingElement(int length) {
        int[] bits = bitsOf(sequence(length));
        FloatBuffer buffer = directBits(bits);
        new GlUniformFloatArray(3).set(buffer);
        Upload upload = uploads(0, 1).get(0);
        assertEquals(3, upload.location());
        assertSame(buffer, upload.argument());
        assertArrayEquals(bits, upload.bits());
        assertEquals(0, buffer.position());
        assertEquals(length, buffer.limit());
    }

    // Hands GL the exact bits of every boundary pattern from native memory
    @Test
    void setBufferForwardsBoundaryBitsExactly() {
        FloatBuffer buffer = directBits(BOUNDARY_BITS);
        new GlUniformFloatArray(2).set(buffer);
        assertArrayEquals(BOUNDARY_BITS, uploads(0, 1).get(0).bits());
    }

    // Reads only the elements between the position and the limit and leaves the position and the limit and the mark untouched
    @Test
    void setBufferReadsBetweenThePositionAndTheLimit() {
        FloatBuffer buffer = directOf(sequence(10));
        buffer.position(2).mark();
        buffer.position(3).limit(7);
        new GlUniformFloatArray(1).set(buffer);
        assertArrayEquals(Arrays.copyOfRange(bitsOf(sequence(10)), 3, 7), uploads(0, 1).get(0).bits());
        assertEquals(3, buffer.position());
        assertEquals(7, buffer.limit());
        assertEquals(10, buffer.capacity());
        assertEquals(2, buffer.reset().position());
    }

    // Hands GL random windows of random direct buffers with their exact bits
    @Test
    void setBufferForwardsRandomWindowsExactly() {
        SplittableRandom random = new SplittableRandom(SEED);
        GlUniformFloatArray uniform = new GlUniformFloatArray(9);
        List<int[]> expected = new ArrayList<>();

        for (int i = 0; i < VALUE_SAMPLES; i++) {
            int[] bits = new int[random.nextInt(MAX_SAMPLE_LENGTH + 1)];

            for (int e = 0; e < bits.length; e++) {
                bits[e] = random.nextInt();
            }

            int limit = random.nextInt(bits.length + 1);
            int position = random.nextInt(limit + 1);
            FloatBuffer buffer = directBits(bits);
            buffer.limit(limit).position(position);
            expected.add(Arrays.copyOfRange(bits, position, limit));
            uniform.set(buffer);
        }

        List<Upload> sent = uploads(0, VALUE_SAMPLES);

        for (int i = 0; i < VALUE_SAMPLES; i++) {
            assertArrayEquals(expected.get(i), sent.get(i).bits());
        }
    }

    // Uploads no elements from a buffer whose position equals its limit
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 5, 8})
    void setBufferWithNothingRemainingUploadsNoElements(int position) {
        FloatBuffer buffer = directOf(sequence(8));
        buffer.limit(position).position(position);
        new GlUniformFloatArray(1).set(buffer);
        Upload upload = uploads(0, 1).get(0);
        assertSame(buffer, upload.argument());
        assertEquals(0, upload.bits().length);
    }

    // Accepts a read only direct buffer and reads its elements
    @Test
    void setBufferAcceptsReadOnlyDirectBuffers() {
        FloatBuffer buffer = directOf(1f, -2f, 3f).asReadOnlyBuffer();
        assertTrue(buffer.isReadOnly());
        new GlUniformFloatArray(1).set(buffer);
        Upload upload = uploads(0, 1).get(0);
        assertSame(buffer, upload.argument());
        assertArrayEquals(bitsOf(1f, -2f, 3f), upload.bits());
    }

    // Accepts direct views at every byte offset and reads their exact bits from the misaligned address
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7, 8, 63})
    void setBufferAcceptsDirectViewsAtEveryByteOffset(int offset) {
        int[] bits = bitsOf(sequence(6));
        ByteBuffer bytes = ByteBuffer.allocateDirect(offset + Float.BYTES * bits.length).order(ByteOrder.nativeOrder());

        for (int i = 0; i < bits.length; i++) {
            bytes.putInt(offset + Float.BYTES * i, bits[i]);
        }

        FloatBuffer view = bytes.slice(offset, Float.BYTES * bits.length).order(ByteOrder.nativeOrder()).asFloatBuffer();
        assertEquals(offset, MemoryUtil.memAddress(view) - MemoryUtil.memAddress(bytes));
        new GlUniformFloatArray(1).set(view);
        Upload upload = uploads(0, 1).get(0);
        assertSame(view, upload.argument());
        assertArrayEquals(bits, upload.bits());
    }

    // Accepts slices and duplicates of a direct buffer and reads from their own position
    @Test
    void setBufferAcceptsSlicesAndDuplicates() {
        FloatBuffer buffer = directOf(sequence(12));
        FloatBuffer slice = buffer.slice(4, 5);
        FloatBuffer duplicate = buffer.duplicate().position(9);
        GlUniformFloatArray uniform = new GlUniformFloatArray(1);
        uniform.set(slice);
        uniform.set(duplicate);
        List<Upload> sent = uploads(0, 2);
        assertArrayEquals(Arrays.copyOfRange(bitsOf(sequence(12)), 4, 9), sent.get(0).bits());
        assertArrayEquals(Arrays.copyOfRange(bitsOf(sequence(12)), 9, 12), sent.get(1).bits());
        assertEquals(0, buffer.position());
    }

    // Accepts buffers from the LWJGL allocator and the thread stack that are freed right after the call
    @Test
    void setBufferAcceptsLwjglAllocationsFreedAfterTheCall() {
        GlUniformFloatArray uniform = new GlUniformFloatArray(4);
        FloatBuffer allocated = MemoryUtil.memAllocFloat(5);

        try {
            allocated.put(0, sequence(5));
            uniform.set(allocated);
        } finally {
            MemoryUtil.memFree(allocated);
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer onStack = stack.mallocFloat(3);
            onStack.put(0, new float[]{9f, 8f, 7f});
            uniform.set(onStack);
        }

        FloatBuffer after = directOf(6f);
        uniform.set(after);
        List<Upload> sent = uploads(0, 3);
        assertArrayEquals(bitsOf(sequence(5)), sent.get(0).bits());
        assertArrayEquals(bitsOf(9f, 8f, 7f), sent.get(1).bits());
        assertArrayEquals(bitsOf(6f), sent.get(2).bits());
        assertSame(after, sent.get(2).argument());
    }

    // Hands GL the current contents on every call with the same buffer
    @Test
    void reusedBufferSendsItsCurrentContents() {
        GlUniformFloatArray uniform = new GlUniformFloatArray(8);
        FloatBuffer buffer = directOf(1f, 2f, 3f);
        uniform.set(buffer);
        buffer.put(1, -2f);
        uniform.set(buffer);
        buffer.position(1);
        uniform.set(buffer);
        List<Upload> sent = uploads(0, 3);
        assertArrayEquals(bitsOf(1f, 2f, 3f), sent.get(0).bits());
        assertArrayEquals(bitsOf(1f, -2f, 3f), sent.get(1).bits());
        assertArrayEquals(bitsOf(-2f, 3f), sent.get(2).bits());
    }

    // Wraps the location GL reports through the program binding
    @ParameterizedTest
    @ValueSource(ints = {0, 1, Integer.MAX_VALUE})
    void programBindingWrapsTheReportedLocation(int location) {
        Mockito.doReturn(location).when(this.gl).glGetUniformLocation(Mockito.anyInt(), Mockito.eq("u_Ages"));
        GlProgram<GlUniformFloatArray> program = GlProgram.builder("uniform").link(context -> context.bindUniform("u_Ages", GlUniformFloatArray::new));
        GlUniformFloatArray uniform = program.getInterface();
        assertEquals(location, uniform.index);
        Mockito.clearInvocations(this.gl);
        float[] value = {0.75f, 1.75f};
        uniform.set(value);
        Upload upload = uploads(1, 0).get(0);
        assertEquals(location, upload.location());
        assertSame(value, upload.argument());
    }

    // Creates no uniform when the program lacks it
    @ParameterizedTest
    @ValueSource(ints = {-1, -2, Integer.MIN_VALUE})
    void missingUniformCreatesNoInstance(int location) {
        Mockito.doReturn(location).when(this.gl).glGetUniformLocation(Mockito.anyInt(), Mockito.eq("u_Ages"));
        AtomicInteger created = new AtomicInteger();
        IntFunction<GlUniformFloatArray> factory = index -> {
            created.incrementAndGet();

            return new GlUniformFloatArray(index);
        };

        GlProgram<GlUniformFloatArray> program = GlProgram.builder("uniform").link(context -> context.bindUniformIfPresent("u_Ages", factory));
        assertNull(program.getInterface());
        assertEquals(0, created.get());
        assertTrue(this.uploads.isEmpty());
    }

    // Keeps forwarding the location after the program is deleted
    @Test
    void uniformOutlivesItsProgram() {
        Mockito.doReturn(5).when(this.gl).glGetUniformLocation(Mockito.anyInt(), Mockito.eq("u_Ages"));
        GlProgram<GlUniformFloatArray> program = GlProgram.builder("uniform").link(context -> context.bindUniform("u_Ages", GlUniformFloatArray::new));
        GlUniformFloatArray uniform = program.getInterface();
        program.delete();
        Mockito.clearInvocations(this.gl);
        uniform.set(new float[]{8f, 9f});
        uniform.set(directOf(10f));
        List<Upload> sent = uploads(1, 1);
        assertEquals(5, sent.get(0).location());
        assertEquals(5, sent.get(1).location());
        assertArrayEquals(bitsOf(8f, 9f), sent.get(0).bits());
        assertArrayEquals(bitsOf(10f), sent.get(1).bits());
    }

    // Keeps the class public and concrete and open to subclasses over a float array type argument
    @Test
    void classShape() {
        int modifiers = GlUniformFloatArray.class.getModifiers();
        assertTrue(Modifier.isPublic(modifiers));
        assertFalse(Modifier.isAbstract(modifiers));
        assertFalse(Modifier.isFinal(modifiers));
        ParameterizedType type = (ParameterizedType) GlUniformFloatArray.class.getGenericSuperclass();
        assertSame(GlUniform.class, type.getRawType());
        assertArrayEquals(new Type[]{float[].class}, type.getActualTypeArguments());
        assertEquals(0, GlUniformFloatArray.class.getInterfaces().length);
    }

    // Keeps one public constructor that takes the location
    @Test
    void constructorShape() {
        Constructor<?>[] constructors = GlUniformFloatArray.class.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        assertTrue(Modifier.isPublic(constructors[0].getModifiers()));
        assertArrayEquals(new Class<?>[]{int.class}, constructors[0].getParameterTypes());
    }

    // Keeps no fields of its own
    @Test
    void fieldShape() {
        Field[] fields = Arrays.stream(GlUniformFloatArray.class.getDeclaredFields()).filter(field -> !field.isSynthetic()).toArray(Field[]::new);
        assertEquals(0, fields.length);
    }

    // Keeps the two uploads as the only methods and keeps both public and void and open to overrides
    @Test
    void methodShape() throws NoSuchMethodException {
        List<Method> methods = Arrays.stream(GlUniformFloatArray.class.getDeclaredMethods()).filter(method -> !method.isSynthetic()).toList();
        assertEquals(2, methods.size());

        for (Class<?> parameter : new Class<?>[]{float[].class, FloatBuffer.class}) {
            Method set = GlUniformFloatArray.class.getDeclaredMethod("set", parameter);
            assertTrue(methods.contains(set));
            int modifiers = set.getModifiers();
            assertTrue(Modifier.isPublic(modifiers));
            assertFalse(Modifier.isFinal(modifiers));
            assertFalse(Modifier.isStatic(modifiers));
            assertSame(void.class, set.getReturnType());
        }

        assertTrue(GlUniformFloatArray.class.getDeclaredMethod("set", Object.class).isBridge());
    }
}
