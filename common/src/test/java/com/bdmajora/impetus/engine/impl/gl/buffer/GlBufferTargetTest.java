package com.bdmajora.impetus.engine.impl.gl.buffer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL40;
import org.lwjgl.opengl.GL42;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

// Tests of every member of the buffer target enum
class GlBufferTargetTest {
    // Number of targets in the enum
    private static final int COUNT = 7;
    // Largest value of a 16 bit GL enum
    private static final int ENUM_LIMIT = 0xFFFF;
    // Number of reads in the repeated read test
    private static final int REPEATS = 1_000_000;
    // Number of threads in the concurrent tests
    private static final int THREADS = 8;
    // Target names in declaration order
    private static final List<String> NAMES = List.of("ARRAY_BUFFER", "ELEMENT_BUFFER", "COPY_READ_BUFFER", "COPY_WRITE_BUFFER",
            "DRAW_INDIRECT_BUFFER", "PIXEL_PACK_BUFFER", "PIXEL_UNPACK_BUFFER");
    // Binary name of the enum
    private static final String NAME = GlBufferTarget.class.getName();

    // Returns the target enum that the GL specification assigns to the binding point
    private static int specificationTarget(GlBufferTarget target) {
        return switch (target) {
            case ARRAY_BUFFER -> 0x8892;
            case ELEMENT_BUFFER -> 0x8893;
            case COPY_READ_BUFFER -> 0x8F36;
            case COPY_WRITE_BUFFER -> 0x8F37;
            case DRAW_INDIRECT_BUFFER -> 0x8F3F;
            case PIXEL_PACK_BUFFER -> 0x88EB;
            case PIXEL_UNPACK_BUFFER -> 0x88EC;
        };
    }

    // Returns the binding query enum that the GL specification assigns to the binding point
    private static int specificationBinding(GlBufferTarget target) {
        return switch (target) {
            case ARRAY_BUFFER -> 0x8894;
            case ELEMENT_BUFFER -> 0x8895;
            case COPY_READ_BUFFER -> 0x8F36;
            case COPY_WRITE_BUFFER -> 0x8F37;
            case DRAW_INDIRECT_BUFFER -> 0x8F43;
            case PIXEL_PACK_BUFFER -> 0x88ED;
            case PIXEL_UNPACK_BUFFER -> 0x88EF;
        };
    }

    // Returns the LWJGL constant that names the binding query of the binding point
    private static int lwjglBinding(GlBufferTarget target) {
        return switch (target) {
            case ARRAY_BUFFER -> GL15.GL_ARRAY_BUFFER_BINDING;
            case ELEMENT_BUFFER -> GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING;
            case COPY_READ_BUFFER -> GL42.GL_COPY_READ_BUFFER_BINDING;
            case COPY_WRITE_BUFFER -> GL42.GL_COPY_WRITE_BUFFER_BINDING;
            case DRAW_INDIRECT_BUFFER -> GL40.GL_DRAW_INDIRECT_BUFFER_BINDING;
            case PIXEL_PACK_BUFFER -> GL21.GL_PIXEL_PACK_BUFFER_BINDING;
            case PIXEL_UNPACK_BUFFER -> GL21.GL_PIXEL_UNPACK_BUFFER_BINDING;
        };
    }

    // Returns a different name with the same string hash as the given name
    private static String hashTwin(String name) {
        char[] chars = name.toCharArray();
        chars[0] += 1;
        chars[1] -= 31;
        return new String(chars);
    }

