package com.bdmajora.impetus.engine.impl.gl.shader.uniform;

import com.bdmajora.impetus.engine.impl.gl.shader.GlProgram;
import com.bdmajora.impetus.lwjgl.LWJGLService;
import com.bdmajora.testing.TestGl;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.TypeVariable;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;
import java.util.function.IntFunction;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Tests of the uniform base class alone and through every production subclass
class GlUniformTest {
    // Message of a location below minus one
    private static final String BAD_LOCATION = "Uniform location must not be less than -1";
    // Number of random locations in the sampled tests
    private static final int SAMPLES = 100_000;
    // Seed of the sampled tests
    private static final long SEED = 0x5EED_6C55L;

    // Shared GL mock that the reset extension clears before each test
    private final LWJGLService gl = TestGl.gl();

    // Uniform that records every value handed to set
    private static final class Recording extends GlUniform<Object> {
        // Values in the order set received them
        private final List<Object> values = new ArrayList<>();

        // Creates a recording uniform at the location
        private Recording(int index) {
            super(index);
        }

        // Records the value
        @Override
        public void set(Object value) {
            this.values.add(value);
        }
    }

    // Uniform whose set fails with the given error
    private static final class Failing extends GlUniform<String> {
        // Error thrown by every set
        private final RuntimeException error;

        // Creates a failing uniform at the location
        private Failing(int index, RuntimeException error) {
            super(index);
            this.error = error;
        }

        // Throws the stored error
        @Override
        public void set(String value) {
            throw this.error;
        }
    }

    // Returns every production subclass constructor with its name
    static Stream<Arguments> factories() {
        return Stream.of(
                Arguments.of("GlUniformFloat", (IntFunction<GlUniform<?>>) GlUniformFloat::new),
                Arguments.of("GlUniformInt", (IntFunction<GlUniform<?>>) GlUniformInt::new),
                Arguments.of("GlUniformFloat3v", (IntFunction<GlUniform<?>>) GlUniformFloat3v::new),
                Arguments.of("GlUniformFloat4v", (IntFunction<GlUniform<?>>) GlUniformFloat4v::new),
                Arguments.of("GlUniformFloatArray", (IntFunction<GlUniform<?>>) GlUniformFloatArray::new),
                Arguments.of("GlUniformMatrix3f", (IntFunction<GlUniform<?>>) GlUniformMatrix3f::new),
                Arguments.of("GlUniformMatrix4f", (IntFunction<GlUniform<?>>) GlUniformMatrix4f::new));
    }

    // Returns every production subclass constructor paired with each accepted boundary location
    static Stream<Arguments> factoriesAtAcceptedLocations() {
        return factories().flatMap(factory -> Arrays.stream(new int[]{-1, 0, 1, Integer.MAX_VALUE - 1, Integer.MAX_VALUE})
                .mapToObj(location -> Arguments.of(factory.get()[0], factory.get()[1], location)));
    }

    // Returns every production subclass constructor paired with each rejected boundary location
    static Stream<Arguments> factoriesAtRejectedLocations() {
        return factories().flatMap(factory -> Arrays.stream(new int[]{-2, -3, Integer.MIN_VALUE + 1, Integer.MIN_VALUE})
                .mapToObj(location -> Arguments.of(factory.get()[0], factory.get()[1], location)));
    }

