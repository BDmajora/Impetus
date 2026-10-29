package com.bdmajora.impetus.engine.impl.gl.shader.uniform;

import com.bdmajora.impetus.engine.impl.gl.shader.GlProgram;
import com.bdmajora.impetus.lwjgl.LWJGLService;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
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

// Tests of the four component vector uniform constructor and array upload
class GlUniformFloat4vTest {
    // Message of a location below minus one
    private static final String BAD_LOCATION = "Uniform location must not be less than -1";
    // Message of an array whose length is not 4
    private static final String BAD_LENGTH = "value.length != 4";
    // Number of components in one upload
    private static final int COMPONENTS = 4;
    // Number of random locations in the sampled constructor test
    private static final int LOCATION_SAMPLES = 100_000;
    // Number of random vectors in the sampled upload test
    private static final int VALUE_SAMPLES = 5_000;
    // Seed of the sampled tests
    private static final long SEED = 0x5EED_F4A7L;
    // Exponent bits of a float
    private static final int EXPONENT = 0x7F80_0000;
    // Mantissa bits of a float
    private static final int MANTISSA = 0x007F_FFFF;
    // Mantissa bit that marks a NaN as quiet
    private static final int QUIET = 0x0040_0000;

    // Shared GL mock that the reset extension clears before each test
    private final LWJGLService gl = TestGl.gl();

    // Vector uniform that records every array it receives instead of calling GL
    private static final class Recording extends GlUniformFloat4v {
        // Arrays in the order the upload received them
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

    // Returns every component sent to the location in call order and checks that no other GL call happened
    private float[] sentValues(int location, int calls) {
        ArgumentCaptor<Float> x = ArgumentCaptor.forClass(Float.class);
        ArgumentCaptor<Float> y = ArgumentCaptor.forClass(Float.class);
        ArgumentCaptor<Float> z = ArgumentCaptor.forClass(Float.class);
        ArgumentCaptor<Float> w = ArgumentCaptor.forClass(Float.class);
        Mockito.verify(this.gl, Mockito.times(calls)).glUniform4f(Mockito.eq(location), x.capture(), y.capture(), z.capture(), w.capture());
        Mockito.verifyNoMoreInteractions(this.gl);

        List<Float> xs = x.getAllValues();
        List<Float> ys = y.getAllValues();
        List<Float> zs = z.getAllValues();
        List<Float> ws = w.getAllValues();
        float[] values = new float[calls * COMPONENTS];

        for (int i = 0; i < calls; i++) {
            values[i * COMPONENTS] = xs.get(i);
            values[i * COMPONENTS + 1] = ys.get(i);
            values[i * COMPONENTS + 2] = zs.get(i);
            values[i * COMPONENTS + 3] = ws.get(i);
        }

        return values;
    }

    // Returns the raw bits of every component sent to the location in call order and checks that no other GL call happened
    private int[] sentBits(int location, int calls) {
        return bitsOf(sentValues(location, calls));
    }

    // Returns the bits with a signaling NaN made quiet
    private static int quiet(int bits) {
        return (bits & EXPONENT) == EXPONENT && (bits & MANTISSA) != 0 ? bits | QUIET : bits;
    }

    // Returns the raw bits of each value
    private static int[] bitsOf(float... values) {
        int[] bits = new int[values.length];

        for (int i = 0; i < values.length; i++) {
            bits[i] = Float.floatToRawIntBits(values[i]);
        }

        return bits;
    }

