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

// Tests of the vector uniform constructor and both upload methods
class GlUniformFloat3vTest {
    // Message of a location below minus one
    private static final String BAD_LOCATION = "Uniform location must not be less than -1";
    // Message of an array whose length is not 3
    private static final String BAD_LENGTH = "value.length != 3";
    // Number of random locations in the sampled constructor test
    private static final int LOCATION_SAMPLES = 100_000;
    // Number of random vectors in each sampled upload test
    private static final int VALUE_SAMPLES = 5_000;
    // Seed of the sampled tests
    private static final long SEED = 0x5EED_F3A7L;
    // Exponent bits of a float
    private static final int EXPONENT = 0x7F80_0000;
    // Mantissa bits of a float
    private static final int MANTISSA = 0x007F_FFFF;
    // Mantissa bit that marks a NaN as quiet
    private static final int QUIET = 0x0040_0000;

    // Shared GL mock that the reset extension clears before each test
    private final LWJGLService gl = TestGl.gl();

    // Vector uniform that records every component upload instead of calling GL
    private static final class Recording extends GlUniformFloat3v {
        // Components in the order the component upload received them
        private final List<Float> values = new ArrayList<>();

        // Creates a recording uniform at the location
        private Recording(int index) {
            super(index);
        }

        // Records the three components
        @Override
        public void set(float x, float y, float z) {
            this.values.addAll(List.of(x, y, z));
        }
    }

    // Returns the raw bits of every component sent to the location in call order and checks that no other GL call happened
    private int[] sentBits(int location, int calls) {
        ArgumentCaptor<Float> x = ArgumentCaptor.forClass(Float.class);
        ArgumentCaptor<Float> y = ArgumentCaptor.forClass(Float.class);
        ArgumentCaptor<Float> z = ArgumentCaptor.forClass(Float.class);
        Mockito.verify(this.gl, Mockito.times(calls)).glUniform3f(Mockito.eq(location), x.capture(), y.capture(), z.capture());
        Mockito.verifyNoMoreInteractions(this.gl);

        List<Float> xs = x.getAllValues();
        List<Float> ys = y.getAllValues();
        List<Float> zs = z.getAllValues();
        int[] bits = new int[calls * 3];

        for (int i = 0; i < calls; i++) {
            bits[i * 3] = Float.floatToRawIntBits(xs.get(i));
            bits[i * 3 + 1] = Float.floatToRawIntBits(ys.get(i));
            bits[i * 3 + 2] = Float.floatToRawIntBits(zs.get(i));
        }

        return bits;
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
        assertEquals(location, new GlUniformFloat3v(location).index);
        Mockito.verifyNoInteractions(this.gl);
    }