    // Returns the bytes of the compiled enum
    private static byte[] classFile() throws ClassNotFoundException {
        try (InputStream in = GlBufferTarget.class.getResourceAsStream(GlBufferTarget.class.getSimpleName() + ".class")) {
            if (in == null) {
                throw new ClassNotFoundException(NAME);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new ClassNotFoundException(NAME, e);
        }
    }

    // Checks that every target reports the target enum that the GL specification assigns
    @ParameterizedTest
    @EnumSource(GlBufferTarget.class)
    void everyTargetReportsItsSpecificationTarget(GlBufferTarget target) {
        assertEquals(specificationTarget(target), target.getTargetParameter());
    }

    // Checks that every target reports the binding query enum that the GL specification assigns and LWJGL names
    @ParameterizedTest
    @EnumSource(GlBufferTarget.class)
    void everyTargetReportsItsSpecificationBinding(GlBufferTarget target) {
        assertEquals(specificationBinding(target), target.getBindingParameter());
        assertEquals(lwjglBinding(target), target.getBindingParameter());
    }

    // Checks that both enums of every target are positive 16 bit values that widen to long unchanged
    @ParameterizedTest
    @EnumSource(GlBufferTarget.class)
    void bothEnumsArePositiveSixteenBitValues(GlBufferTarget target) {
        for (int value : new int[] {target.getTargetParameter(), target.getBindingParameter()}) {
            assertTrue(value > 0, Integer.toHexString(value));
            assertEquals(value, value & ENUM_LIMIT);
            assertEquals(Integer.toUnsignedLong(value), (long) value);
        }
    }

    // Checks that no two targets share a target enum
    @Test
    void noTwoTargetsShareATargetEnum() {
        Set<Integer> seen = new HashSet<>();
        for (GlBufferTarget target : GlBufferTarget.values()) {
            assertTrue(seen.add(target.getTargetParameter()), target.name());
        }
        assertEquals(COUNT, seen.size());
    }

    // Checks that no two targets share a binding query enum
    @Test
    void noTwoTargetsShareABindingQueryEnum() {
        Set<Integer> seen = new HashSet<>();
        for (GlBufferTarget target : GlBufferTarget.values()) {
            assertTrue(seen.add(target.getBindingParameter()), target.name());
        }
        assertEquals(COUNT, seen.size());
    }

    // Checks that no binding query of a target equals the target enum of another target
    @Test
    void noBindingQueryEqualsTheTargetEnumOfAnotherTarget() {
        for (GlBufferTarget target : GlBufferTarget.values()) {
            for (GlBufferTarget other : GlBufferTarget.values()) {
                if (other != target) {
                    assertNotEquals(other.getTargetParameter(), target.getBindingParameter(), target + " " + other);
                }
            }
        }
    }

    // Checks that only the copy targets reuse the target enum as the binding query
    @ParameterizedTest
    @EnumSource(GlBufferTarget.class)
    void onlyTheCopyTargetsReuseTheTargetEnumAsTheBindingQuery(GlBufferTarget target) {
        boolean copy = target == GlBufferTarget.COPY_READ_BUFFER || target == GlBufferTarget.COPY_WRITE_BUFFER;
        assertEquals(copy, target.getBindingParameter() == target.getTargetParameter());
    }

    // Checks that repeated reads of every target return the first values
    @ParameterizedTest
    @EnumSource(GlBufferTarget.class)
    void repeatedReadsReturnTheFirstValues(GlBufferTarget target) {
        int first = target.getTargetParameter();
        int binding = target.getBindingParameter();
        for (int i = 0; i < REPEATS; i++) {
            if (target.getTargetParameter() != first || target.getBindingParameter() != binding) {
                fail("Read " + i + " of " + target + " changed");
            }
        }
    }

    // Checks that reads from several threads at once return the specification values
    @Test
    void concurrentReadsReturnTheSpecificationValues() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int t = 0; t < THREADS; t++) {
                results.add(pool.submit(() -> {
                    for (int i = 0; i < REPEATS / THREADS; i++) {
                        for (GlBufferTarget target : GlBufferTarget.VALUES) {
                            if (target.getTargetParameter() != specificationTarget(target)
                                    || target.getBindingParameter() != specificationBinding(target)) {
                                return false;
                            }
                        }
                    }
                    return true;
                }));
            }
            for (Future<Boolean> result : results) {
                assertTrue(result.get());
            }
        } finally {
            pool.shutdownNow();
        }
    }

    // Checks that the shared array lists every target once in declaration order
    @Test
    void sharedArrayListsEveryTargetOnceInDeclarationOrder() {
        assertArrayEquals(GlBufferTarget.values(), GlBufferTarget.VALUES);
        List<String> names = new ArrayList<>();
        for (int i = 0; i < GlBufferTarget.VALUES.length; i++) {
            assertEquals(i, GlBufferTarget.VALUES[i].ordinal());
            names.add(GlBufferTarget.VALUES[i].name());
        }
        assertEquals(NAMES, names);
    }

    // Checks that the count equals the number of targets and the length of both arrays
    @Test
    void countEqualsTheNumberOfTargets() {
        assertEquals(COUNT, GlBufferTarget.COUNT);
        assertEquals(GlBufferTarget.COUNT, GlBufferTarget.VALUES.length);
        assertEquals(GlBufferTarget.COUNT, GlBufferTarget.values().length);
        assertEquals(GlBufferTarget.COUNT, EnumSet.allOf(GlBufferTarget.class).size());
    }

    // Checks that the ordinals of the targets fill an array of count slots once each
    @Test
    void ordinalsFillAnArrayOfCountSlotsOnceEach() {
        int[] slots = new int[GlBufferTarget.COUNT];
        for (GlBufferTarget target : GlBufferTarget.VALUES) {
            slots[target.ordinal()]++;
        }
        int[] once = new int[COUNT];
        Arrays.fill(once, 1);
        assertArrayEquals(once, slots);
    }

    // Checks the first and last index of both arrays and the failure of the indexes beside them and at the int limits
    @Test
    void arraysHonourTheirBoundaryIndexes() {
        for (GlBufferTarget[] targets : List.of(GlBufferTarget.VALUES, GlBufferTarget.values())) {
            assertSame(GlBufferTarget.ARRAY_BUFFER, targets[0]);
            assertSame(GlBufferTarget.PIXEL_UNPACK_BUFFER, targets[COUNT - 1]);
            for (int index : new int[] {-1, COUNT, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
                assertThrows(ArrayIndexOutOfBoundsException.class, () -> targets[index].getTargetParameter(), Integer.toString(index));
            }
        }
    }

    // Checks that values lists every target once in declaration order
    @Test
    void valuesListsEveryTargetOnceInDeclarationOrder() {
        GlBufferTarget[] targets = GlBufferTarget.values();
        assertEquals(COUNT, targets.length);
        List<String> names = new ArrayList<>();
        for (int i = 0; i < targets.length; i++) {
            assertEquals(i, targets[i].ordinal());
            names.add(targets[i].name());
        }
        assertEquals(NAMES, names);
        assertEquals(EnumSet.allOf(GlBufferTarget.class), EnumSet.copyOf(Arrays.asList(targets)));
    }

    // Checks that values returns a fresh array that a change to an earlier array leaves intact
    @Test
    void valuesReturnsAFreshArrayEachCall() {
        GlBufferTarget[] first = GlBufferTarget.values();
        GlBufferTarget[] second = GlBufferTarget.values();
        assertNotSame(first, second);
        assertNotSame(GlBufferTarget.VALUES, first);
        assertArrayEquals(first, second);
        Arrays.fill(first, null);
        assertArrayEquals(second, GlBufferTarget.values());
        assertArrayEquals(second, GlBufferTarget.VALUES);
        assertSame(GlBufferTarget.ARRAY_BUFFER, GlBufferTarget.values()[0]);
    }

    // Checks that every target name resolves back to the same constant
    @ParameterizedTest
    @EnumSource(GlBufferTarget.class)
    void everyNameResolvesToItsTarget(GlBufferTarget target) {
        assertSame(target, GlBufferTarget.valueOf(target.name()));
        assertSame(target, Enum.valueOf(GlBufferTarget.class, target.name()));
        assertSame(target, GlBufferTarget.valueOf(new String(target.name().toCharArray())));
    }

    // Checks that valueOf rejects a null name
    @Test
    void valueOfRejectsANullName() {
        NullPointerException error = assertThrows(NullPointerException.class, () -> GlBufferTarget.valueOf(null));
        assertEquals("Name is null", error.getMessage());
        assertThrows(NullPointerException.class, () -> Enum.valueOf(GlBufferTarget.class, null));
    }

    // Checks that valueOf rejects every name that matches no constant exactly
    @ParameterizedTest
    @ValueSource(strings = {"", " ", "A", "_", "array_buffer", "Array_Buffer", "aRRAY_BUFFER", "ARRAY_BUFFEr", " ARRAY_BUFFER", "ARRAY_BUFFER ",
            "ARRAY_BUFFER\t", "ARRAY_BUFFER\0", "ARRAY BUFFER", "ARRAY-BUFFER", "ARRAYBUFFER", "GL_ARRAY_BUFFER", "ARRAY_BUFFER_BINDING",
            "ELEMENT_ARRAY_BUFFER", "GL_ELEMENT_ARRAY_BUFFER", "INDEX_BUFFER", "COPY_READ_BUFFER_BINDING", "COPY_WRITE", "DRAW_INDIRECT",
            "DISPATCH_INDIRECT_BUFFER", "PIXEL_BUFFER", "UNIFORM_BUFFER", "SHADER_STORAGE_BUFFER", "ATOMIC_COUNTER_BUFFER", "TEXTURE_BUFFER",
            "TRANSFORM_FEEDBACK_BUFFER", "QUERY_BUFFER", "PARAMETER_BUFFER", "VALUES", "COUNT", "0", "6", "7", "-1", "34962", "0x8892",
            "2147483647", "-2147483648", "9223372036854775807", "null"})
    void valueOfRejectsUnknownNames(String name) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> GlBufferTarget.valueOf(name));
        assertEquals("No enum constant " + GlBufferTarget.class.getCanonicalName() + "." + name, error.getMessage());
    }

    // Checks that valueOf rejects every proper prefix of every name and every name with one extra character
    @ParameterizedTest
    @EnumSource(GlBufferTarget.class)
    void valueOfRejectsPrefixesAndExtensionsOfEveryName(GlBufferTarget target) {
        String name = target.name();
        for (int end = 0; end < name.length(); end++) {
            String prefix = name.substring(0, end);
            assertThrows(IllegalArgumentException.class, () -> GlBufferTarget.valueOf(prefix), prefix);
        }
        for (char extra : new char[] {'_', 'S', 'a', ' ', '\0', '\n'}) {
            assertThrows(IllegalArgumentException.class, () -> GlBufferTarget.valueOf(name + extra));
            assertThrows(IllegalArgumentException.class, () -> GlBufferTarget.valueOf(extra + name));
        }
    }

    // Checks that valueOf rejects a long name that starts with a target name
    @Test
    void valueOfRejectsALongName() {
        String name = "ARRAY_BUFFER" + "_".repeat(1 << 20);
        assertThrows(IllegalArgumentException.class, () -> GlBufferTarget.valueOf(name));
    }

    // Checks that valueOf rejects a different name with the hash of a target name
    @ParameterizedTest
    @EnumSource(GlBufferTarget.class)
    void valueOfRejectsANameWithTheHashOfATargetName(GlBufferTarget target) {
        String twin = hashTwin(target.name());
        assertEquals(target.name().hashCode(), twin.hashCode());
        assertNotEquals(target.name(), twin);
        assertThrows(IllegalArgumentException.class, () -> GlBufferTarget.valueOf(twin));
    }

    // Checks that the constructor refuses a reflective call with valid and with extreme arguments
    @Test
    void constructorRefusesReflectiveInstantiation() {
        Constructor<?>[] constructors = GlBufferTarget.class.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        Constructor<?> constructor = constructors[0];
        assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        assertArrayEquals(new Class<?>[] {String.class, int.class, int.class, int.class}, constructor.getParameterTypes());
        constructor.setAccessible(true);
        for (Object[] arguments : List.of(
                new Object[] {"UNIFORM_BUFFER", COUNT, 0x8A11, 0x8A28},
                new Object[] {"ARRAY_BUFFER", 0, 0x8892, 0x8894},
                new Object[] {null, -1, 0, 0},
                new Object[] {"", Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE})) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> constructor.newInstance(arguments));
            assertEquals("Cannot reflectively create enum objects", error.getMessage());
        }
        assertEquals(COUNT, GlBufferTarget.values().length);
    }

    // Checks that an isolated copy of the enum initialises and answers while loading no class outside the JDK
    @Test
    void isolatedCopyRunsWithoutLoadingLwjgl() throws Exception {
        IsolatedLoader loader = new IsolatedLoader();
        Class<?> type = Class.forName(NAME, true, loader);
        assertNotSame(GlBufferTarget.class, type);
        assertSame(loader, type.getClassLoader());
        Object[] shared = (Object[]) type.getField("VALUES").get(null);
        assertEquals(COUNT, shared.length);
        assertEquals(COUNT, type.getField("COUNT").getInt(null));
        Method targetGetter = type.getMethod("getTargetParameter");
        Method bindingGetter = type.getMethod("getBindingParameter");
        Method valueOf = type.getMethod("valueOf", String.class);
        for (int i = 0; i < COUNT; i++) {
            GlBufferTarget expected = GlBufferTarget.VALUES[i];
            assertEquals(expected.name(), ((Enum<?>) shared[i]).name());
            assertEquals(expected.getTargetParameter(), targetGetter.invoke(shared[i]));
            assertEquals(expected.getBindingParameter(), bindingGetter.invoke(shared[i]));
            assertSame(shared[i], valueOf.invoke(null, expected.name()));
        }
        for (String requested : loader.requested()) {
            assertTrue(requested.equals(NAME) || requested.startsWith("java."), requested);
        }
    }

    // Checks that threads that initialise one isolated copy at once all read one shared array and one count
    @Test
    void concurrentInitialisationPublishesOneSharedArray() throws Exception {
        IsolatedLoader loader = new IsolatedLoader();
        CyclicBarrier start = new CyclicBarrier(THREADS);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            List<Future<Object[]>> results = new ArrayList<>();
            for (int t = 0; t < THREADS; t++) {
                results.add(pool.submit(() -> {
                    start.await();
                    Class<?> type = Class.forName(NAME, true, loader);
                    assertEquals(COUNT, type.getField("COUNT").getInt(null));
                    return (Object[]) type.getField("VALUES").get(null);
                }));
            }
            Object[] first = results.get(0).get();
            assertEquals(COUNT, first.length);
            for (Object target : first) {
                assertNotNull(target);
            }
            for (Future<Object[]> result : results) {
                assertSame(first, result.get());
            }
        } finally {
            pool.shutdownNow();
        }
    }

    // Checks that a change to the shared array of an isolated copy leaves values and valueOf intact
    @Test
    void changeToTheSharedArrayLeavesValuesAndValueOfIntact() throws Exception {
        Class<?> type = Class.forName(NAME, true, new IsolatedLoader());
        Object[] shared = (Object[]) type.getField("VALUES").get(null);
        Object[] before = shared.clone();
        Arrays.fill(shared, null);
        assertArrayEquals(before, (Object[]) type.getMethod("values").invoke(null));
        assertSame(before[0], type.getMethod("valueOf", String.class).invoke(null, "ARRAY_BUFFER"));
        assertEquals(COUNT, type.getField("COUNT").getInt(null));
        assertNull(((Object[]) type.getField("VALUES").get(null))[0]);
    }

    // Checks the public shape of the enum
    @Test
    void enumKeepsItsShape() throws ReflectiveOperationException {
        Class<GlBufferTarget> type = GlBufferTarget.class;
        assertTrue(type.isEnum());
        assertTrue(Modifier.isPublic(type.getModifiers()));
        assertTrue(Modifier.isFinal(type.getModifiers()));
        assertSame(Enum.class, type.getSuperclass());
        assertEquals(0, type.getInterfaces().length);

        List<String> constants = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        for (Field field : type.getDeclaredFields()) {
            if (field.isEnumConstant()) {
                constants.add(field.getName());
            } else if (!field.isSynthetic()) {
                fields.add(field.getName());
            }
        }
        assertEquals(NAMES, constants);
        fields.sort(null);
        assertEquals(List.of("COUNT", "VALUES", "binding", "target"), fields);
        Field shared = type.getDeclaredField("VALUES");
        assertEquals(Modifier.PUBLIC | Modifier.STATIC | Modifier.FINAL, shared.getModifiers());
        assertSame(GlBufferTarget[].class, shared.getType());
        Field count = type.getDeclaredField("COUNT");
        assertEquals(Modifier.PUBLIC | Modifier.STATIC | Modifier.FINAL, count.getModifiers());
        assertSame(int.class, count.getType());
        for (String name : List.of("target", "binding")) {
            Field field = type.getDeclaredField(name);
            assertEquals(Modifier.PRIVATE | Modifier.FINAL, field.getModifiers());
            assertSame(int.class, field.getType());
        }

        List<String> methods = new ArrayList<>();
        for (Method method : type.getDeclaredMethods()) {
            if (!method.isSynthetic() && !Modifier.isPrivate(method.getModifiers())) {
                methods.add(method.getName());
            }
        }
        methods.sort(null);
        assertEquals(List.of("getBindingParameter", "getTargetParameter", "valueOf", "values"), methods);
        for (String name : List.of("getTargetParameter", "getBindingParameter")) {
            Method getter = type.getDeclaredMethod(name);
            assertEquals(Modifier.PUBLIC, getter.getModifiers());
            assertSame(int.class, getter.getReturnType());
        }
    }

    // Loader that defines its own copy of the enum and delegates every other class to the platform loader
    private static final class IsolatedLoader extends ClassLoader {
        // Names of the classes requested through the loader
        private final List<String> requested = Collections.synchronizedList(new ArrayList<>());

        // Creates a loader whose parent is the platform loader
        IsolatedLoader() {
            super(ClassLoader.getPlatformClassLoader());
        }

        // Returns a copy of the names requested so far
        List<String> requested() {
            synchronized (this.requested) {
                return new ArrayList<>(this.requested);
            }
        }

        // Records the name and defines the enum from its class file or delegates any other name to the parent
        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            this.requested.add(name);
            if (!name.equals(NAME)) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    byte[] bytes = classFile();
                    loaded = defineClass(name, bytes, 0, bytes.length);
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
        }
    }
}
