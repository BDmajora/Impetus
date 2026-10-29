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

// Tests of the integer uniform constructor and both upload methods
class GlUniformIntTest {
    // Message of a location below minus one
    private static final String BAD_LOCATION = "Uniform location must not be less than -1";
    // Number of random locations in the sampled constructor test
    private static final int LOCATION_SAMPLES = 100_000;
    // Number of random values in the sampled upload test
    private static final int VALUE_SAMPLES = 10_000;
    // Seed of the sampled tests
    private static final long SEED = 0x5EED_1A7L;

    // Shared GL mock that the reset extension clears before each test
    private final LWJGLService gl = TestGl.gl();

    // Integer uniform that records every setInt call instead of calling GL
    private static final class Recording extends GlUniformInt {
        // Values in the order setInt received them
        private final List<Integer> values = new ArrayList<>();

        // Creates a recording uniform at the location
        private Recording(int index) {
            super(index);
        }

        // Records the value
        @Override
        public void setInt(int value) {
            this.values.add(value);
        }
    }

    // Returns every value sent to the location in call order and checks that no other GL call happened
    private int[] sent(int location, int calls) {
        ArgumentCaptor<Integer> values = ArgumentCaptor.forClass(Integer.class);
        Mockito.verify(this.gl, Mockito.times(calls)).glUniform1i(Mockito.eq(location), values.capture());
        Mockito.verifyNoMoreInteractions(this.gl);

        return values.getAllValues().stream().mapToInt(Integer::intValue).toArray();
    }