    // Stores every accepted location exactly
    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1, 2, 1023, 1024, 65535, 65536, Integer.MAX_VALUE - 1, Integer.MAX_VALUE})
    void constructorStoresTheLocation(int location) {
        assertEquals(location, new Recording(location).index);
    }

    // Rejects every location below minus one with the same message
    @ParameterizedTest
    @ValueSource(ints = {-2, -3, -1024, -65536, Integer.MIN_VALUE + 2, Integer.MIN_VALUE + 1, Integer.MIN_VALUE})
    void constructorRejectsALocationBelowMinusOne(int location) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> new Recording(location));
        assertEquals(BAD_LOCATION, error.getMessage());
        assertNull(error.getCause());
    }

    // Accepts a location exactly when it is at least minus one over random samples of the whole int range
    @Test
    void constructorAcceptsExactlyTheLocationsFromMinusOneUp() {
        SplittableRandom random = new SplittableRandom(SEED);

        for (int i = 0; i < SAMPLES; i++) {
            int location = random.nextInt();

            if (location >= -1) {
                assertEquals(location, new Recording(location).index);
            } else {
                assertThrows(IllegalArgumentException.class, () -> new Recording(location));
            }
        }
    }

    // Makes no GL call while storing or rejecting a location
    @Test
    void constructorMakesNoGlCall() {
        new Recording(-1);
        new Recording(0);
        new Recording(Integer.MAX_VALUE);
        assertThrows(IllegalArgumentException.class, () -> new Recording(-2));
        assertThrows(IllegalArgumentException.class, () -> new Recording(Integer.MIN_VALUE));
        Mockito.verifyNoInteractions(gl);
    }

    // Skips the subclass constructor body when the location is rejected
    @Test
    void rejectedConstructionSkipsTheSubclassBody() {
        List<Integer> bodies = new ArrayList<>();

        // Uniform that records when its own constructor body runs
        final class Tracked extends GlUniform<Void> {
            // Creates a tracked uniform and records its location
            Tracked(int index) {
                super(index);
                bodies.add(index);
            }

            // Ignores the value
            @Override
            public void set(Void value) {
            }
        }

        assertThrows(IllegalArgumentException.class, () -> new Tracked(-2));
        assertTrue(bodies.isEmpty());
        new Tracked(-1);
        assertEquals(List.of(-1), bodies);
    }

    // Creates independent instances for the same location
    @Test
    void repeatedConstructionCreatesIndependentInstances() {
        Recording first = new Recording(7);
        Recording second = new Recording(7);
        assertNotSame(first, second);
        first.set("a");
        assertEquals(List.of("a"), first.values);
        assertTrue(second.values.isEmpty());
        assertEquals(first.index, second.index);
    }

    // Creates a uniform after a rejected location
    @Test
    void constructorRecoversAfterARejection() {
        assertThrows(IllegalArgumentException.class, () -> new Recording(-2));
        assertEquals(3, new Recording(3).index);
    }

    // Hands null to the subclass unchanged
    @Test
    void setPassesNullToTheSubclass() {
        Recording uniform = new Recording(0);
        uniform.set(null);
        assertEquals(1, uniform.values.size());
        assertNull(uniform.values.get(0));
    }

    // Hands every value to the subclass in order and keeps the location across repeated calls
    @Test
    void repeatedSetCallsReachTheSubclassInOrder() {
        Recording uniform = new Recording(Integer.MAX_VALUE);
        Object shared = new Object();

        for (int i = 0; i < 1000; i++) {
            uniform.set(i);
        }

        uniform.set(shared);
        uniform.set(shared);
        assertEquals(1002, uniform.values.size());

        for (int i = 0; i < 1000; i++) {
            assertEquals(i, uniform.values.get(i));
        }

        assertSame(shared, uniform.values.get(1000));
        assertSame(shared, uniform.values.get(1001));
        assertEquals(Integer.MAX_VALUE, uniform.index);
    }

    // Passes a failure from the subclass through unchanged and stays usable
    @Test
    void setPassesSubclassFailuresThrough() {
        IllegalStateException failure = new IllegalStateException("boom");
        Failing uniform = new Failing(4, failure);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> uniform.set("x")));
        assertSame(failure, assertThrows(IllegalStateException.class, () -> uniform.set(null)));
        assertEquals(4, uniform.index);
    }

    // Dispatches through the base type to the subclass override
    @Test
    void setDispatchesThroughTheBaseType() {
        GlUniform<Object> uniform = new Recording(1);
        uniform.set("value");
        assertEquals(List.of("value"), ((Recording) uniform).values);
    }

    // Stores the accepted boundary locations in every production subclass
    @ParameterizedTest(name = "{0} at {2}")
    @MethodSource("factoriesAtAcceptedLocations")
    void everySubclassStoresTheLocation(String name, IntFunction<GlUniform<?>> factory, int location) {
        assertEquals(location, factory.apply(location).index);
        Mockito.verifyNoInteractions(gl);
    }

    // Rejects the boundary locations below minus one in every production subclass
    @ParameterizedTest(name = "{0} at {2}")
    @MethodSource("factoriesAtRejectedLocations")
    void everySubclassRejectsALocationBelowMinusOne(String name, IntFunction<GlUniform<?>> factory, int location) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> factory.apply(location));
        assertEquals(BAD_LOCATION, error.getMessage());
        Mockito.verifyNoInteractions(gl);
    }

    // Forwards minus one to GL from every production subclass
    @Test
    void minusOneReachesGlFromEverySubclass() {
        new GlUniformFloat(-1).set(1.5f);
        Mockito.verify(gl).glUniform1f(-1, 1.5f);
        new GlUniformInt(-1).set(3);
        Mockito.verify(gl).glUniform1i(-1, 3);
        new GlUniformFloat3v(-1).set(new float[]{1, 2, 3});
        Mockito.verify(gl).glUniform3f(-1, 1, 2, 3);
        new GlUniformFloat4v(-1).set(new float[]{1, 2, 3, 4});
        Mockito.verify(gl).glUniform4f(-1, 1, 2, 3, 4);
        float[] elements = {5};
        new GlUniformFloatArray(-1).set(elements);
        Mockito.verify(gl).glUniform1fv(Mockito.eq(-1), Mockito.same(elements));
        new GlUniformMatrix3f(-1).set(new Matrix3f());
        Mockito.verify(gl).glUniformMatrix3fv(Mockito.eq(-1), Mockito.eq(false), Mockito.any(FloatBuffer.class));
        new GlUniformMatrix4f(-1).set(new Matrix4f());
        Mockito.verify(gl).glUniformMatrix4fv(Mockito.eq(-1), Mockito.eq(false), Mockito.any(FloatBuffer.class));
    }

    // Forwards the int limit location to GL unchanged
    @Test
    void maximumLocationReachesGlUnchanged() {
        new GlUniformInt(Integer.MAX_VALUE).set(Integer.MIN_VALUE);
        Mockito.verify(gl).glUniform1i(Integer.MAX_VALUE, Integer.MIN_VALUE);
        new GlUniformFloat(Integer.MAX_VALUE).set(Float.NaN);
        Mockito.verify(gl).glUniform1f(Integer.MAX_VALUE, Float.NaN);
    }

    // Wraps the location GL reports through the program binding
    @ParameterizedTest
    @ValueSource(ints = {0, 1, Integer.MAX_VALUE})
    void programBindingStoresTheReportedLocation(int location) {
        Mockito.doReturn(location).when(gl).glGetUniformLocation(Mockito.anyInt(), Mockito.eq("u_Value"));
        GlProgram<GlUniformInt> program = GlProgram.builder("uniform").link(context -> context.bindUniform("u_Value", GlUniformInt::new));
        assertEquals(location, program.getInterface().index);
    }

    // Keeps the location and forwards it after the program is deleted
    @Test
    void uniformOutlivesItsProgram() {
        Mockito.doReturn(5).when(gl).glGetUniformLocation(Mockito.anyInt(), Mockito.eq("u_Value"));
        GlProgram<GlUniformInt> program = GlProgram.builder("uniform").link(context -> context.bindUniform("u_Value", GlUniformInt::new));
        GlUniformInt uniform = program.getInterface();
        program.delete();
        uniform.set(8);
        uniform.set(8);
        assertEquals(5, uniform.index);
        Mockito.verify(gl, Mockito.times(2)).glUniform1i(5, 8);
    }

    // Keeps the class abstract and public with one type parameter
    @Test
    void classShape() {
        int modifiers = GlUniform.class.getModifiers();
        assertTrue(Modifier.isPublic(modifiers));
        assertTrue(Modifier.isAbstract(modifiers));
        TypeVariable<?>[] parameters = GlUniform.class.getTypeParameters();
        assertEquals(1, parameters.length);
        assertEquals("T", parameters[0].getName());
        assertSame(Object.class, GlUniform.class.getSuperclass());
        assertEquals(0, GlUniform.class.getInterfaces().length);
    }

    // Keeps one protected constructor that takes the location
    @Test
    void constructorShape() {
        Constructor<?>[] constructors = GlUniform.class.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        assertTrue(Modifier.isProtected(constructors[0].getModifiers()));
        assertArrayEquals(new Class<?>[]{int.class}, constructors[0].getParameterTypes());
    }

    // Keeps the location in one protected final int field
    @Test
    void fieldShape() throws NoSuchFieldException {
        Field[] fields = Arrays.stream(GlUniform.class.getDeclaredFields()).filter(field -> !field.isSynthetic()).toArray(Field[]::new);
        assertEquals(1, fields.length);
        Field index = GlUniform.class.getDeclaredField("index");
        assertSame(int.class, index.getType());
        int modifiers = index.getModifiers();
        assertTrue(Modifier.isProtected(modifiers));
        assertTrue(Modifier.isFinal(modifiers));
        assertFalse(Modifier.isStatic(modifiers));
    }

    // Keeps set public and abstract over the type parameter
    @Test
    void setShape() throws NoSuchMethodException {
        Method[] methods = Arrays.stream(GlUniform.class.getDeclaredMethods()).filter(method -> !method.isSynthetic()).toArray(Method[]::new);
        assertEquals(1, methods.length);
        Method set = GlUniform.class.getDeclaredMethod("set", Object.class);
        int modifiers = set.getModifiers();
        assertTrue(Modifier.isPublic(modifiers));
        assertTrue(Modifier.isAbstract(modifiers));
        assertSame(void.class, set.getReturnType());
        assertEquals("T", set.getGenericParameterTypes()[0].getTypeName());
    }

    // Checks the type argument each production subclass gives the base class
    @Test
    void subclassTypeArguments() {
        assertEquals(Float.class, typeArgument(GlUniformFloat.class));
        assertEquals(Integer.class, typeArgument(GlUniformInt.class));
        assertEquals(float[].class, typeArgument(GlUniformFloat3v.class));
        assertEquals(float[].class, typeArgument(GlUniformFloat4v.class));
        assertEquals(float[].class, typeArgument(GlUniformFloatArray.class));
        assertEquals(Matrix3f.class, typeArgument(GlUniformMatrix3f.class));
        assertEquals(org.joml.Matrix4fc.class, typeArgument(GlUniformMatrix4f.class));
    }

    // Returns the type argument a subclass gives the base class
    private static java.lang.reflect.Type typeArgument(Class<?> subclass) {
        ParameterizedType type = (ParameterizedType) subclass.getGenericSuperclass();
        assertSame(GlUniform.class, type.getRawType());
        return type.getActualTypeArguments()[0];
    }
}
