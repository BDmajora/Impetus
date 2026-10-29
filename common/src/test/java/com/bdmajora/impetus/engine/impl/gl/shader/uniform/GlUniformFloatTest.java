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

// Tests of the float uniform constructor and both upload methods
class GlUniformFloatTest {
    // Message of a location below minus one
    private static final String BAD_LOCATION = "Uniform location must not be less than -1";
    // Number of random locations in the sampled constructor test
    private static final int LOCATION_SAMPLES = 100_000;
    // Number of random bit patterns in the sampled upload test
    private static final int VALUE_SAMPLES = 10_000;
    // Seed of the sampled tests
    private static final long SEED = 0x5EED_F10AL;
    // Exponent bits of a float
    private static final int EXPONENT = 0x7F80_0000;
    // Mantissa bits of a float
    private static final int MANTISSA = 0x007F_FFFF;
    // Mantissa bit that marks a NaN as quiet
    private static final int QUIET = 0x0040_0000;

    // Shared GL mock that the reset extension clears before each test
    private final LWJGLService gl = TestGl.gl();

    // Float uniform that records every setFloat call instead of calling GL
    private static final class Recording extends GlUniformFloat {
        // Values in the order setFloat received them
        private final List<Float> values = new ArrayList<>();

        // Creates a recording uniform at the location
        private Recording(int index) {
            super(index);
        }

        // Records the value
        @Override
        public void setFloat(float value) {
            this.values.add(value);
        }
    }

    // Returns the raw bits of every value sent to the location in call order and checks that no other GL call happened
    private int[] sentBits(int location, int calls) {
        ArgumentCaptor<Float> values = ArgumentCaptor.forClass(Float.class);
        Mockito.verify(this.gl, Mockito.times(calls)).glUniform1f(Mockito.eq(location), values.capture());
        Mockito.verifyNoMoreInteractions(this.gl);

        return values.getAllValues().stream().mapToInt(Float::floatToRawIntBits).toArray();
    }