    // Stores every accepted location exactly without a GL call
    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1, 2, 1023, 1024, 65535, 65536, Integer.MAX_VALUE - 1, Integer.MAX_VALUE})
    void constructorStoresEveryAcceptedLocation(int location) {
        assertEquals(location, new GlUniformInt(location).index);
        Mockito.verifyNoInteractions(this.gl);
    }

    // Rejects every location below minus one with the same message and without a GL call
    @ParameterizedTest
    @ValueSource(ints = {-2, -3, -1024, -65536, Integer.MIN_VALUE + 2, Integer.MIN_VALUE + 1, Integer.MIN_VALUE})
    void constructorRejectsEveryLocationBelowMinusOne(int location) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> new GlUniformInt(location));
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
                assertEquals(location, new GlUniformInt(location).index);
            } else {
                assertThrows(IllegalArgumentException.class, () -> new GlUniformInt(location));
            }
        }

        Mockito.verifyNoInteractions(this.gl);
    }

    // Skips the subclass constructor body when the location is rejected
    @Test
    void rejectedConstructionSkipsTheSubclassBody() {
        List<Integer> bodies = new ArrayList<>();

        // Integer uniform that records when its own constructor body runs
        final class Tracked extends GlUniformInt {
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
        first.set(1);
        assertEquals(List.of(1), first.values);
        assertTrue(second.values.isEmpty());
        assertEquals(first.index, second.index);
    }

    // Forwards every boundary value exactly through both upload methods
    @ParameterizedTest
    @ValueSource(ints = {
            0, 1, -1, 2, -2, 15, 16, 31, 32, 127, 128, -128, -129, 255, 256, 32767, 32768, -32768, -32769,
            65535, 65536, 0x00FF_FFFF, 0x0100_0000, 0x0100_0001, 0x4000_0000, 0x8000_0001,
            Integer.MAX_VALUE - 1, Integer.MAX_VALUE, Integer.MIN_VALUE + 1, Integer.MIN_VALUE})
    void bothMethodsForwardBoundaryValuesExactly(int value) {
        GlUniformInt uniform = new GlUniformInt(2);
        uniform.setInt(value);
        uniform.set(value);
        assertArrayEquals(new int[]{value, value}, sent(2, 2));
    }

    // Forwards random values over the whole int range exactly
    @Test
    void setIntForwardsRandomValuesExactly() {
        SplittableRandom random = new SplittableRandom(SEED);
        GlUniformInt uniform = new GlUniformInt(9);
        int[] expected = new int[VALUE_SAMPLES];

        for (int i = 0; i < VALUE_SAMPLES; i++) {
            expected[i] = random.nextInt();
            uniform.setInt(expected[i]);
        }

        assertArrayEquals(expected, sent(9, VALUE_SAMPLES));
    }

    // Forwards wrapped int arithmetic and narrowed long and floating values as the converted int
    @Test
    void overflowedArgumentsArriveAsTheConvertedInt() {
        GlUniformInt uniform = new GlUniformInt(0);
        int max = Integer.MAX_VALUE;
        int min = Integer.MIN_VALUE;
        uniform.setInt(max + 1);
        uniform.setInt(min - 1);
        uniform.setInt(-min);
        uniform.setInt(max * 2);
        uniform.setInt((int) Long.MAX_VALUE);
        uniform.setInt((int) Long.MIN_VALUE);
        uniform.setInt((int) (1L << 32));
        uniform.setInt((int) ((1L << 32) + 5));
        uniform.set((int) 0x8000_0000L);
        uniform.set((int) Double.MAX_VALUE);
        uniform.set((int) -Double.MAX_VALUE);
        uniform.set((int) Float.NaN);
        int[] expected = {min, max, min, -2, -1, 0, 0, 5, min, max, min, 0};
        assertArrayEquals(expected, sent(0, expected.length));
    }

    // Forwards boxed values on both sides of the Integer cache and distinct boxes of one value by value
    @Test
    void boxedValuesArriveByValue() {
        GlUniformInt uniform = new GlUniformInt(3);
        int[] values = {-129, -128, 127, 128, 1000, Integer.MAX_VALUE, Integer.MIN_VALUE};

        for (int value : values) {
            uniform.set(Integer.valueOf(value));
        }

        Integer first = Integer.valueOf(1000);
        Integer second = Integer.valueOf(1000);
        uniform.set(first);
        uniform.set(second);
        int[] expected = Arrays.copyOf(values, values.length + 2);
        expected[values.length] = 1000;
        expected[values.length + 1] = 1000;
        assertArrayEquals(expected, sent(3, expected.length));
    }

    // Rejects a null value before any GL call and stays usable
    @Test
    void setRejectsNullBeforeAnyGlCall() {
        GlUniformInt uniform = new GlUniformInt(4);
        assertThrows(NullPointerException.class, () -> uniform.set(null));
        assertThrows(NullPointerException.class, () -> uniform.set(null));
        Mockito.verifyNoInteractions(this.gl);
        uniform.set(5);
        assertArrayEquals(new int[]{5}, sent(4, 1));
    }

    // Rejects a null value before setInt runs
    @Test
    void setRejectsNullBeforeSetInt() {
        Recording uniform = new Recording(4);
        assertThrows(NullPointerException.class, () -> uniform.set(null));
        assertTrue(uniform.values.isEmpty());
    }

    // Rejects a value of another type through the raw base type before any GL call
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void rawSetRejectsOtherTypesBeforeAnyGlCall() {
        GlUniform raw = new GlUniformInt(1);
        assertThrows(ClassCastException.class, () -> raw.set(1L));
        assertThrows(ClassCastException.class, () -> raw.set(1f));
        assertThrows(ClassCastException.class, () -> raw.set(1.0d));
        assertThrows(ClassCastException.class, () -> raw.set((short) 1));
        assertThrows(ClassCastException.class, () -> raw.set((byte) 1));
        assertThrows(ClassCastException.class, () -> raw.set('1'));
        assertThrows(ClassCastException.class, () -> raw.set(Boolean.TRUE));
        assertThrows(ClassCastException.class, () -> raw.set("1"));
        assertThrows(ClassCastException.class, () -> raw.set(new int[]{1}));
        assertThrows(ClassCastException.class, () -> raw.set(new AtomicInteger(1)));
        assertThrows(NullPointerException.class, () -> raw.set(null));
        Mockito.verifyNoInteractions(this.gl);
        raw.set(1);
        assertArrayEquals(new int[]{1}, sent(1, 1));
    }

    // Dispatches through the base type to the integer upload
    @Test
    void setThroughTheBaseTypeReachesGl() {
        GlUniform<Integer> uniform = new GlUniformInt(6);
        uniform.set(-6);
        assertArrayEquals(new int[]{-6}, sent(6, 1));
    }

    // Routes the boxed upload through setInt
    @Test
    void setDelegatesToSetInt() {
        Recording uniform = new Recording(3);
        uniform.set(2);
        uniform.set(Integer.MIN_VALUE);
        uniform.set(Integer.MAX_VALUE);
        assertEquals(List.of(2, Integer.MIN_VALUE, Integer.MAX_VALUE), uniform.values);
        Mockito.verifyNoInteractions(this.gl);
    }

    // Sends every repeated value again in call order
    @Test
    void repeatedCallsSendEveryValue() {
        GlUniformInt uniform = new GlUniformInt(Integer.MAX_VALUE);
        int[] expected = new int[1003];

        for (int i = 0; i < 1000; i++) {
            uniform.setInt(i);
            expected[i] = i;
        }

        Integer shared = 7;
        uniform.set(shared);
        uniform.set(shared);
        uniform.setInt(shared);
        Arrays.fill(expected, 1000, 1003, 7);
        assertArrayEquals(expected, sent(Integer.MAX_VALUE, 1003));
    }

    // Keeps each instance on its own location when calls interleave
    @Test
    void interleavedInstancesKeepTheirLocations() {
        GlUniformInt first = new GlUniformInt(7);
        GlUniformInt second = new GlUniformInt(7);
        GlUniformInt other = new GlUniformInt(8);
        first.setInt(1);
        other.setInt(2);
        second.set(3);
        first.set(4);
        other.set(5);

        InOrder order = Mockito.inOrder(this.gl);
        order.verify(this.gl).glUniform1i(7, 1);
        order.verify(this.gl).glUniform1i(8, 2);
        order.verify(this.gl).glUniform1i(7, 3);
        order.verify(this.gl).glUniform1i(7, 4);
        order.verify(this.gl).glUniform1i(8, 5);
        Mockito.verifyNoMoreInteractions(this.gl);
    }

    // Forwards minus one to GL from both upload methods
    @Test
    void minusOneLocationStillReachesGl() {
        GlUniformInt uniform = new GlUniformInt(-1);
        uniform.setInt(3);
        uniform.set(-3);
        assertArrayEquals(new int[]{3, -3}, sent(-1, 2));
    }

    // Uses only the integer upload and never a float or vector upload
    @Test
    void uploadsUseOnlyTheIntegerEntryPoint() {
        GlUniformInt uniform = new GlUniformInt(11);
        uniform.setInt(1);
        uniform.set(2);
        Mockito.verify(this.gl, Mockito.never()).glUniform1f(Mockito.anyInt(), Mockito.anyFloat());
        assertArrayEquals(new int[]{1, 2}, sent(11, 2));
    }

    // Passes a GL failure through unchanged from both upload methods and stays usable
    @Test
    void glFailurePassesThrough() {
        IllegalStateException failure = new IllegalStateException("lost context");
        Mockito.doThrow(failure).doThrow(failure).doNothing().when(this.gl).glUniform1i(Mockito.anyInt(), Mockito.anyInt());
        GlUniformInt uniform = new GlUniformInt(5);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> uniform.setInt(1)));
        assertSame(failure, assertThrows(IllegalStateException.class, () -> uniform.set(2)));
        uniform.setInt(3);
        assertArrayEquals(new int[]{1, 2, 3}, sent(5, 3));
        assertEquals(5, uniform.index);
    }

    // Passes a GL error through unchanged
    @Test
    void glErrorPassesThrough() {
        StackOverflowError failure = new StackOverflowError();
        Mockito.doThrow(failure).when(this.gl).glUniform1i(Mockito.anyInt(), Mockito.anyInt());
        GlUniformInt uniform = new GlUniformInt(0);
        assertSame(failure, assertThrows(StackOverflowError.class, () -> uniform.set(1)));
    }

    // Wraps the location GL reports through the program binding
    @ParameterizedTest
    @ValueSource(ints = {0, 1, Integer.MAX_VALUE})
    void programBindingWrapsTheReportedLocation(int location) {
        Mockito.doReturn(location).when(this.gl).glGetUniformLocation(Mockito.anyInt(), Mockito.eq("u_Value"));
        GlProgram<GlUniformInt> program = GlProgram.builder("uniform").link(context -> context.bindUniform("u_Value", GlUniformInt::new));
        GlUniformInt uniform = program.getInterface();
        assertEquals(location, uniform.index);
        Mockito.clearInvocations(this.gl);
        uniform.setInt(12);
        assertArrayEquals(new int[]{12}, sent(location, 1));
    }

    // Creates no uniform when the program lacks it
    @ParameterizedTest
    @ValueSource(ints = {-1, -2, Integer.MIN_VALUE})
    void missingUniformCreatesNoInstance(int location) {
        Mockito.doReturn(location).when(this.gl).glGetUniformLocation(Mockito.anyInt(), Mockito.eq("u_Value"));
        AtomicInteger created = new AtomicInteger();
        IntFunction<GlUniformInt> factory = index -> {
            created.incrementAndGet();

            return new GlUniformInt(index);
        };

        GlProgram<GlUniformInt> program = GlProgram.builder("uniform").link(context -> context.bindUniformIfPresent("u_Value", factory));
        assertNull(program.getInterface());
        assertEquals(0, created.get());
        Mockito.verify(this.gl, Mockito.never()).glUniform1i(Mockito.anyInt(), Mockito.anyInt());
    }

    // Keeps forwarding the location after the program is deleted
    @Test
    void uniformOutlivesItsProgram() {
        Mockito.doReturn(5).when(this.gl).glGetUniformLocation(Mockito.anyInt(), Mockito.eq("u_Value"));
        GlProgram<GlUniformInt> program = GlProgram.builder("uniform").link(context -> context.bindUniform("u_Value", GlUniformInt::new));
        GlUniformInt uniform = program.getInterface();
        program.delete();
        Mockito.clearInvocations(this.gl);
        uniform.setInt(8);
        uniform.set(8);
        assertArrayEquals(new int[]{8, 8}, sent(5, 2));
    }

    // Keeps the class public and concrete and open to subclasses over an Integer type argument
    @Test
    void classShape() {
        int modifiers = GlUniformInt.class.getModifiers();
        assertTrue(Modifier.isPublic(modifiers));
        assertFalse(Modifier.isAbstract(modifiers));
        assertFalse(Modifier.isFinal(modifiers));
        ParameterizedType type = (ParameterizedType) GlUniformInt.class.getGenericSuperclass();
        assertSame(GlUniform.class, type.getRawType());
        assertArrayEquals(new Type[]{Integer.class}, type.getActualTypeArguments());
        assertEquals(0, GlUniformInt.class.getInterfaces().length);
    }

    // Keeps one public constructor that takes the location
    @Test
    void constructorShape() {
        Constructor<?>[] constructors = GlUniformInt.class.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        assertTrue(Modifier.isPublic(constructors[0].getModifiers()));
        assertArrayEquals(new Class<?>[]{int.class}, constructors[0].getParameterTypes());
    }

    // Keeps no fields of its own
    @Test
    void fieldShape() {
        Field[] fields = Arrays.stream(GlUniformInt.class.getDeclaredFields()).filter(field -> !field.isSynthetic()).toArray(Field[]::new);
        assertEquals(0, fields.length);
    }

    // Keeps both upload methods public and void and open to overrides
    @Test
    void methodShape() throws NoSuchMethodException {
        Method[] methods = Arrays.stream(GlUniformInt.class.getDeclaredMethods()).filter(method -> !method.isSynthetic()).toArray(Method[]::new);
        assertEquals(2, methods.length);

        for (Method method : new Method[]{
                GlUniformInt.class.getDeclaredMethod("set", Integer.class),
                GlUniformInt.class.getDeclaredMethod("setInt", int.class)}) {
            int modifiers = method.getModifiers();
            assertTrue(Modifier.isPublic(modifiers));
            assertFalse(Modifier.isFinal(modifiers));
            assertFalse(Modifier.isStatic(modifiers));
            assertSame(void.class, method.getReturnType());
        }

        assertTrue(GlUniformInt.class.getDeclaredMethod("set", Object.class).isBridge());
    }
}