    // Stores every accepted location exactly without a GL call
    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1, 2, 1023, 1024, 65535, 65536, Integer.MAX_VALUE - 1, Integer.MAX_VALUE})
    void constructorStoresEveryAcceptedLocation(int location) {
        assertEquals(location, new GlUniformFloat4v(location).index);
        Mockito.verifyNoInteractions(this.gl);
    }

    // Rejects every location below minus one with the same message and without a GL call
    @ParameterizedTest
    @ValueSource(ints = {-2, -3, -1024, -65536, Integer.MIN_VALUE + 2, Integer.MIN_VALUE + 1, Integer.MIN_VALUE})
    void constructorRejectsEveryLocationBelowMinusOne(int location) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> new GlUniformFloat4v(location));
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
                assertEquals(location, new GlUniformFloat4v(location).index);
            } else {
                assertThrows(IllegalArgumentException.class, () -> new GlUniformFloat4v(location));
            }
        }

        Mockito.verifyNoInteractions(this.gl);
    }

    // Skips the subclass constructor body when the location is rejected
    @Test
    void rejectedConstructionSkipsTheSubclassBody() {
        List<Integer> bodies = new ArrayList<>();

        // Vector uniform that records when its own constructor body runs
        final class Tracked extends GlUniformFloat4v {
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
        assertThrows(IllegalArgumentException.class, () -> new GlUniformFloat4v(-2));
        GlUniformFloat4v first = new GlUniformFloat4v(7);
        GlUniformFloat4v second = new GlUniformFloat4v(7);
        assertNotSame(first, second);
        assertEquals(first.index, second.index);
        first.set(new float[]{1f, 2f, 3f, 4f});
        second.set(new float[]{5f, 6f, 7f, 8f});
        assertArrayEquals(bitsOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f), sentBits(7, 2));
    }

    // Rejects a null array before any GL call and stays usable
    @Test
    void setRejectsNullBeforeAnyGlCall() {
        GlUniformFloat4v uniform = new GlUniformFloat4v(4);
        assertThrows(NullPointerException.class, () -> uniform.set(null));
        assertThrows(NullPointerException.class, () -> uniform.set(null));
        Mockito.verifyNoInteractions(this.gl);
        uniform.set(new float[]{1f, 2f, 3f, 4f});
        assertArrayEquals(bitsOf(1f, 2f, 3f, 4f), sentBits(4, 1));
    }

    // Rejects every array length other than 4 with the same message and without a GL call
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 5, 6, 7, 8, 12, 16, 64, 4 * 1024, 65535, 65536, 1 << 20})
    void setRejectsEveryOtherLength(int length) {
        GlUniformFloat4v uniform = new GlUniformFloat4v(5);
        float[] value = new float[length];
        Arrays.fill(value, 1f);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> uniform.set(value));
        assertEquals(BAD_LENGTH, error.getMessage());
        assertNull(error.getCause());
        Mockito.verifyNoInteractions(this.gl);
    }

    // Rejects arrays of the wrong length and stays usable
    @Test
    void rejectedArrayLeavesTheUniformUsable() {
        GlUniformFloat4v uniform = new GlUniformFloat4v(5);
        assertThrows(IllegalArgumentException.class, () -> uniform.set(new float[0]));
        assertThrows(IllegalArgumentException.class, () -> uniform.set(new float[]{9f}));
        assertThrows(IllegalArgumentException.class, () -> uniform.set(new float[]{9f, 9f, 9f}));
        assertThrows(IllegalArgumentException.class, () -> uniform.set(new float[]{9f, 9f, 9f, 9f, 9f}));
        assertThrows(IllegalArgumentException.class, () -> uniform.set(new float[8]));
        uniform.set(new float[]{4f, 5f, 6f, 7f});
        assertArrayEquals(bitsOf(4f, 5f, 6f, 7f), sentBits(5, 1));
    }

    // Leaves the array contents untouched on both rejection and upload
    @Test
    void setNeverWritesTheArray() {
        GlUniformFloat4v uniform = new GlUniformFloat4v(1);
        float[] accepted = {Float.NaN, -0f, Float.MIN_VALUE, Float.NEGATIVE_INFINITY};
        float[] acceptedCopy = accepted.clone();
        float[] rejected = {1f, 2f, 3f, 4f, 5f};
        float[] rejectedCopy = rejected.clone();
        uniform.set(accepted);
        assertThrows(IllegalArgumentException.class, () -> uniform.set(rejected));
        assertArrayEquals(bitsOf(acceptedCopy), bitsOf(accepted));
        assertArrayEquals(bitsOf(rejectedCopy), bitsOf(rejected));
    }

    // Sends the array elements as the x then y then z then w components
    @Test
    void setSendsTheElementsInOrder() {
        new GlUniformFloat4v(3).set(new float[]{1f, 2f, 3f, 4f});
        Mockito.verify(this.gl).glUniform4f(3, 1f, 2f, 3f, 4f);
        Mockito.verifyNoMoreInteractions(this.gl);
    }

    // Uploads the array without the array or buffer overloads of GL
    @Test
    void setUsesNoVectorUpload() {
        GlUniformFloat4v uniform = new GlUniformFloat4v(2);
        uniform.set(new float[]{1f, 2f, 3f, 4f});
        uniform.set(new float[4]);
        Mockito.verify(this.gl, Mockito.never()).glUniform4fv(Mockito.anyInt(), Mockito.any(float[].class));
        Mockito.verify(this.gl, Mockito.never()).glUniform4fv(Mockito.anyInt(), Mockito.any(FloatBuffer.class));
        assertArrayEquals(bitsOf(1f, 2f, 3f, 4f, 0f, 0f, 0f, 0f), sentBits(2, 2));
    }

    // Forwards every boundary bit pattern exactly in every component position
    @ParameterizedTest
    @ValueSource(ints = {
            0x0000_0000, 0x8000_0000, 0x0000_0001, 0x8000_0001, 0x007F_FFFF, 0x807F_FFFF, 0x0080_0000, 0x8080_0000,
            0x3F80_0000, 0xBF80_0000, 0x4B80_0000, 0x4B80_0001, 0x7F7F_FFFF, 0xFF7F_FFFF, 0x7F80_0000, 0xFF80_0000,
            0x7FC0_0000, 0x7FC0_0001, 0x7FFF_FFFF, 0xFFC0_0000, 0xFFFF_FFFF})
    void setForwardsBoundaryBitsExactly(int bits) {
        GlUniformFloat4v uniform = new GlUniformFloat4v(2);
        int[] filler = bitsOf(0f, 1f, -1f, 2f);
        int[] expected = new int[COMPONENTS * COMPONENTS];

        for (int position = 0; position < COMPONENTS; position++) {
            int[] row = filler.clone();
            row[position] = bits;
            float[] value = new float[COMPONENTS];

            for (int c = 0; c < COMPONENTS; c++) {
                value[c] = Float.intBitsToFloat(row[c]);
            }

            uniform.set(value);
            System.arraycopy(row, 0, expected, position * COMPONENTS, COMPONENTS);
        }

        assertArrayEquals(expected, sentBits(2, COMPONENTS));
    }

    // Sends a signaling NaN in every component position as a NaN and leaves the other components exact
    @ParameterizedTest
    @ValueSource(ints = {0x7F80_0001, 0x7FA0_0000, 0x7FBF_FFFF, 0xFF80_0001, 0xFFBF_FFFF})
    void setSendsSignalingNaNAsNaN(int bits) {
        GlUniformFloat4v uniform = new GlUniformFloat4v(6);

        for (int position = 0; position < COMPONENTS; position++) {
            float[] value = {1f, 1f, 1f, 1f};
            value[position] = Float.intBitsToFloat(bits);
            uniform.set(value);
        }

        float[] sent = sentValues(6, COMPONENTS);

        for (int i = 0; i < sent.length; i++) {
            if (i / COMPONENTS == i % COMPONENTS) {
                assertTrue(Float.isNaN(sent[i]));
            } else {
                assertEquals(Float.floatToRawIntBits(1f), Float.floatToRawIntBits(sent[i]));
            }
        }
    }

    // Forwards random bit patterns exactly with every NaN made quiet
    @Test
    void setForwardsRandomBitPatternsExactly() {
        SplittableRandom random = new SplittableRandom(SEED);
        GlUniformFloat4v uniform = new GlUniformFloat4v(9);
        int[] expected = new int[VALUE_SAMPLES * COMPONENTS];

        for (int i = 0; i < VALUE_SAMPLES; i++) {
            float[] value = new float[COMPONENTS];

            for (int c = 0; c < COMPONENTS; c++) {
                int bits = quiet(random.nextInt());
                expected[i * COMPONENTS + c] = bits;
                value[c] = Float.intBitsToFloat(bits);
            }

            uniform.set(value);
        }

        assertArrayEquals(expected, sentBits(9, VALUE_SAMPLES));
    }

    // Sends the int and long limits as the nearest float and sends overflow as infinity and underflow as zero
    @Test
    void numericLimitsArriveAsTheConvertedFloat() {
        GlUniformFloat4v uniform = new GlUniformFloat4v(0);
        uniform.set(new float[]{Integer.MAX_VALUE, Integer.MIN_VALUE, Long.MAX_VALUE, Long.MIN_VALUE});
        uniform.set(new float[]{(float) Double.MAX_VALUE, (float) -Double.MAX_VALUE, (float) Double.MIN_VALUE, (float) -Double.MIN_VALUE});
        uniform.set(new float[]{Float.MAX_VALUE * 2f, -Float.MAX_VALUE * 2f, Float.MIN_VALUE / 2f, Float.MAX_VALUE});
        uniform.set(new float[]{Integer.MAX_VALUE + 1, Long.MAX_VALUE + 1, (1 << 24) + 1, Float.MIN_NORMAL});
        int[] expected = {
                0x4F00_0000, 0xCF00_0000, 0x5F00_0000, 0xDF00_0000,
                0x7F80_0000, 0xFF80_0000, 0x0000_0000, 0x8000_0000,
                0x7F80_0000, 0xFF80_0000, 0x0000_0000, 0x7F7F_FFFF,
                0xCF00_0000, 0xDF00_0000, 0x4B80_0000, 0x0080_0000};
        assertArrayEquals(expected, sentBits(0, 4));
    }

    // Sends the current array contents on every call with the same array
    @Test
    void reusedArraySendsItsCurrentContents() {
        GlUniformFloat4v uniform = new GlUniformFloat4v(8);
        float[] value = {1f, 2f, 3f, 4f};
        uniform.set(value);
        value[1] = -2f;
        uniform.set(value);
        uniform.set(value);
        value[0] = 10f;
        value[3] = 40f;
        uniform.set(value);
        Arrays.fill(value, 99f);
        assertArrayEquals(bitsOf(1f, 2f, 3f, 4f, 1f, -2f, 3f, 4f, 1f, -2f, 3f, 4f, 10f, -2f, 3f, 40f), sentBits(8, 4));
    }

    // Sends every repeated value again in call order at the int limit location
    @Test
    void repeatedCallsSendEveryValue() {
        GlUniformFloat4v uniform = new GlUniformFloat4v(Integer.MAX_VALUE);
        float[] values = new float[1002 * COMPONENTS];

        for (int i = 0; i < 1000; i++) {
            uniform.set(new float[]{i, i + 1, i + 2, i + 3});
            values[i * COMPONENTS] = i;
            values[i * COMPONENTS + 1] = i + 1;
            values[i * COMPONENTS + 2] = i + 2;
            values[i * COMPONENTS + 3] = i + 3;
        }

        float[] shared = {7f, 7f, 7f, 7f};
        uniform.set(shared);
        uniform.set(shared);
        Arrays.fill(values, 4000, 4008, 7f);
        assertArrayEquals(bitsOf(values), sentBits(Integer.MAX_VALUE, 1002));
    }

    // Keeps each instance on its own location when calls interleave
    @Test
    void interleavedInstancesKeepTheirLocations() {
        GlUniformFloat4v first = new GlUniformFloat4v(7);
        GlUniformFloat4v second = new GlUniformFloat4v(7);
        GlUniformFloat4v other = new GlUniformFloat4v(8);
        first.set(new float[]{1f, 2f, 3f, 4f});
        other.set(new float[]{5f, 6f, 7f, 8f});
        second.set(new float[]{9f, 10f, 11f, 12f});
        first.set(new float[]{13f, 14f, 15f, 16f});
        other.set(new float[]{17f, 18f, 19f, 20f});

        InOrder order = Mockito.inOrder(this.gl);
        order.verify(this.gl).glUniform4f(7, 1f, 2f, 3f, 4f);
        order.verify(this.gl).glUniform4f(8, 5f, 6f, 7f, 8f);
        order.verify(this.gl).glUniform4f(7, 9f, 10f, 11f, 12f);
        order.verify(this.gl).glUniform4f(7, 13f, 14f, 15f, 16f);
        order.verify(this.gl).glUniform4f(8, 17f, 18f, 19f, 20f);
        Mockito.verifyNoMoreInteractions(this.gl);
    }

    // Forwards minus one to GL
    @Test
    void minusOneLocationStillReachesGl() {
        GlUniformFloat4v uniform = new GlUniformFloat4v(-1);
        uniform.set(new float[]{3f, 4f, 5f, 6f});
        uniform.set(new float[]{-3f, -4f, -5f, -6f});
        assertArrayEquals(bitsOf(3f, 4f, 5f, 6f, -3f, -4f, -5f, -6f), sentBits(-1, 2));
    }

    // Passes a GL failure through unchanged and stays usable
    @Test
    void glFailurePassesThrough() {
        IllegalStateException failure = new IllegalStateException("lost context");
        Mockito.doThrow(failure).doThrow(failure).doNothing().when(this.gl)
                .glUniform4f(Mockito.anyInt(), Mockito.anyFloat(), Mockito.anyFloat(), Mockito.anyFloat(), Mockito.anyFloat());
        GlUniformFloat4v uniform = new GlUniformFloat4v(5);
        float[] value = {1f, 2f, 3f, 4f};
        assertSame(failure, assertThrows(IllegalStateException.class, () -> uniform.set(value)));
        assertSame(failure, assertThrows(IllegalStateException.class, () -> uniform.set(value)));
        uniform.set(value);
        assertArrayEquals(bitsOf(1f, 2f, 3f, 4f, 1f, 2f, 3f, 4f, 1f, 2f, 3f, 4f), sentBits(5, 3));
        assertArrayEquals(bitsOf(1f, 2f, 3f, 4f), bitsOf(value));
        assertEquals(5, uniform.index);
    }

    // Passes a GL error through unchanged
    @Test
    void glErrorPassesThrough() {
        StackOverflowError failure = new StackOverflowError();
        Mockito.doThrow(failure).when(this.gl)
                .glUniform4f(Mockito.anyInt(), Mockito.anyFloat(), Mockito.anyFloat(), Mockito.anyFloat(), Mockito.anyFloat());
        GlUniformFloat4v uniform = new GlUniformFloat4v(0);
        assertSame(failure, assertThrows(StackOverflowError.class, () -> uniform.set(new float[4])));
    }

    // Rejects a value of another type through the raw base type before any GL call
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void rawSetRejectsOtherTypesBeforeAnyGlCall() {
        GlUniform raw = new GlUniformFloat4v(1);
        assertThrows(ClassCastException.class, () -> raw.set(1f));
        assertThrows(ClassCastException.class, () -> raw.set(new double[]{1, 2, 3, 4}));
        assertThrows(ClassCastException.class, () -> raw.set(new int[]{1, 2, 3, 4}));
        assertThrows(ClassCastException.class, () -> raw.set(new Float[]{1f, 2f, 3f, 4f}));
        assertThrows(ClassCastException.class, () -> raw.set(new float[][]{{1f, 2f, 3f, 4f}}));
        assertThrows(ClassCastException.class, () -> raw.set(FloatBuffer.wrap(new float[]{1f, 2f, 3f, 4f})));
        assertThrows(ClassCastException.class, () -> raw.set("1 2 3 4"));
        assertThrows(NullPointerException.class, () -> raw.set(null));
        assertThrows(IllegalArgumentException.class, () -> raw.set(new float[3]));
        Mockito.verifyNoInteractions(this.gl);
        raw.set(new float[]{1f, 2f, 3f, 4f});
        assertArrayEquals(bitsOf(1f, 2f, 3f, 4f), sentBits(1, 1));
    }

    // Dispatches through the base type to the array upload
    @Test
    void setThroughTheBaseTypeReachesGl() {
        GlUniform<float[]> uniform = new GlUniformFloat4v(6);
        uniform.set(new float[]{0.25f, 0.5f, 0.75f, 1f});
        assertArrayEquals(bitsOf(0.25f, 0.5f, 0.75f, 1f), sentBits(6, 1));
    }

    // Routes a call through the base type to an override of the array upload
    @Test
    void arrayUploadIsOverridable() {
        Recording uniform = new Recording(3);
        GlUniform<float[]> base = uniform;
        float[] value = {2.5f, -0f, Float.NaN, 1f};
        base.set(value);
        uniform.set(null);
        assertEquals(2, uniform.values.size());
        assertSame(value, uniform.values.get(0));
        assertNull(uniform.values.get(1));
        Mockito.verifyNoInteractions(this.gl);
    }

    // Wraps the location GL reports through the program binding
    @ParameterizedTest
    @ValueSource(ints = {0, 1, Integer.MAX_VALUE})
    void programBindingWrapsTheReportedLocation(int location) {
        Mockito.doReturn(location).when(this.gl).glGetUniformLocation(Mockito.anyInt(), Mockito.eq("u_Value"));
        GlProgram<GlUniformFloat4v> program = GlProgram.builder("uniform").link(context -> context.bindUniform("u_Value", GlUniformFloat4v::new));
        GlUniformFloat4v uniform = program.getInterface();
        assertEquals(location, uniform.index);
        Mockito.clearInvocations(this.gl);
        uniform.set(new float[]{0.75f, 1.75f, 2.75f, 3.75f});
        assertArrayEquals(bitsOf(0.75f, 1.75f, 2.75f, 3.75f), sentBits(location, 1));
    }

    // Creates no uniform when the program lacks it
    @ParameterizedTest
    @ValueSource(ints = {-1, -2, Integer.MIN_VALUE})
    void missingUniformCreatesNoInstance(int location) {
        Mockito.doReturn(location).when(this.gl).glGetUniformLocation(Mockito.anyInt(), Mockito.eq("u_Value"));
        AtomicInteger created = new AtomicInteger();
        IntFunction<GlUniformFloat4v> factory = index -> {
            created.incrementAndGet();

            return new GlUniformFloat4v(index);
        };

        GlProgram<GlUniformFloat4v> program = GlProgram.builder("uniform").link(context -> context.bindUniformIfPresent("u_Value", factory));
        assertNull(program.getInterface());
        assertEquals(0, created.get());
        Mockito.verify(this.gl, Mockito.never())
                .glUniform4f(Mockito.anyInt(), Mockito.anyFloat(), Mockito.anyFloat(), Mockito.anyFloat(), Mockito.anyFloat());
    }

    // Keeps forwarding the location after the program is deleted
    @Test
    void uniformOutlivesItsProgram() {
        Mockito.doReturn(5).when(this.gl).glGetUniformLocation(Mockito.anyInt(), Mockito.eq("u_Value"));
        GlProgram<GlUniformFloat4v> program = GlProgram.builder("uniform").link(context -> context.bindUniform("u_Value", GlUniformFloat4v::new));
        GlUniformFloat4v uniform = program.getInterface();
        program.delete();
        Mockito.clearInvocations(this.gl);
        uniform.set(new float[]{8f, 9f, 10f, 11f});
        uniform.set(new float[]{8f, 9f, 10f, 11f});
        assertArrayEquals(bitsOf(8f, 9f, 10f, 11f, 8f, 9f, 10f, 11f), sentBits(5, 2));
    }

    // Keeps the class public and concrete and open to subclasses over a float array type argument
    @Test
    void classShape() {
        int modifiers = GlUniformFloat4v.class.getModifiers();
        assertTrue(Modifier.isPublic(modifiers));
        assertFalse(Modifier.isAbstract(modifiers));
        assertFalse(Modifier.isFinal(modifiers));
        ParameterizedType type = (ParameterizedType) GlUniformFloat4v.class.getGenericSuperclass();
        assertSame(GlUniform.class, type.getRawType());
        assertArrayEquals(new Type[]{float[].class}, type.getActualTypeArguments());
        assertEquals(0, GlUniformFloat4v.class.getInterfaces().length);
    }

    // Keeps one public constructor that takes the location
    @Test
    void constructorShape() {
        Constructor<?>[] constructors = GlUniformFloat4v.class.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        assertTrue(Modifier.isPublic(constructors[0].getModifiers()));
        assertArrayEquals(new Class<?>[]{int.class}, constructors[0].getParameterTypes());
    }

    // Keeps no fields of its own
    @Test
    void fieldShape() {
        Field[] fields = Arrays.stream(GlUniformFloat4v.class.getDeclaredFields()).filter(field -> !field.isSynthetic()).toArray(Field[]::new);
        assertEquals(0, fields.length);
    }

    // Keeps the array upload as the only method and keeps it public and void and open to overrides
    @Test
    void methodShape() throws NoSuchMethodException {
        Method[] methods = Arrays.stream(GlUniformFloat4v.class.getDeclaredMethods()).filter(method -> !method.isSynthetic()).toArray(Method[]::new);
        assertEquals(1, methods.length);
        Method set = GlUniformFloat4v.class.getDeclaredMethod("set", float[].class);
        assertEquals(set, methods[0]);
        int modifiers = set.getModifiers();
        assertTrue(Modifier.isPublic(modifiers));
        assertFalse(Modifier.isFinal(modifiers));
        assertFalse(Modifier.isStatic(modifiers));
        assertSame(void.class, set.getReturnType());
        assertTrue(GlUniformFloat4v.class.getDeclaredMethod("set", Object.class).isBridge());
    }
}