    // Stores every accepted location exactly without a GL call
    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1, 2, 1023, 1024, 65535, 65536, Integer.MAX_VALUE - 1, Integer.MAX_VALUE})
    void constructorStoresEveryAcceptedLocation(int location) {
        assertEquals(location, new GlUniformFloat(location).index);
        Mockito.verifyNoInteractions(this.gl);
    }

    // Rejects every location below minus one with the same message and without a GL call
    @ParameterizedTest
    @ValueSource(ints = {-2, -3, -1024, -65536, Integer.MIN_VALUE + 2, Integer.MIN_VALUE + 1, Integer.MIN_VALUE})
    void constructorRejectsEveryLocationBelowMinusOne(int location) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> new GlUniformFloat(location));
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
                assertEquals(location, new GlUniformFloat(location).index);
            } else {
                assertThrows(IllegalArgumentException.class, () -> new GlUniformFloat(location));
            }
        }

        Mockito.verifyNoInteractions(this.gl);
    }

    // Skips the subclass constructor body when the location is rejected
    @Test
    void rejectedConstructionSkipsTheSubclassBody() {
        List<Integer> bodies = new ArrayList<>();

        // Float uniform that records when its own constructor body runs
        final class Tracked extends GlUniformFloat {
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
        first.set(1f);
        assertEquals(List.of(1f), first.values);
        assertTrue(second.values.isEmpty());
        assertEquals(first.index, second.index);
    }

    // Forwards every boundary bit pattern exactly through both upload methods
    @ParameterizedTest
    @ValueSource(ints = {
            0x0000_0000, 0x8000_0000, 0x0000_0001, 0x8000_0001, 0x007F_FFFF, 0x807F_FFFF, 0x0080_0000, 0x8080_0000,
            0x3F80_0000, 0xBF80_0000, 0x4B80_0000, 0x4B80_0001, 0x7F7F_FFFF, 0xFF7F_FFFF, 0x7F80_0000, 0xFF80_0000,
            0x7FC0_0000, 0x7FC0_0001, 0x7FFF_FFFF, 0xFFC0_0000, 0xFFFF_FFFF})
    void bothMethodsForwardBoundaryBitsExactly(int bits) {
        GlUniformFloat uniform = new GlUniformFloat(2);
        float value = Float.intBitsToFloat(bits);
        uniform.setFloat(value);
        uniform.set(value);
        assertArrayEquals(new int[]{bits, bits}, sentBits(2, 2));
    }

    // Forwards random bit patterns exactly with every NaN made quiet
    @Test
    void setFloatForwardsRandomBitPatternsExactly() {
        SplittableRandom random = new SplittableRandom(SEED);
        GlUniformFloat uniform = new GlUniformFloat(9);
        int[] expected = new int[VALUE_SAMPLES];

        for (int i = 0; i < VALUE_SAMPLES; i++) {
            int bits = random.nextInt();

            if ((bits & EXPONENT) == EXPONENT && (bits & MANTISSA) != 0) {
                bits |= QUIET;
            }

            expected[i] = bits;
            uniform.setFloat(Float.intBitsToFloat(bits));
        }

        assertArrayEquals(expected, sentBits(9, VALUE_SAMPLES));
    }

    // Forwards the int and long limits as the nearest float and double overflow as infinity
    @Test
    void numericLimitsArriveAsTheConvertedFloat() {
        GlUniformFloat uniform = new GlUniformFloat(0);
        uniform.setFloat(Integer.MAX_VALUE);
        uniform.setFloat(Integer.MIN_VALUE);
        uniform.setFloat(Long.MAX_VALUE);
        uniform.setFloat(Long.MIN_VALUE);
        uniform.set((float) Double.MAX_VALUE);
        uniform.set((float) -Double.MAX_VALUE);
        uniform.set((float) Double.MIN_VALUE);
        uniform.set((float) -Double.MIN_VALUE);
        int[] expected = {0x4F00_0000, 0xCF00_0000, 0x5F00_0000, 0xDF00_0000, 0x7F80_0000, 0xFF80_0000, 0x0000_0000, 0x8000_0000};
        assertArrayEquals(expected, sentBits(0, expected.length));
    }

    // Rejects a null value before any GL call and stays usable
    @Test
    void setRejectsNullBeforeAnyGlCall() {
        GlUniformFloat uniform = new GlUniformFloat(4);
        assertThrows(NullPointerException.class, () -> uniform.set(null));
        assertThrows(NullPointerException.class, () -> uniform.set(null));
        Mockito.verifyNoInteractions(this.gl);
        uniform.set(0.5f);
        assertArrayEquals(new int[]{Float.floatToRawIntBits(0.5f)}, sentBits(4, 1));
    }

    // Rejects a null value before setFloat runs
    @Test
    void setRejectsNullBeforeSetFloat() {
        Recording uniform = new Recording(4);
        assertThrows(NullPointerException.class, () -> uniform.set(null));
        assertTrue(uniform.values.isEmpty());
    }

    // Rejects a value of another type through the raw base type before any GL call
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void rawSetRejectsOtherTypesBeforeAnyGlCall() {
        GlUniform raw = new GlUniformFloat(1);
        assertThrows(ClassCastException.class, () -> raw.set(1.0d));
        assertThrows(ClassCastException.class, () -> raw.set(1));
        assertThrows(ClassCastException.class, () -> raw.set("1"));
        assertThrows(ClassCastException.class, () -> raw.set(new float[]{1f}));
        assertThrows(NullPointerException.class, () -> raw.set(null));
        Mockito.verifyNoInteractions(this.gl);
        raw.set(1f);
        assertArrayEquals(new int[]{Float.floatToRawIntBits(1f)}, sentBits(1, 1));
    }

    // Dispatches through the base type to the float upload
    @Test
    void setThroughTheBaseTypeReachesGl() {
        GlUniform<Float> uniform = new GlUniformFloat(6);
        uniform.set(0.25f);
        assertArrayEquals(new int[]{Float.floatToRawIntBits(0.25f)}, sentBits(6, 1));
    }

    // Routes the boxed upload through setFloat
    @Test
    void setDelegatesToSetFloat() {
        Recording uniform = new Recording(3);
        uniform.set(2.5f);
        uniform.set(-0f);
        uniform.set(Float.NaN);
        assertEquals(List.of(2.5f, -0f, Float.NaN), uniform.values);
        Mockito.verifyNoInteractions(this.gl);
    }

    // Sends every repeated value again in call order
    @Test
    void repeatedCallsSendEveryValue() {
        GlUniformFloat uniform = new GlUniformFloat(Integer.MAX_VALUE);
        int[] expected = new int[1003];

        for (int i = 0; i < 1000; i++) {
            uniform.setFloat(i);
            expected[i] = Float.floatToRawIntBits(i);
        }

        Float shared = 7f;
        uniform.set(shared);
        uniform.set(shared);
        uniform.setFloat(shared);
        Arrays.fill(expected, 1000, 1003, Float.floatToRawIntBits(7f));
        assertArrayEquals(expected, sentBits(Integer.MAX_VALUE, 1003));
    }

    // Keeps each instance on its own location when calls interleave
    @Test
    void interleavedInstancesKeepTheirLocations() {
        GlUniformFloat first = new GlUniformFloat(7);
        GlUniformFloat second = new GlUniformFloat(7);
        GlUniformFloat other = new GlUniformFloat(8);
        first.setFloat(1f);
        other.setFloat(2f);
        second.set(3f);
        first.set(4f);
        other.set(5f);

        InOrder order = Mockito.inOrder(this.gl);
        order.verify(this.gl).glUniform1f(7, 1f);
        order.verify(this.gl).glUniform1f(8, 2f);
        order.verify(this.gl).glUniform1f(7, 3f);
        order.verify(this.gl).glUniform1f(7, 4f);
        order.verify(this.gl).glUniform1f(8, 5f);
        Mockito.verifyNoMoreInteractions(this.gl);
    }

    // Forwards minus one to GL from both upload methods
    @Test
    void minusOneLocationStillReachesGl() {
        GlUniformFloat uniform = new GlUniformFloat(-1);
        uniform.setFloat(3f);
        uniform.set(-3f);
        int[] expected = {Float.floatToRawIntBits(3f), Float.floatToRawIntBits(-3f)};
        assertArrayEquals(expected, sentBits(-1, 2));
    }

    // Passes a GL failure through unchanged from both upload methods and stays usable
    @Test
    void glFailurePassesThrough() {
        IllegalStateException failure = new IllegalStateException("lost context");
        Mockito.doThrow(failure).doThrow(failure).doNothing().when(this.gl).glUniform1f(Mockito.anyInt(), Mockito.anyFloat());
        GlUniformFloat uniform = new GlUniformFloat(5);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> uniform.setFloat(1f)));
        assertSame(failure, assertThrows(IllegalStateException.class, () -> uniform.set(2f)));
        uniform.setFloat(3f);
        int[] expected = {Float.floatToRawIntBits(1f), Float.floatToRawIntBits(2f), Float.floatToRawIntBits(3f)};
        assertArrayEquals(expected, sentBits(5, 3));
        assertEquals(5, uniform.index);
    }

    // Passes a GL error through unchanged
    @Test
    void glErrorPassesThrough() {
        StackOverflowError failure = new StackOverflowError();
        Mockito.doThrow(failure).when(this.gl).glUniform1f(Mockito.anyInt(), Mockito.anyFloat());
        GlUniformFloat uniform = new GlUniformFloat(0);
        assertSame(failure, assertThrows(StackOverflowError.class, () -> uniform.set(1f)));
    }

    // Wraps the location GL reports through the program binding
    @ParameterizedTest
    @ValueSource(ints = {0, 1, Integer.MAX_VALUE})
    void programBindingWrapsTheReportedLocation(int location) {
        Mockito.doReturn(location).when(this.gl).glGetUniformLocation(Mockito.anyInt(), Mockito.eq("u_Value"));
        GlProgram<GlUniformFloat> program = GlProgram.builder("uniform").link(context -> context.bindUniform("u_Value", GlUniformFloat::new));
        GlUniformFloat uniform = program.getInterface();
        assertEquals(location, uniform.index);
        Mockito.clearInvocations(this.gl);
        uniform.setFloat(0.75f);
        assertArrayEquals(new int[]{Float.floatToRawIntBits(0.75f)}, sentBits(location, 1));
    }

    // Creates no uniform when the program lacks it
    @ParameterizedTest
    @ValueSource(ints = {-1, -2, Integer.MIN_VALUE})
    void missingUniformCreatesNoInstance(int location) {
        Mockito.doReturn(location).when(this.gl).glGetUniformLocation(Mockito.anyInt(), Mockito.eq("u_Value"));
        AtomicInteger created = new AtomicInteger();
        IntFunction<GlUniformFloat> factory = index -> {
            created.incrementAndGet();

            return new GlUniformFloat(index);
        };

        GlProgram<GlUniformFloat> program = GlProgram.builder("uniform").link(context -> context.bindUniformIfPresent("u_Value", factory));
        assertNull(program.getInterface());
        assertEquals(0, created.get());
        Mockito.verify(this.gl, Mockito.never()).glUniform1f(Mockito.anyInt(), Mockito.anyFloat());
    }

    // Keeps forwarding the location after the program is deleted
    @Test
    void uniformOutlivesItsProgram() {
        Mockito.doReturn(5).when(this.gl).glGetUniformLocation(Mockito.anyInt(), Mockito.eq("u_Value"));
        GlProgram<GlUniformFloat> program = GlProgram.builder("uniform").link(context -> context.bindUniform("u_Value", GlUniformFloat::new));
        GlUniformFloat uniform = program.getInterface();
        program.delete();
        Mockito.clearInvocations(this.gl);
        uniform.setFloat(8f);
        uniform.set(8f);
        assertArrayEquals(new int[]{Float.floatToRawIntBits(8f), Float.floatToRawIntBits(8f)}, sentBits(5, 2));
    }

    // Keeps the class public and concrete and open to subclasses over a Float type argument
    @Test
    void classShape() {
        int modifiers = GlUniformFloat.class.getModifiers();
        assertTrue(Modifier.isPublic(modifiers));
        assertFalse(Modifier.isAbstract(modifiers));
        assertFalse(Modifier.isFinal(modifiers));
        ParameterizedType type = (ParameterizedType) GlUniformFloat.class.getGenericSuperclass();
        assertSame(GlUniform.class, type.getRawType());
        assertArrayEquals(new java.lang.reflect.Type[]{Float.class}, type.getActualTypeArguments());
        assertEquals(0, GlUniformFloat.class.getInterfaces().length);
    }

    // Keeps one public constructor that takes the location
    @Test
    void constructorShape() {
        Constructor<?>[] constructors = GlUniformFloat.class.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        assertTrue(Modifier.isPublic(constructors[0].getModifiers()));
        assertArrayEquals(new Class<?>[]{int.class}, constructors[0].getParameterTypes());
    }

    // Keeps no fields of its own
    @Test
    void fieldShape() {
        Field[] fields = Arrays.stream(GlUniformFloat.class.getDeclaredFields()).filter(field -> !field.isSynthetic()).toArray(Field[]::new);
        assertEquals(0, fields.length);
    }

    // Keeps both upload methods public and void and open to overrides
    @Test
    void methodShape() throws NoSuchMethodException {
        Method[] methods = Arrays.stream(GlUniformFloat.class.getDeclaredMethods()).filter(method -> !method.isSynthetic()).toArray(Method[]::new);
        assertEquals(2, methods.length);

        for (Method method : new Method[]{
                GlUniformFloat.class.getDeclaredMethod("set", Float.class),
                GlUniformFloat.class.getDeclaredMethod("setFloat", float.class)}) {
            int modifiers = method.getModifiers();
            assertTrue(Modifier.isPublic(modifiers));
            assertFalse(Modifier.isFinal(modifiers));
            assertFalse(Modifier.isStatic(modifiers));
            assertSame(void.class, method.getReturnType());
        }

        assertTrue(GlUniformFloat.class.getDeclaredMethod("set", Object.class).isBridge());
    }
}
