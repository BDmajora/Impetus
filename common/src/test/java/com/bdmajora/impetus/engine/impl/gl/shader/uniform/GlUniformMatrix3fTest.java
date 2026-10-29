package com.bdmajora.impetus.engine.impl.gl.shader.uniform;

import com.bdmajora.impetus.engine.impl.gl.shader.GlProgram;
import com.bdmajora.impetus.lwjgl.LWJGLService;
import com.bdmajora.testing.TestGl;
import org.joml.Matrix3d;
import org.joml.Matrix3f;
import org.joml.Matrix3fStack;
import org.joml.Matrix3x2f;
import org.joml.Matrix4f;
import org.joml.Matrix4x3f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.system.Checks;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

// Tests of the three by three matrix uniform constructor and its upload
class GlUniformMatrix3fTest {
    // Message of a location below minus one
    private static final String BAD_LOCATION = "Uniform location must not be less than -1";
    // Message of a null matrix
    private static final String NULL_MATRIX = "Matrix must not be null";
    // Message of a thread stack without room for the matrix
    private static final String NO_STACK = "Out of stack space.";
    // Number of floats in one matrix
    private static final int ELEMENTS = 9;
    // Number of bytes in one matrix
    private static final int BYTES = ELEMENTS * Float.BYTES;
    // Number of random locations in the sampled constructor test
    private static final int LOCATION_SAMPLES = 100_000;
    // Number of random matrices in the sampled upload test
    private static final int MATRIX_SAMPLES = 2_000;
    // Seed of the sampled tests
    private static final long SEED = 0x5EED_3F3FL;
    // Exponent bits of a float
    private static final int EXPONENT = 0x7F80_0000;
    // Mantissa bits of a float
    private static final int MANTISSA = 0x007F_FFFF;
    // Mantissa bit that marks a NaN as quiet
    private static final int QUIET = 0x0040_0000;
    // Byte that fills guard memory next to the scratch matrix
    private static final byte GUARD = (byte) 0xA5;
    // Bits of the matrix whose elements count from one to nine in column major order
    private static final int[] SEQUENCE = bitsOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f);
    // Bits of the identity matrix
    private static final int[] IDENTITY = bitsOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f);

    // Shared GL mock that the reset extension clears before each test
    private final LWJGLService gl = TestGl.gl();
    // Uploads in the order GL received them
    private final List<Upload> uploads = new ArrayList<>();

    // Upload with the bits of the matrices GL read and the buffer shape and the thread stack state during the call
    private record Upload(int location, boolean transpose, int[] bits, long address, int position, int limit, int capacity,
                          boolean direct, ByteOrder order, long stackBase, int stackSize, long stackPointer, int stackFrame,
                          Thread thread) {}

    // Pointer address and frame index of the thread stack at one moment
    private record StackState(long pointer, int frame) {
        // Returns the state of the stack of the current thread
        static StackState now() {
            MemoryStack stack = MemoryStack.stackGet();

            return new StackState(stack.getPointerAddress(), stack.getFrameIndex());
        }
    }

    // Matrix uniform that records every matrix it receives instead of calling GL
    private static final class Recording extends GlUniformMatrix3f {
        // Matrices in the order the upload received them
        private final List<Matrix3f> values = new ArrayList<>();

        // Creates a recording uniform at the location
        private Recording(int index) {
            super(index);
        }

        // Records the matrix
        @Override
        public void set(Matrix3f value) {
            this.values.add(value);
        }
    }

    // Records every upload together with what GL reads during the call
    @BeforeEach
    void recordUploads() {
        Mockito.doAnswer(this::record).when(this.gl).glUniformMatrix3fv(Mockito.anyInt(), Mockito.anyBoolean(), Mockito.any(FloatBuffer.class));
    }

    // Records the upload with the bits of every whole matrix that native memory holds from the position
    private Object record(InvocationOnMock invocation) {
        int location = invocation.getArgument(0);
        boolean transpose = invocation.getArgument(1);
        FloatBuffer value = invocation.getArgument(2);
        MemoryStack stack = MemoryStack.stackGet();
        long address = MemoryUtil.memAddress(value);
        int[] bits = new int[value.remaining() / ELEMENTS * ELEMENTS];

        for (int i = 0; i < bits.length; i++) {
            bits[i] = MemoryUtil.memGetInt(address + (long) Float.BYTES * i);
        }

        this.uploads.add(new Upload(location, transpose, bits, address, value.position(), value.limit(), value.capacity(),
                value.isDirect(), value.order(), stack.getAddress(), stack.getSize(), stack.getPointerAddress(),
                stack.getFrameIndex(), Thread.currentThread()));

        return null;
    }

    // Returns the uploads after checking that GL received exactly the counted calls and nothing else
    private List<Upload> uploads(int calls) {
        Mockito.verify(this.gl, Mockito.times(calls)).glUniformMatrix3fv(Mockito.anyInt(), Mockito.anyBoolean(), Mockito.any(FloatBuffer.class));
        Mockito.verifyNoMoreInteractions(this.gl);
        assertEquals(calls, this.uploads.size());

        return this.uploads;
    }

    // Checks the location and the transpose flag and the bits of one upload
    private static void assertSent(Upload upload, int location, int[] bits) {
        assertEquals(location, upload.location());
        assertFalse(upload.transpose());
        assertArrayEquals(bits, upload.bits());
    }

    // Checks that the thread stack is back at the state
    private static void assertStackIs(StackState expected) {
        assertEquals(expected, StackState.now());
    }

    // Checks that every guard byte still holds the guard value
    private static void assertGuardIntact(long address, int length) {
        for (int i = 0; i < length; i++) {
            assertEquals(GUARD, MemoryUtil.memGetByte(address + i));
        }
    }

    // Returns the raw bits of each value
    private static int[] bitsOf(float... values) {
        int[] bits = new int[values.length];

        for (int i = 0; i < values.length; i++) {
            bits[i] = Float.floatToRawIntBits(values[i]);
        }

        return bits;
    }

    // Returns the raw bits of the matrix in column major order
    private static int[] bitsOf(Matrix3f m) {
        return bitsOf(m.m00(), m.m01(), m.m02(), m.m10(), m.m11(), m.m12(), m.m20(), m.m21(), m.m22());
    }

    // Returns the matrix whose elements hold the raw bits in column major order
    private static Matrix3f matrixOf(int... bits) {
        float[] v = new float[ELEMENTS];

        for (int i = 0; i < ELEMENTS; i++) {
            v[i] = Float.intBitsToFloat(bits[i]);
        }

        return new Matrix3f(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8]);
    }

    // Returns nine copies of the bits
    private static int[] filled(int bits) {
        int[] all = new int[ELEMENTS];
        Arrays.fill(all, bits);

        return all;
    }

    // Stores every accepted location exactly without a GL call
    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1, 2, 1023, 1024, 65535, 65536, Integer.MAX_VALUE - 1, Integer.MAX_VALUE})
    void constructorStoresEveryAcceptedLocation(int location) {
        assertEquals(location, new GlUniformMatrix3f(location).index);
        Mockito.verifyNoInteractions(this.gl);
    }

    // Rejects every location below minus one with the same message and without a GL call
    @ParameterizedTest
    @ValueSource(ints = {-2, -3, -1024, -65536, Integer.MIN_VALUE + 2, Integer.MIN_VALUE + 1, Integer.MIN_VALUE})
    void constructorRejectsEveryLocationBelowMinusOne(int location) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> new GlUniformMatrix3f(location));
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
                assertEquals(location, new GlUniformMatrix3f(location).index);
            } else {
                assertThrows(IllegalArgumentException.class, () -> new GlUniformMatrix3f(location));
            }
        }

        Mockito.verifyNoInteractions(this.gl);
    }

    // Skips the subclass constructor body when the location is rejected
    @Test
    void rejectedConstructionSkipsTheSubclassBody() {
        List<Integer> bodies = new ArrayList<>();

        // Matrix uniform that records when its own constructor body runs
        final class Tracked extends GlUniformMatrix3f {
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
        assertThrows(IllegalArgumentException.class, () -> new Recording(-2));
        Recording first = new Recording(7);
        Recording second = new Recording(7);
        assertNotSame(first, second);
        Matrix3f matrix = new Matrix3f();
        first.set(matrix);
        assertEquals(List.of(matrix), first.values);
        assertSame(matrix, first.values.get(0));
        assertTrue(second.values.isEmpty());
        assertEquals(first.index, second.index);
        Mockito.verifyNoInteractions(this.gl);
    }

    // Uploads the nine elements untransposed in column major order
    @Test
    void setUploadsTheMatrixInColumnMajorOrderUntransposed() {
        GlUniformMatrix3f uniform = new GlUniformMatrix3f(4);
        uniform.set(new Matrix3f(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f));
        uniform.set(new Matrix3f().zero().m01(5f));
        uniform.set(new Matrix3f().zero().m10(7f));
        uniform.set(new Matrix3f().zero().m21(-2f));
        List<Upload> sent = uploads(4);
        assertSent(sent.get(0), 4, SEQUENCE);
        assertSent(sent.get(1), 4, bitsOf(0f, 5f, 0f, 0f, 0f, 0f, 0f, 0f, 0f));
        assertSent(sent.get(2), 4, bitsOf(0f, 0f, 0f, 7f, 0f, 0f, 0f, 0f, 0f));
        assertSent(sent.get(3), 4, bitsOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, -2f, 0f));
    }

    // Hands GL a direct buffer in the native byte order that holds exactly one matrix from position zero
    @Test
    void setHandsGlExactlyOneMatrixInADirectNativeBuffer() {
        new GlUniformMatrix3f(0).set(new Matrix3f());
        Upload upload = uploads(1).get(0);
        assertEquals(0, upload.position());
        assertEquals(ELEMENTS, upload.limit());
        assertEquals(ELEMENTS, upload.capacity());
        assertEquals(0, (upload.limit() - upload.position()) % ELEMENTS);
        assertEquals(1, (upload.limit() - upload.position()) / ELEMENTS);
        assertTrue(upload.direct());
        assertEquals(ByteOrder.nativeOrder(), upload.order());
    }

    // Uploads the identity and zero and negative zero matrices exactly
    @Test
    void identityAndZeroMatricesArriveExactly() {
        GlUniformMatrix3f uniform = new GlUniformMatrix3f(1);
        uniform.set(new Matrix3f());
        uniform.set(new Matrix3f().zero());
        uniform.set(matrixOf(filled(0x8000_0000)));
        List<Upload> sent = uploads(3);
        assertSent(sent.get(0), 1, IDENTITY);
        assertSent(sent.get(1), 1, filled(0));
        assertSent(sent.get(2), 1, filled(0x8000_0000));
    }

    // Forwards every quiet boundary bit pattern exactly in all elements and in each element alone
    @ParameterizedTest
    @ValueSource(ints = {
            0x0000_0000, 0x8000_0000, 0x0000_0001, 0x8000_0001, 0x007F_FFFF, 0x807F_FFFF, 0x0080_0000, 0x8080_0000,
            0x3F80_0000, 0xBF80_0000, 0x4B80_0000, 0x4B80_0001, 0x7F7F_FFFF, 0xFF7F_FFFF, 0x7F80_0000, 0xFF80_0000,
            0x7FC0_0000, 0x7FC0_0001, 0x7FFF_FFFF, 0xFFC0_0000, 0xFFFF_FFFF})
    void setForwardsQuietBoundaryBitsFromEveryElement(int bits) {
        GlUniformMatrix3f uniform = new GlUniformMatrix3f(2);
        uniform.set(matrixOf(filled(bits)));
        int[][] expected = new int[ELEMENTS + 1][];
        expected[0] = filled(bits);

        for (int slot = 0; slot < ELEMENTS; slot++) {
            int[] single = new int[ELEMENTS];
            single[slot] = bits;
            expected[slot + 1] = single;
            uniform.set(matrixOf(single));
        }

        List<Upload> sent = uploads(ELEMENTS + 1);

        for (int i = 0; i < expected.length; i++) {
            assertSent(sent.get(i), 2, expected[i]);
        }
    }

    // Forwards signaling NaNs in every element as NaNs
    @ParameterizedTest
    @ValueSource(ints = {0x7F80_0001, 0x7FA0_0000, 0x7FBF_FFFF, 0xFF80_0001, 0xFFBF_FFFF})
    void signalingNaNsArriveAsNaNs(int bits) {
        new GlUniformMatrix3f(3).set(matrixOf(filled(bits)));
        Upload upload = uploads(1).get(0);
        assertEquals(ELEMENTS, upload.bits().length);

        for (int element : upload.bits()) {
            assertTrue(Float.isNaN(Float.intBitsToFloat(element)));
        }
    }

    // Forwards random matrices exactly with every NaN made quiet
    @Test
    void setForwardsRandomMatricesExactly() {
        SplittableRandom random = new SplittableRandom(SEED);
        GlUniformMatrix3f uniform = new GlUniformMatrix3f(9);
        int[][] expected = new int[MATRIX_SAMPLES][];

        for (int i = 0; i < MATRIX_SAMPLES; i++) {
            int[] bits = new int[ELEMENTS];

            for (int j = 0; j < ELEMENTS; j++) {
                int element = random.nextInt();

                if ((element & EXPONENT) == EXPONENT && (element & MANTISSA) != 0) {
                    element |= QUIET;
                }

                bits[j] = element;
            }

            expected[i] = bits;
            uniform.set(matrixOf(bits));
        }

        List<Upload> sent = uploads(MATRIX_SAMPLES);

        for (int i = 0; i < MATRIX_SAMPLES; i++) {
            assertSent(sent.get(i), 9, expected[i]);
        }
    }

    // Forwards the int and long limits as the nearest float and double overflow and underflow as infinity and signed zero
    @Test
    void numericLimitsArriveAsTheConvertedFloat() {
        new GlUniformMatrix3f(0).set(new Matrix3f(
                Integer.MAX_VALUE, Integer.MIN_VALUE, Long.MAX_VALUE,
                Long.MIN_VALUE, (float) Double.MAX_VALUE, (float) -Double.MAX_VALUE,
                (float) Double.MIN_VALUE, (float) -Double.MIN_VALUE, Float.MIN_VALUE));
        int[] expected = {0x4F00_0000, 0xCF00_0000, 0x5F00_0000, 0xDF00_0000, 0x7F80_0000, 0xFF80_0000, 0x0000_0000, 0x8000_0000, 0x0000_0001};
        assertSent(uploads(1).get(0), 0, expected);
    }

    // Leaves every element of the matrix unchanged
    @Test
    void setLeavesTheMatrixUnchanged() {
        Matrix3f matrix = matrixOf(SEQUENCE);
        Matrix3f copy = new Matrix3f(matrix);
        new GlUniformMatrix3f(1).set(matrix);
        assertEquals(copy, matrix);
        assertArrayEquals(SEQUENCE, bitsOf(matrix));
        assertSent(uploads(1).get(0), 1, SEQUENCE);
    }

    // Reads the matrix again at each call and keeps no reference to it
    @Test
    void setReadsTheMatrixAtEachCall() {
        GlUniformMatrix3f uniform = new GlUniformMatrix3f(2);
        Matrix3f matrix = matrixOf(SEQUENCE);
        uniform.set(matrix);
        matrix.m11(42f);
        uniform.set(matrix);
        matrix.zero();
        List<Upload> sent = uploads(2);
        assertSent(sent.get(0), 2, SEQUENCE);
        assertSent(sent.get(1), 2, bitsOf(1f, 2f, 3f, 4f, 42f, 6f, 7f, 8f, 9f));
    }

    // Uploads the current matrix of a matrix stack subclass
    @Test
    void matrixStackUploadsItsCurrentMatrix() {
        GlUniformMatrix3f uniform = new GlUniformMatrix3f(3);
        Matrix3fStack matrices = new Matrix3fStack(2);
        matrices.set(matrixOf(SEQUENCE));
        matrices.pushMatrix();
        matrices.scale(2f);
        uniform.set(matrices);
        matrices.popMatrix();
        uniform.set(matrices);
        List<Upload> sent = uploads(2);
        assertSent(sent.get(0), 3, bitsOf(2f, 4f, 6f, 8f, 10f, 12f, 14f, 16f, 18f));
        assertSent(sent.get(1), 3, SEQUENCE);
    }

    // Rejects a null matrix with a message before any GL call and leaves the stack as it was and stays usable
    @Test
    void setRejectsNullBeforeAnyGlCall() {
        GlUniformMatrix3f uniform = new GlUniformMatrix3f(4);
        StackState before = StackState.now();

        for (int i = 0; i < 2; i++) {
            NullPointerException error = assertThrows(NullPointerException.class, () -> uniform.set(null));
            assertEquals(NULL_MATRIX, error.getMessage());
            assertNull(error.getCause());
            assertStackIs(before);
        }

        Mockito.verifyNoInteractions(this.gl);
        uniform.set(new Matrix3f());
        assertSent(uploads(1).get(0), 4, IDENTITY);
    }

    // Rejects a null matrix before it reserves room on a full thread stack
    @Test
    void setRejectsNullBeforeTheThreadStack() {
        GlUniformMatrix3f uniform = new GlUniformMatrix3f(4);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            stack.nmalloc(1, stack.getPointer());
            assertEquals(0, stack.getPointer());
            StackState before = StackState.now();
            NullPointerException error = assertThrows(NullPointerException.class, () -> uniform.set(null));
            assertEquals(NULL_MATRIX, error.getMessage());
            assertStackIs(before);
        }

        Mockito.verifyNoInteractions(this.gl);
    }

    // Rejects a value of another type through the raw base type before any GL call
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void rawSetRejectsOtherTypesBeforeAnyGlCall() {
        GlUniform raw = new GlUniformMatrix3f(1);
        StackState before = StackState.now();
        assertThrows(ClassCastException.class, () -> raw.set(new Matrix4f()));
        assertThrows(ClassCastException.class, () -> raw.set(new Matrix3d()));
        assertThrows(ClassCastException.class, () -> raw.set(new Matrix3x2f()));
        assertThrows(ClassCastException.class, () -> raw.set(new Matrix4x3f()));
        assertThrows(ClassCastException.class, () -> raw.set(new float[ELEMENTS]));
        assertThrows(ClassCastException.class, () -> raw.set("matrix"));
        assertThrows(ClassCastException.class, () -> raw.set(1));
        assertEquals(NULL_MATRIX, assertThrows(NullPointerException.class, () -> raw.set(null)).getMessage());
        assertStackIs(before);
        Mockito.verifyNoInteractions(this.gl);
        raw.set(new Matrix3f());
        assertSent(uploads(1).get(0), 1, IDENTITY);
    }

    // Dispatches through the base type to the matrix upload
    @Test
    void setThroughTheBaseTypeReachesGl() {
        GlUniform<Matrix3f> uniform = new GlUniformMatrix3f(6);
        uniform.set(matrixOf(SEQUENCE));
        assertSent(uploads(1).get(0), 6, SEQUENCE);
    }

    // Places the matrix in a fresh frame of the thread stack directly below the pointer and releases it afterwards
    @Test
    void scratchLivesInTheThreadStackAndIsReleased() {
        GlUniformMatrix3f uniform = new GlUniformMatrix3f(1);
        StackState before = StackState.now();
        uniform.set(matrixOf(SEQUENCE));
        Upload upload = uploads(1).get(0);
        assertSent(upload, 1, SEQUENCE);
        assertSame(Thread.currentThread(), upload.thread());
        assertEquals((before.pointer() - BYTES) & -Float.BYTES, upload.address());
        assertEquals(0, upload.address() % Float.BYTES);
        assertTrue(upload.address() >= upload.stackBase());
        assertTrue(upload.address() + BYTES <= before.pointer());
        assertEquals(upload.address(), upload.stackPointer());
        assertEquals(before.frame() + 1, upload.stackFrame());
        assertStackIs(before);
    }

    // Aligns the matrix to four bytes below a misaligned outer allocation and leaves that allocation intact
    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 5, 6, 7, 13, 4095})
    void misalignedOuterAllocationsStillGiveAnAlignedScratch(int outer) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            long guard = stack.nmalloc(1, outer);
            MemoryUtil.memSet(guard, GUARD, outer);
            StackState before = StackState.now();
            new GlUniformMatrix3f(2).set(matrixOf(SEQUENCE));
            Upload upload = uploads(1).get(0);
            assertSent(upload, 2, SEQUENCE);
            assertEquals((guard - BYTES) & -Float.BYTES, upload.address());
            assertEquals(0, upload.address() % Float.BYTES);
            assertTrue(upload.address() + BYTES <= guard);
            assertGuardIntact(guard, outer);
            assertStackIs(before);
        }
    }

    // Fits the matrix into the last thirty six bytes of the thread stack
    @Test
    void setFitsInTheLastBytesOfTheThreadStack() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            assertEquals(0, stack.getAddress() % Float.BYTES);
            stack.nmalloc(1, stack.getPointer() - BYTES);
            assertEquals(BYTES, stack.getPointer());
            StackState before = StackState.now();
            new GlUniformMatrix3f(3).set(matrixOf(SEQUENCE));
            Upload upload = uploads(1).get(0);
            assertSent(upload, 3, SEQUENCE);
            assertEquals(stack.getAddress(), upload.address());
            assertStackIs(before);
        }
    }

    // Throws without a GL call when the thread stack has less room than one matrix and restores the frame
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 3, 4, 32, BYTES - 1})
    void setFailsWhenTheThreadStackIsFull(int free) {
        assumeTrue(Checks.CHECKS);
        GlUniformMatrix3f uniform = new GlUniformMatrix3f(3);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            stack.nmalloc(1, stack.getPointer() - free);
            assertEquals(free, stack.getPointer());
            StackState before = StackState.now();
            OutOfMemoryError error = null;

            try {
                uniform.set(new Matrix3f());
            } catch (OutOfMemoryError e) {
                error = e;
            }

            assertNotNull(error);
            assertEquals(NO_STACK, error.getMessage());
            assertStackIs(before);
        }

        Mockito.verifyNoInteractions(this.gl);
        uniform.set(new Matrix3f());
        assertSent(uploads(1).get(0), 3, IDENTITY);
    }

    // Restores the stack at any frame depth
    @Test
    void nestedFramesAreRestored() {
        GlUniformMatrix3f uniform = new GlUniformMatrix3f(5);
        MemoryStack stack = MemoryStack.stackGet();
        StackState outer = StackState.now();

        try {
            for (int i = 0; i < 40; i++) {
                stack.push();
                stack.nmalloc(1, 3);
            }

            StackState before = StackState.now();
            uniform.set(matrixOf(SEQUENCE));
            Upload upload = uploads(1).get(0);
            assertSent(upload, 5, SEQUENCE);
            assertEquals(before.frame() + 1, upload.stackFrame());
            assertEquals(0, upload.address() % Float.BYTES);
            assertStackIs(before);
        } finally {
            while (stack.getFrameIndex() > outer.frame()) {
                stack.pop();
            }
        }

        assertStackIs(outer);
    }

    // Passes a GL failure through unchanged and restores the stack and stays usable
    @Test
    void glFailurePassesThroughAndRestoresTheStack() {
        IllegalStateException failure = new IllegalStateException("lost context");
        Mockito.doAnswer(invocation -> {
            this.record(invocation);
            throw failure;
        }).doAnswer(this::record).when(this.gl).glUniformMatrix3fv(Mockito.anyInt(), Mockito.anyBoolean(), Mockito.any(FloatBuffer.class));
        GlUniformMatrix3f uniform = new GlUniformMatrix3f(5);
        StackState before = StackState.now();
        assertSame(failure, assertThrows(IllegalStateException.class, () -> uniform.set(matrixOf(SEQUENCE))));
        assertStackIs(before);
        uniform.set(new Matrix3f());
        assertStackIs(before);
        List<Upload> sent = uploads(2);
        assertSent(sent.get(0), 5, SEQUENCE);
        assertSent(sent.get(1), 5, IDENTITY);
        assertEquals(sent.get(0).address(), sent.get(1).address());
        assertEquals(5, uniform.index);
    }

    // Passes a GL error through unchanged and restores the stack
    @Test
    void glErrorPassesThroughAndRestoresTheStack() {
        StackOverflowError failure = new StackOverflowError();
        Mockito.doThrow(failure).when(this.gl).glUniformMatrix3fv(Mockito.anyInt(), Mockito.anyBoolean(), Mockito.any(FloatBuffer.class));
        GlUniformMatrix3f uniform = new GlUniformMatrix3f(0);
        StackState before = StackState.now();
        assertSame(failure, assertThrows(StackOverflowError.class, () -> uniform.set(new Matrix3f())));
        assertStackIs(before);
    }

    // Passes a failure of the matrix copy through without a GL call and restores the stack
    @Test
    void matrixFailurePassesThroughAndRestoresTheStack() {
        IllegalStateException failure = new IllegalStateException("broken matrix");
        Matrix3f broken = new Matrix3f() {
            // Throws instead of writing the elements
            @Override
            public FloatBuffer get(FloatBuffer buffer) {
                throw failure;
            }
        };
        GlUniformMatrix3f uniform = new GlUniformMatrix3f(0);
        StackState before = StackState.now();
        assertSame(failure, assertThrows(IllegalStateException.class, () -> uniform.set(broken)));
        assertStackIs(before);
        Mockito.verifyNoInteractions(this.gl);
    }

    // Sends every repeated matrix again from the same stack address in call order
    @Test
    void repeatedCallsSendEveryMatrixFromTheSameScratch() {
        GlUniformMatrix3f uniform = new GlUniformMatrix3f(Integer.MAX_VALUE);
        StackState before = StackState.now();
        Matrix3f matrix = new Matrix3f();
        int[][] expected = new int[1002][];

        for (int i = 0; i < 1000; i++) {
            matrix.identity().scale(i).m21(-i);
            uniform.set(matrix);
            expected[i] = bitsOf(matrix);
        }

        uniform.set(matrix);
        uniform.set(matrix);
        expected[1000] = bitsOf(matrix);
        expected[1001] = bitsOf(matrix);
        List<Upload> sent = uploads(1002);

        for (int i = 0; i < expected.length; i++) {
            assertSent(sent.get(i), Integer.MAX_VALUE, expected[i]);
            assertEquals(sent.get(0).address(), sent.get(i).address());
        }

        assertStackIs(before);
    }

    // Keeps each instance on its own location when calls interleave
    @Test
    void interleavedInstancesKeepTheirLocations() {
        GlUniformMatrix3f first = new GlUniformMatrix3f(7);
        GlUniformMatrix3f second = new GlUniformMatrix3f(7);
        GlUniformMatrix3f other = new GlUniformMatrix3f(8);
        first.set(matrixOf(filled(Float.floatToRawIntBits(1f))));
        other.set(matrixOf(filled(Float.floatToRawIntBits(2f))));
        second.set(matrixOf(filled(Float.floatToRawIntBits(3f))));
        first.set(matrixOf(filled(Float.floatToRawIntBits(4f))));
        other.set(matrixOf(filled(Float.floatToRawIntBits(5f))));
        List<Upload> sent = uploads(5);
        int[] locations = {7, 8, 7, 7, 8};

        for (int i = 0; i < locations.length; i++) {
            assertSent(sent.get(i), locations[i], filled(Float.floatToRawIntBits(i + 1f)));
        }
    }

    // Forwards minus one to GL
    @Test
    void minusOneLocationStillReachesGl() {
        new GlUniformMatrix3f(-1).set(matrixOf(SEQUENCE));
        assertSent(uploads(1).get(0), -1, SEQUENCE);
    }

    // Uses the stack of the calling thread when another thread uploads
    @Test
    void setOnAnotherThreadUsesThatThreadStack() throws InterruptedException {
        GlUniformMatrix3f uniform = new GlUniformMatrix3f(6);
        StackState before = StackState.now();
        long mainBase = MemoryStack.stackGet().getAddress();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                uniform.set(matrixOf(SEQUENCE));
            } catch (Throwable t) {
                failure.set(t);
            }
        }, "matrix uniform worker");
        worker.start();
        worker.join();
        assertNull(failure.get());
        Upload upload = uploads(1).get(0);
        assertSent(upload, 6, SEQUENCE);
        assertSame(worker, upload.thread());
        assertNotEquals(mainBase, upload.stackBase());
        assertTrue(upload.address() >= upload.stackBase());
        assertTrue(upload.address() + BYTES <= upload.stackBase() + upload.stackSize());
        assertEquals(1, upload.stackFrame());
        assertStackIs(before);
    }

    // Wraps the location GL reports through the program binding
    @ParameterizedTest
    @ValueSource(ints = {0, 1, Integer.MAX_VALUE})
    void programBindingWrapsTheReportedLocation(int location) {
        Mockito.doReturn(location).when(this.gl).glGetUniformLocation(Mockito.anyInt(), Mockito.eq("u_Value"));
        GlProgram<GlUniformMatrix3f> program = GlProgram.builder("uniform").link(context -> context.bindUniform("u_Value", GlUniformMatrix3f::new));
        GlUniformMatrix3f uniform = program.getInterface();
        assertEquals(location, uniform.index);
        Mockito.clearInvocations(this.gl);
        uniform.set(matrixOf(SEQUENCE));
        assertSent(uploads(1).get(0), location, SEQUENCE);
    }

    // Creates no uniform when the program lacks it
    @ParameterizedTest
    @ValueSource(ints = {-1, -2, Integer.MIN_VALUE})
    void missingUniformCreatesNoInstance(int location) {
        Mockito.doReturn(location).when(this.gl).glGetUniformLocation(Mockito.anyInt(), Mockito.eq("u_Value"));
        AtomicInteger created = new AtomicInteger();
        IntFunction<GlUniformMatrix3f> factory = index -> {
            created.incrementAndGet();

            return new GlUniformMatrix3f(index);
        };

        GlProgram<GlUniformMatrix3f> program = GlProgram.builder("uniform").link(context -> context.bindUniformIfPresent("u_Value", factory));
        assertNull(program.getInterface());
        assertEquals(0, created.get());
        Mockito.verify(this.gl, Mockito.never()).glUniformMatrix3fv(Mockito.anyInt(), Mockito.anyBoolean(), Mockito.any(FloatBuffer.class));
    }

    // Keeps forwarding the location after the program is deleted
    @Test
    void uniformOutlivesItsProgram() {
        Mockito.doReturn(5).when(this.gl).glGetUniformLocation(Mockito.anyInt(), Mockito.eq("u_Value"));
        GlProgram<GlUniformMatrix3f> program = GlProgram.builder("uniform").link(context -> context.bindUniform("u_Value", GlUniformMatrix3f::new));
        GlUniformMatrix3f uniform = program.getInterface();
        program.delete();
        Mockito.clearInvocations(this.gl);
        uniform.set(matrixOf(SEQUENCE));
        uniform.set(matrixOf(SEQUENCE));
        List<Upload> sent = uploads(2);
        assertSent(sent.get(0), 5, SEQUENCE);
        assertSent(sent.get(1), 5, SEQUENCE);
    }

    // Keeps the class public and concrete and open to subclasses over a Matrix3f type argument
    @Test
    void classShape() {
        int modifiers = GlUniformMatrix3f.class.getModifiers();
        assertTrue(Modifier.isPublic(modifiers));
        assertFalse(Modifier.isAbstract(modifiers));
        assertFalse(Modifier.isFinal(modifiers));
        ParameterizedType type = (ParameterizedType) GlUniformMatrix3f.class.getGenericSuperclass();
        assertSame(GlUniform.class, type.getRawType());
        assertArrayEquals(new Type[]{Matrix3f.class}, type.getActualTypeArguments());
        assertEquals(0, GlUniformMatrix3f.class.getInterfaces().length);
    }

    // Keeps one public constructor that takes the location
    @Test
    void constructorShape() {
        Constructor<?>[] constructors = GlUniformMatrix3f.class.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        assertTrue(Modifier.isPublic(constructors[0].getModifiers()));
        assertArrayEquals(new Class<?>[]{int.class}, constructors[0].getParameterTypes());
    }

    // Keeps one private constant of nine elements and no instance fields
    @Test
    void fieldShape() throws IllegalAccessException {
        Field[] fields = Arrays.stream(GlUniformMatrix3f.class.getDeclaredFields()).filter(field -> !field.isSynthetic()).toArray(Field[]::new);
        assertEquals(1, fields.length);
        Field elements = fields[0];
        int modifiers = elements.getModifiers();
        assertEquals("ELEMENTS", elements.getName());
        assertSame(int.class, elements.getType());
        assertTrue(Modifier.isPrivate(modifiers));
        assertTrue(Modifier.isStatic(modifiers));
        assertTrue(Modifier.isFinal(modifiers));
        elements.setAccessible(true);
        assertEquals(ELEMENTS, elements.getInt(null));
    }

    // Keeps the upload public and void and open to overrides
    @Test
    void methodShape() throws NoSuchMethodException {
        Method[] methods = Arrays.stream(GlUniformMatrix3f.class.getDeclaredMethods()).filter(method -> !method.isSynthetic()).toArray(Method[]::new);
        assertEquals(1, methods.length);
        Method set = GlUniformMatrix3f.class.getDeclaredMethod("set", Matrix3f.class);
        int modifiers = set.getModifiers();
        assertTrue(Modifier.isPublic(modifiers));
        assertFalse(Modifier.isFinal(modifiers));
        assertFalse(Modifier.isStatic(modifiers));
        assertSame(void.class, set.getReturnType());
        assertTrue(GlUniformMatrix3f.class.getDeclaredMethod("set", Object.class).isBridge());
    }
}