    // Rejects every location below minus one with the same message and without a GL call
    @ParameterizedTest
    @ValueSource(ints = {-2, -3, -1024, -65536, Integer.MIN_VALUE + 2, Integer.MIN_VALUE + 1, Integer.MIN_VALUE})
    void constructorRejectsEveryLocationBelowMinusOne(int location) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> new GlUniformFloat3v(location));
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
                assertEquals(location, new GlUniformFloat3v(location).index);
            } else {
                assertThrows(IllegalArgumentException.class, () -> new GlUniformFloat3v(location));
            }
        }

        Mockito.verifyNoInteractions(this.gl);
    }

    // Skips the subclass constructor body when the location is rejected
    @Test
    void rejectedConstructionSkipsTheSubclassBody() {
        List<Integer> bodies = new ArrayList<>();

        // Vector uniform that records when its own constructor body runs
        final class Tracked extends GlUniformFloat3v {
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
        first.set(1f, 2f, 3f);
        assertEquals(List.of(1f, 2f, 3f), first.values);
        assertTrue(second.values.isEmpty());
        assertEquals(first.index, second.index);
    }

    // Rejects a null array before any GL call and stays usable
    @Test
    void setRejectsNullBeforeAnyGlCall() {
        GlUniformFloat3v uniform = new GlUniformFloat3v(4);
        assertThrows(NullPointerException.class, () -> uniform.set(null));
        assertThrows(NullPointerException.class, () -> uniform.set(null));
        Mockito.verifyNoInteractions(this.gl);
        uniform.set(new float[]{1f, 2f, 3f});
        assertArrayEquals(bitsOf(1f, 2f, 3f), sentBits(4, 1));
    }

    // Rejects every array length other than 3 with the same message and without a GL call
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 4, 5, 6, 9, 12, 64, 3 * 1024, 65535, 65536, 1 << 20})
    void setRejectsEveryOtherLength(int length) {
        GlUniformFloat3v uniform = new GlUniformFloat3v(5);
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
        GlUniformFloat3v uniform = new GlUniformFloat3v(5);
        assertThrows(IllegalArgumentException.class, () -> uniform.set(new float[0]));
        assertThrows(IllegalArgumentException.class, () -> uniform.set(new float[]{9f}));
        assertThrows(IllegalArgumentException.class, () -> uniform.set(new float[]{9f, 9f, 9f, 9f}));
        uniform.set(new float[]{4f, 5f, 6f});
        assertArrayEquals(bitsOf(4f, 5f, 6f), sentBits(5, 1));
    }

    // Leaves the array contents untouched on both rejection and upload
    @Test
    void setNeverWritesTheArray() {
        GlUniformFloat3v uniform = new GlUniformFloat3v(1);
        float[] accepted = {Float.NaN, -0f, Float.MIN_VALUE};
        float[] acceptedCopy = accepted.clone();
        float[] rejected = {1f, 2f, 3f, 4f};
        float[] rejectedCopy = rejected.clone();
        uniform.set(accepted);
        assertThrows(IllegalArgumentException.class, () -> uniform.set(rejected));
        assertArrayEquals(bitsOf(acceptedCopy), bitsOf(accepted));
        assertArrayEquals(bitsOf(rejectedCopy), bitsOf(rejected));
    }

    // Sends the array elements as the x then y then z components
    @Test
    void setArraySendsTheElementsInOrder() {
        new GlUniformFloat3v(3).set(new float[]{1f, 2f, 3f});
        Mockito.verify(this.gl).glUniform3f(3, 1f, 2f, 3f);
        Mockito.verifyNoMoreInteractions(this.gl);
    }

    // Sends the components in the order they were passed
    @Test
    void setComponentsSendsTheComponentsInOrder() {
        new GlUniformFloat3v(3).set(1f, 2f, 3f);
        Mockito.verify(this.gl).glUniform3f(3, 1f, 2f, 3f);
        Mockito.verifyNoMoreInteractions(this.gl);
    }

    // Uploads the array without the array or buffer overloads of GL
    @Test
    void setArrayUsesNoVectorUpload() {
        GlUniformFloat3v uniform = new GlUniformFloat3v(2);
        uniform.set(new float[]{1f, 2f, 3f});
        uniform.set(new float[3]);
        Mockito.verify(this.gl, Mockito.never()).glUniform3fv(Mockito.anyInt(), Mockito.any(float[].class));
        Mockito.verify(this.gl, Mockito.never()).glUniform3fv(Mockito.anyInt(), Mockito.any(FloatBuffer.class));
        assertArrayEquals(bitsOf(1f, 2f, 3f, 0f, 0f, 0f), sentBits(2, 2));
    }

    // Uploads the array without dispatching to an overridden component method
    @Test
    void setArrayBypassesAnOverriddenComponentMethod() {
        Recording uniform = new Recording(6);
        uniform.set(new float[]{7f, 8f, 9f});
        assertTrue(uniform.values.isEmpty());
        assertArrayEquals(bitsOf(7f, 8f, 9f), sentBits(6, 1));
    }

    // Forwards every boundary bit pattern exactly in every component position through both upload methods
    @ParameterizedTest
    @ValueSource(ints = {
            0x0000_0000, 0x8000_0000, 0x0000_0001, 0x8000_0001, 0x007F_FFFF, 0x807F_FFFF, 0x0080_0000, 0x8080_0000,
            0x3F80_0000, 0xBF80_0000, 0x4B80_0000, 0x4B80_0001, 0x7F7F_FFFF, 0xFF7F_FFFF, 0x7F80_0000, 0xFF80_0000,
            0x7FC0_0000, 0x7FC0_0001, 0x7FFF_FFFF, 0xFFC0_0000, 0xFFFF_FFFF})
    void bothMethodsForwardBoundaryBitsExactly(int bits) {
        GlUniformFloat3v uniform = new GlUniformFloat3v(2);
        float v = Float.intBitsToFloat(bits);
        uniform.set(new float[]{v, 0f, 1f});
        uniform.set(new float[]{0f, v, 1f});
        uniform.set(new float[]{0f, 1f, v});
        uniform.set(v, 0f, 1f);
        uniform.set(0f, v, 1f);
        uniform.set(0f, 1f, v);
        int zero = Float.floatToRawIntBits(0f);
        int one = Float.floatToRawIntBits(1f);
        int[] row = {bits, zero, one, zero, bits, one, zero, one, bits};
        int[] expected = new int[18];
        System.arraycopy(row, 0, expected, 0, 9);
        System.arraycopy(row, 0, expected, 9, 9);
        assertArrayEquals(expected, sentBits(2, 6));
    }

    // Forwards random bit patterns exactly through the array upload with every NaN made quiet
    @Test
    void setArrayForwardsRandomBitPatternsExactly() {
        SplittableRandom random = new SplittableRandom(SEED);
        GlUniformFloat3v uniform = new GlUniformFloat3v(9);
        int[] expected = new int[VALUE_SAMPLES * 3];

        for (int i = 0; i < VALUE_SAMPLES; i++) {
            float[] value = new float[3];

            for (int c = 0; c < 3; c++) {
                int bits = quiet(random.nextInt());
                expected[i * 3 + c] = bits;
                value[c] = Float.intBitsToFloat(bits);
            }

            uniform.set(value);
        }

        assertArrayEquals(expected, sentBits(9, VALUE_SAMPLES));
    }

    // Forwards random bit patterns exactly through the component upload with every NaN made quiet
    @Test
    void setComponentsForwardsRandomBitPatternsExactly() {
        SplittableRandom random = new SplittableRandom(SEED + 1);
        GlUniformFloat3v uniform = new GlUniformFloat3v(9);
        int[] expected = new int[VALUE_SAMPLES * 3];

        for (int i = 0; i < VALUE_SAMPLES; i++) {
            int x = quiet(random.nextInt());
            int y = quiet(random.nextInt());
            int z = quiet(random.nextInt());
            expected[i * 3] = x;
            expected[i * 3 + 1] = y;
            expected[i * 3 + 2] = z;
            uniform.set(Float.intBitsToFloat(x), Float.intBitsToFloat(y), Float.intBitsToFloat(z));
        }

        assertArrayEquals(expected, sentBits(9, VALUE_SAMPLES));
    }

    // Forwards the int and long limits as the nearest float and double overflow as infinity
    @Test
    void numericLimitsArriveAsTheConvertedFloat() {
        GlUniformFloat3v uniform = new GlUniformFloat3v(0);
        uniform.set(Integer.MAX_VALUE, Integer.MIN_VALUE, Long.MAX_VALUE);
        uniform.set(new float[]{Long.MIN_VALUE, (float) Double.MAX_VALUE, (float) -Double.MAX_VALUE});
        uniform.set((float) Double.MIN_VALUE, (float) -Double.MIN_VALUE, (float) (double) Float.MAX_VALUE);
        int[] expected = {
                0x4F00_0000, 0xCF00_0000, 0x5F00_0000,
                0xDF00_0000, 0x7F80_0000, 0xFF80_0000,
                0x0000_0000, 0x8000_0000, 0x7F7F_FFFF};
        assertArrayEquals(expected, sentBits(0, 3));
    }

    // Sends the current array contents on every call with the same array
    @Test
    void reusedArraySendsItsCurrentContents() {
        GlUniformFloat3v uniform = new GlUniformFloat3v(8);
        float[] value = {1f, 2f, 3f};
        uniform.set(value);
        value[1] = -2f;
        uniform.set(value);
        uniform.set(value);
        value[0] = 10f;
        value[2] = 30f;
        uniform.set(value);
        Arrays.fill(value, 99f);
        assertArrayEquals(bitsOf(1f, 2f, 3f, 1f, -2f, 3f, 1f, -2f, 3f, 10f, -2f, 30f), sentBits(8, 4));
    }

    // Sends every repeated value again in call order from both upload methods
    @Test
    void repeatedCallsSendEveryValue() {
        GlUniformFloat3v uniform = new GlUniformFloat3v(Integer.MAX_VALUE);
        float[] values = new float[1002 * 3];

        for (int i = 0; i < 1000; i++) {
            if ((i & 1) == 0) {
                uniform.set(i, i + 1, i + 2);
            } else {
                uniform.set(new float[]{i, i + 1, i + 2});
            }

            values[i * 3] = i;
            values[i * 3 + 1] = i + 1;
            values[i * 3 + 2] = i + 2;
        }

        float[] shared = {7f, 7f, 7f};
        uniform.set(shared);
        uniform.set(7f, 7f, 7f);
        Arrays.fill(values, 3000, 3006, 7f);
        assertArrayEquals(bitsOf(values), sentBits(Integer.MAX_VALUE, 1002));
    }

    // Keeps each instance on its own location when calls interleave
    @Test
    void interleavedInstancesKeepTheirLocations() {
        GlUniformFloat3v first = new GlUniformFloat3v(7);
        GlUniformFloat3v second = new GlUniformFloat3v(7);
        GlUniformFloat3v other = new GlUniformFloat3v(8);
        first.set(1f, 2f, 3f);
        other.set(new float[]{4f, 5f, 6f});
        second.set(new float[]{7f, 8f, 9f});
        first.set(new float[]{10f, 11f, 12f});
        other.set(13f, 14f, 15f);

        InOrder order = Mockito.inOrder(this.gl);
        order.verify(this.gl).glUniform3f(7, 1f, 2f, 3f);
        order.verify(this.gl).glUniform3f(8, 4f, 5f, 6f);
        order.verify(this.gl).glUniform3f(7, 7f, 8f, 9f);
        order.verify(this.gl).glUniform3f(7, 10f, 11f, 12f);
        order.verify(this.gl).glUniform3f(8, 13f, 14f, 15f);
        Mockito.verifyNoMoreInteractions(this.gl);
    }

    // Forwards minus one to GL from both upload methods
    @Test
    void minusOneLocationStillReachesGl() {
        GlUniformFloat3v uniform = new GlUniformFloat3v(-1);
        uniform.set(new float[]{3f, 4f, 5f});
        uniform.set(-3f, -4f, -5f);
        assertArrayEquals(bitsOf(3f, 4f, 5f, -3f, -4f, -5f), sentBits(-1, 2));
    }

    // Passes a GL failure through unchanged from both upload methods and stays usable
    @Test
    void glFailurePassesThrough() {
        IllegalStateException failure = new IllegalStateException("lost context");
        Mockito.doThrow(failure).doThrow(failure).doNothing().when(this.gl)
                .glUniform3f(Mockito.anyInt(), Mockito.anyFloat(), Mockito.anyFloat(), Mockito.anyFloat());
        GlUniformFloat3v uniform = new GlUniformFloat3v(5);
        float[] value = {1f, 2f, 3f};
        assertSame(failure, assertThrows(IllegalStateException.class, () -> uniform.set(value)));
        assertSame(failure, assertThrows(IllegalStateException.class, () -> uniform.set(4f, 5f, 6f)));
        uniform.set(value);
        assertArrayEquals(bitsOf(1f, 2f, 3f, 4f, 5f, 6f, 1f, 2f, 3f), sentBits(5, 3));
        assertArrayEquals(bitsOf(1f, 2f, 3f), bitsOf(value));
        assertEquals(5, uniform.index);
    }

    // Passes a GL error through unchanged from both upload methods
    @Test
    void glErrorPassesThrough() {
        StackOverflowError failure = new StackOverflowError();
        Mockito.doThrow(failure).when(this.gl).glUniform3f(Mockito.anyInt(), Mockito.anyFloat(), Mockito.anyFloat(), Mockito.anyFloat());
        GlUniformFloat3v uniform = new GlUniformFloat3v(0);
        assertSame(failure, assertThrows(StackOverflowError.class, () -> uniform.set(new float[3])));
        assertSame(failure, assertThrows(StackOverflowError.class, () -> uniform.set(1f, 1f, 1f)));
    }

    // Rejects a value of another type through the raw base type before any GL call
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void rawSetRejectsOtherTypesBeforeAnyGlCall() {
        GlUniform raw = new GlUniformFloat3v(1);
        assertThrows(ClassCastException.class, () -> raw.set(1f));
        assertThrows(ClassCastException.class, () -> raw.set(new double[]{1, 2, 3}));
        assertThrows(ClassCastException.class, () -> raw.set(new int[]{1, 2, 3}));
        assertThrows(ClassCastException.class, () -> raw.set(new Float[]{1f, 2f, 3f}));
        assertThrows(ClassCastException.class, () -> raw.set(FloatBuffer.wrap(new float[]{1f, 2f, 3f})));
        assertThrows(ClassCastException.class, () -> raw.set("1 2 3"));
        assertThrows(NullPointerException.class, () -> raw.set(null));
        assertThrows(IllegalArgumentException.class, () -> raw.set(new float[2]));
        Mockito.verifyNoInteractions(this.gl);
        raw.set(new float[]{1f, 2f, 3f});
        assertArrayEquals(bitsOf(1f, 2f, 3f), sentBits(1, 1));
    }

    // Dispatches through the base type to the array upload
    @Test
    void setThroughTheBaseTypeReachesGl() {
        GlUniform<float[]> uniform = new GlUniformFloat3v(6);
        uniform.set(new float[]{0.25f, 0.5f, 0.75f});
        assertArrayEquals(bitsOf(0.25f, 0.5f, 0.75f), sentBits(6, 1));
    }

    // Routes the component upload through an override
    @Test
    void componentMethodIsOverridable() {
        Recording uniform = new Recording(3);
        uniform.set(2.5f, -0f, Float.NaN);
        assertEquals(List.of(2.5f, -0f, Float.NaN), uniform.values);
        Mockito.verifyNoInteractions(this.gl);
    }

    // Wraps the location GL reports through the program binding
    @ParameterizedTest
    @ValueSource(ints = {0, 1, Integer.MAX_VALUE})
    void programBindingWrapsTheReportedLocation(int location) {
        Mockito.doReturn(location).when(this.gl).glGetUniformLocation(Mockito.anyInt(), Mockito.eq("u_Value"));
        GlProgram<GlUniformFloat3v> program = GlProgram.builder("uniform").link(context -> context.bindUniform("u_Value", GlUniformFloat3v::new));
        GlUniformFloat3v uniform = program.getInterface();
        assertEquals(location, uniform.index);
        Mockito.clearInvocations(this.gl);
        uniform.set(new float[]{0.75f, 1.75f, 2.75f});
        assertArrayEquals(bitsOf(0.75f, 1.75f, 2.75f), sentBits(location, 1));
    }

    // Creates no uniform when the program lacks it
    @ParameterizedTest
    @ValueSource(ints = {-1, -2, Integer.MIN_VALUE})
    void missingUniformCreatesNoInstance(int location) {
        Mockito.doReturn(location).when(this.gl).glGetUniformLocation(Mockito.anyInt(), Mockito.eq("u_Value"));
        AtomicInteger created = new AtomicInteger();
        IntFunction<GlUniformFloat3v> factory = index -> {
            created.incrementAndGet();

            return new GlUniformFloat3v(index);
        };

        GlProgram<GlUniformFloat3v> program = GlProgram.builder("uniform").link(context -> context.bindUniformIfPresent("u_Value", factory));
        assertNull(program.getInterface());
        assertEquals(0, created.get());
        Mockito.verify(this.gl, Mockito.never()).glUniform3f(Mockito.anyInt(), Mockito.anyFloat(), Mockito.anyFloat(), Mockito.anyFloat());
    }

    // Keeps forwarding the location after the program is deleted
    @Test
    void uniformOutlivesItsProgram() {
        Mockito.doReturn(5).when(this.gl).glGetUniformLocation(Mockito.anyInt(), Mockito.eq("u_Value"));
        GlProgram<GlUniformFloat3v> program = GlProgram.builder("uniform").link(context -> context.bindUniform("u_Value", GlUniformFloat3v::new));
        GlUniformFloat3v uniform = program.getInterface();
        program.delete();
        Mockito.clearInvocations(this.gl);
        uniform.set(new float[]{8f, 9f, 10f});
        uniform.set(8f, 9f, 10f);
        assertArrayEquals(bitsOf(8f, 9f, 10f, 8f, 9f, 10f), sentBits(5, 2));
    }

    // Keeps the class public and concrete and open to subclasses over a float array type argument
    @Test
    void classShape() {
        int modifiers = GlUniformFloat3v.class.getModifiers();
        assertTrue(Modifier.isPublic(modifiers));
        assertFalse(Modifier.isAbstract(modifiers));
        assertFalse(Modifier.isFinal(modifiers));
        ParameterizedType type = (ParameterizedType) GlUniformFloat3v.class.getGenericSuperclass();
        assertSame(GlUniform.class, type.getRawType());
        assertArrayEquals(new java.lang.reflect.Type[]{float[].class}, type.getActualTypeArguments());
        assertEquals(0, GlUniformFloat3v.class.getInterfaces().length);
    }

    // Keeps one public constructor that takes the location
    @Test
    void constructorShape() {
        Constructor<?>[] constructors = GlUniformFloat3v.class.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        assertTrue(Modifier.isPublic(constructors[0].getModifiers()));
        assertArrayEquals(new Class<?>[]{int.class}, constructors[0].getParameterTypes());
    }

    // Keeps no fields of its own
    @Test
    void fieldShape() {
        Field[] fields = Arrays.stream(GlUniformFloat3v.class.getDeclaredFields()).filter(field -> !field.isSynthetic()).toArray(Field[]::new);
        assertEquals(0, fields.length);
    }

    // Keeps both upload methods public and void and open to overrides
    @Test
    void methodShape() throws NoSuchMethodException {
        Method[] methods = Arrays.stream(GlUniformFloat3v.class.getDeclaredMethods()).filter(method -> !method.isSynthetic()).toArray(Method[]::new);
        assertEquals(2, methods.length);

        for (Method method : new Method[]{
                GlUniformFloat3v.class.getDeclaredMethod("set", float[].class),
                GlUniformFloat3v.class.getDeclaredMethod("set", float.class, float.class, float.class)}) {
            int modifiers = method.getModifiers();
            assertTrue(Modifier.isPublic(modifiers));
            assertFalse(Modifier.isFinal(modifiers));
            assertFalse(Modifier.isStatic(modifiers));
            assertSame(void.class, method.getReturnType());
        }

        assertTrue(GlUniformFloat3v.class.getDeclaredMethod("set", Object.class).isBridge());
    }
}
