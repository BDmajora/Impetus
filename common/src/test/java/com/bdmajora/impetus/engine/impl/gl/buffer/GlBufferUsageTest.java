package com.bdmajora.impetus.engine.impl.gl.buffer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.opengl.GL15C;

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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

// Tests of every member of the buffer usage enum
class GlBufferUsageTest {
    // Number of usage hints in the enum
    private static final int COUNT = 9;
    // Largest value of a 16 bit GL enum
    private static final int ENUM_LIMIT = 0xFFFF;
    // GL enum of the first usage hint in the specification
    private static final int BASE = 0x88E0;
    // Number of enums in the block of each frequency
    private static final int BLOCK = 4;
    // Number of reads in the repeated read test
    private static final int REPEATS = 1_000_000;
    // Number of threads in the concurrent tests
    private static final int THREADS = 8;
    // Usage names in declaration order
    private static final List<String> NAMES = List.of("STREAM_DRAW", "STREAM_READ", "STREAM_COPY", "STATIC_DRAW", "STATIC_READ",
            "STATIC_COPY", "DYNAMIC_DRAW", "DYNAMIC_READ", "DYNAMIC_COPY");
    // Frequency prefixes in declaration order
    private static final List<String> FREQUENCIES = List.of("STREAM", "STATIC", "DYNAMIC");
    // Nature suffixes in declaration order
    private static final List<String> NATURES = List.of("DRAW", "READ", "COPY");
    // Binary name of the enum
    private static final String NAME = GlBufferUsage.class.getName();

    // Returns the enum that the GL specification assigns to the usage hint
    private static int specification(GlBufferUsage usage) {
        return switch (usage) {
            case STREAM_DRAW -> 0x88E0;
            case STREAM_READ -> 0x88E1;
            case STREAM_COPY -> 0x88E2;
            case STATIC_DRAW -> 0x88E4;
            case STATIC_READ -> 0x88E5;
            case STATIC_COPY -> 0x88E6;
            case DYNAMIC_DRAW -> 0x88E8;
            case DYNAMIC_READ -> 0x88E9;
            case DYNAMIC_COPY -> 0x88EA;
        };
    }

    // Returns the core LWJGL constant of the usage hint
    private static int lwjglCore(GlBufferUsage usage) {
        return switch (usage) {
            case STREAM_DRAW -> GL15C.GL_STREAM_DRAW;
            case STREAM_READ -> GL15C.GL_STREAM_READ;
            case STREAM_COPY -> GL15C.GL_STREAM_COPY;
            case STATIC_DRAW -> GL15C.GL_STATIC_DRAW;
            case STATIC_READ -> GL15C.GL_STATIC_READ;
            case STATIC_COPY -> GL15C.GL_STATIC_COPY;
            case DYNAMIC_DRAW -> GL15C.GL_DYNAMIC_DRAW;
            case DYNAMIC_READ -> GL15C.GL_DYNAMIC_READ;
            case DYNAMIC_COPY -> GL15C.GL_DYNAMIC_COPY;
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
        try (InputStream in = GlBufferUsage.class.getResourceAsStream(GlBufferUsage.class.getSimpleName() + ".class")) {
            if (in == null) {
                throw new ClassNotFoundException(NAME);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new ClassNotFoundException(NAME, e);
        }
    }

    // Checks that every usage reports the enum that the GL specification assigns
    @ParameterizedTest
    @EnumSource(GlBufferUsage.class)
    void everyUsageReportsItsSpecificationEnum(GlBufferUsage usage) {
        assertEquals(specification(usage), usage.getId());
    }

    // Checks that every usage reports the same enum as the core LWJGL constant
    @ParameterizedTest
    @EnumSource(GlBufferUsage.class)
    void everyUsageMatchesTheCoreLwjglConstant(GlBufferUsage usage) {
        assertEquals(lwjglCore(usage), usage.getId());
    }

    // Checks that every enum is a positive 16 bit value that widens to long unchanged
    @ParameterizedTest
    @EnumSource(GlBufferUsage.class)
    void everyEnumIsAPositiveSixteenBitValue(GlBufferUsage usage) {
        int id = usage.getId();
        assertTrue(id > 0, Integer.toHexString(id));
        assertEquals(id, id & ENUM_LIMIT);
        assertEquals(Integer.toUnsignedLong(id), (long) id);
        assertEquals(id, (int) (long) id);
    }

    // Checks that no two usages share an enum
    @Test
    void noTwoUsagesShareAnEnum() {
        Set<Integer> seen = new HashSet<>();
        for (GlBufferUsage usage : GlBufferUsage.values()) {
            assertTrue(seen.add(usage.getId()), usage.name());
        }
        assertEquals(COUNT, seen.size());
    }

    // Checks that every name joins its frequency and nature and that the enum sits at that slot of the grid
    @ParameterizedTest
    @EnumSource(GlBufferUsage.class)
    void enumsFollowTheFrequencyAndNatureGrid(GlBufferUsage usage) {
        int frequency = usage.ordinal() / NATURES.size();
        int nature = usage.ordinal() % NATURES.size();
        assertEquals(FREQUENCIES.get(frequency) + "_" + NATURES.get(nature), usage.name());
        assertEquals(BASE + frequency * BLOCK + nature, usage.getId());
        assertEquals(frequency, (usage.getId() - BASE) / BLOCK);
        assertEquals(nature, (usage.getId() - BASE) % BLOCK);
    }

    // Checks that the enums stay between the first and last usage enum and skip the last slot of every block
    @Test
    void enumsSkipTheLastSlotOfEveryBlock() {
        Set<Integer> used = new HashSet<>();
        for (GlBufferUsage usage : GlBufferUsage.values()) {
            used.add(usage.getId());
        }
        for (int id = BASE - 1; id <= BASE + FREQUENCIES.size() * BLOCK; id++) {
            boolean inside = id >= BASE && id < BASE + FREQUENCIES.size() * BLOCK;
            boolean spare = (id - BASE) % BLOCK == BLOCK - 1;
            assertEquals(inside && !spare, used.contains(id), Integer.toHexString(id));
        }
    }

    // Checks that no usage enum equals the target or binding query enum of a buffer target
    @Test
    void noUsageEqualsABufferTargetEnum() {
        for (GlBufferUsage usage : GlBufferUsage.values()) {
            for (GlBufferTarget target : GlBufferTarget.VALUES) {
                assertNotEquals(target.getTargetParameter(), usage.getId(), usage + " " + target);
                assertNotEquals(target.getBindingParameter(), usage.getId(), usage + " " + target);
            }
        }
    }

    // Checks that repeated reads of every usage return the first value
    @ParameterizedTest
    @EnumSource(GlBufferUsage.class)
    void repeatedReadsReturnTheFirstValue(GlBufferUsage usage) {
        int first = usage.getId();
        for (int i = 0; i < REPEATS; i++) {
            if (usage.getId() != first) {
                fail("Read " + i + " of " + usage + " changed");
            }
        }
    }

    // Checks that reads from several threads at once return the specification enums
    @Test
    void concurrentReadsReturnTheSpecificationEnums() throws Exception {
        GlBufferUsage[] usages = GlBufferUsage.values();
        CyclicBarrier start = new CyclicBarrier(THREADS);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int t = 0; t < THREADS; t++) {
                results.add(pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < REPEATS / THREADS; i++) {
                        for (GlBufferUsage usage : usages) {
                            if (usage.getId() != specification(usage)) {
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

    // Checks that values lists every usage once in declaration order
    @Test
    void valuesListsEveryUsageOnceInDeclarationOrder() {
        GlBufferUsage[] usages = GlBufferUsage.values();
        assertEquals(COUNT, usages.length);
        List<String> names = new ArrayList<>();
        for (int i = 0; i < usages.length; i++) {
            assertEquals(i, usages[i].ordinal());
            names.add(usages[i].name());
        }
        assertEquals(NAMES, names);
        assertEquals(EnumSet.allOf(GlBufferUsage.class), EnumSet.copyOf(Arrays.asList(usages)));
        assertEquals(COUNT, EnumSet.allOf(GlBufferUsage.class).size());
    }

    // Checks that values returns a fresh array that a change to an earlier array leaves intact
    @Test
    void valuesReturnsAFreshArrayEachCall() {
        GlBufferUsage[] first = GlBufferUsage.values();
        GlBufferUsage[] second = GlBufferUsage.values();
        assertNotSame(first, second);
        assertArrayEquals(first, second);
        Arrays.fill(first, null);
        assertArrayEquals(second, GlBufferUsage.values());
        assertSame(GlBufferUsage.STREAM_DRAW, GlBufferUsage.values()[0]);
        assertSame(GlBufferUsage.STREAM_DRAW, GlBufferUsage.valueOf("STREAM_DRAW"));
    }

    // Checks the first and last index of values and the failure of the indexes beside them and at the int limits
    @Test
    void valuesHonoursItsBoundaryIndexes() {
        GlBufferUsage[] usages = GlBufferUsage.values();
        assertSame(GlBufferUsage.STREAM_DRAW, usages[0]);
        assertSame(GlBufferUsage.DYNAMIC_COPY, usages[COUNT - 1]);
        for (int index : new int[] {-1, COUNT, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertThrows(ArrayIndexOutOfBoundsException.class, () -> usages[index].getId(), Integer.toString(index));
        }
    }

    // Checks that the ordinals of the usages fill an array of count slots once each
    @Test
    void ordinalsFillAnArrayOfCountSlotsOnceEach() {
        int[] slots = new int[COUNT];
        for (GlBufferUsage usage : GlBufferUsage.values()) {
            slots[usage.ordinal()]++;
        }
        int[] once = new int[COUNT];
        Arrays.fill(once, 1);
        assertArrayEquals(once, slots);
    }

    // Checks that every usage name resolves back to the same constant
    @ParameterizedTest
    @EnumSource(GlBufferUsage.class)
    void everyNameResolvesToItsUsage(GlBufferUsage usage) {
        assertSame(usage, GlBufferUsage.valueOf(usage.name()));
        assertSame(usage, Enum.valueOf(GlBufferUsage.class, usage.name()));
        assertSame(usage, GlBufferUsage.valueOf(new String(usage.name().toCharArray())));
    }

    // Checks that valueOf rejects a null name and a null enum type
    @Test
    void valueOfRejectsNullArguments() {
        NullPointerException error = assertThrows(NullPointerException.class, () -> GlBufferUsage.valueOf(null));
        assertEquals("Name is null", error.getMessage());
        assertThrows(NullPointerException.class, () -> Enum.valueOf(GlBufferUsage.class, null));
        assertThrows(NullPointerException.class, () -> Enum.valueOf(null, "STATIC_DRAW"));
    }

    // Checks that valueOf rejects every name that matches no constant exactly
    @ParameterizedTest
    @ValueSource(strings = {"", " ", "S", "_", "static_draw", "Static_Draw", "sTATIC_DRAW", "STATIC_DRAw", " STATIC_DRAW", "STATIC_DRAW ",
            "STATIC_DRAW\t", "STATIC_DRAW\0", "STATIC DRAW", "STATIC-DRAW", "STATICDRAW", "GL_STATIC_DRAW", "STATIC", "STREAM", "DYNAMIC",
            "DRAW", "READ", "COPY", "DRAW_STATIC", "STATIC_WRITE", "STREAM_WRITE", "DYNAMIC_STORAGE", "DYNAMIC_STORAGE_BIT", "CLIENT_STORAGE",
            "IMMUTABLE", "ARRAY_BUFFER", "MAP_READ", "PERSISTENT", "ID", "id", "VALUES", "$VALUES", "0", "8", "9", "-1", "35044", "0x88E4",
            "2147483647", "-2147483648", "9223372036854775807", "null"})
    void valueOfRejectsUnknownNames(String name) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> GlBufferUsage.valueOf(name));
        assertEquals("No enum constant " + GlBufferUsage.class.getCanonicalName() + "." + name, error.getMessage());
    }

    // Checks that valueOf rejects every proper prefix of every name and every name with one extra character
    @ParameterizedTest
    @EnumSource(GlBufferUsage.class)
    void valueOfRejectsPrefixesAndExtensionsOfEveryName(GlBufferUsage usage) {
        String name = usage.name();
        for (int end = 0; end < name.length(); end++) {
            String prefix = name.substring(0, end);
            assertThrows(IllegalArgumentException.class, () -> GlBufferUsage.valueOf(prefix), prefix);
        }
        for (char extra : new char[] {'_', 'S', 'a', ' ', '\0', '\n'}) {
            assertThrows(IllegalArgumentException.class, () -> GlBufferUsage.valueOf(name + extra));
            assertThrows(IllegalArgumentException.class, () -> GlBufferUsage.valueOf(extra + name));
        }
    }

    // Checks that valueOf rejects a long name that starts with a usage name
    @Test
    void valueOfRejectsALongName() {
        String name = "STATIC_DRAW" + "_".repeat(1 << 20);
        assertThrows(IllegalArgumentException.class, () -> GlBufferUsage.valueOf(name));
    }

    // Checks that valueOf rejects a different name with the hash of a usage name
    @ParameterizedTest
    @EnumSource(GlBufferUsage.class)
    void valueOfRejectsANameWithTheHashOfAUsageName(GlBufferUsage usage) {
        String twin = hashTwin(usage.name());
        assertEquals(usage.name().hashCode(), twin.hashCode());
        assertNotEquals(usage.name(), twin);
        assertThrows(IllegalArgumentException.class, () -> GlBufferUsage.valueOf(twin));
    }

    // Checks that the constructor refuses a reflective call with valid and with extreme arguments
    @Test
    void constructorRefusesReflectiveInstantiation() {
        Constructor<?>[] constructors = GlBufferUsage.class.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        Constructor<?> constructor = constructors[0];
        assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        assertArrayEquals(new Class<?>[] {String.class, int.class, int.class}, constructor.getParameterTypes());
        constructor.setAccessible(true);
        for (Object[] arguments : List.of(
                new Object[] {"STREAM_WRITE", COUNT, 0x88E3},
                new Object[] {"STATIC_DRAW", 3, 0x88E4},
                new Object[] {null, -1, 0},
                new Object[] {"", Integer.MIN_VALUE, Integer.MAX_VALUE},
                new Object[] {"X", Integer.MAX_VALUE, Integer.MIN_VALUE})) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> constructor.newInstance(arguments));
            assertEquals("Cannot reflectively create enum objects", error.getMessage());
        }
        assertEquals(COUNT, GlBufferUsage.values().length);
    }

    // Checks that an isolated copy of the enum initialises and answers while loading no class outside the JDK
    @Test
    void isolatedCopyRunsWithoutLoadingLwjgl() throws Exception {
        IsolatedLoader loader = new IsolatedLoader();
        Class<?> type = Class.forName(NAME, true, loader);
        assertNotSame(GlBufferUsage.class, type);
        assertSame(loader, type.getClassLoader());
        Object[] usages = (Object[]) type.getMethod("values").invoke(null);
        assertEquals(COUNT, usages.length);
        Method getter = type.getMethod("getId");
        Method valueOf = type.getMethod("valueOf", String.class);
        for (int i = 0; i < COUNT; i++) {
            GlBufferUsage expected = GlBufferUsage.values()[i];
            assertEquals(expected.name(), ((Enum<?>) usages[i]).name());
            assertEquals(expected.getId(), getter.invoke(usages[i]));
            assertSame(usages[i], valueOf.invoke(null, expected.name()));
        }
        for (String requested : loader.requested()) {
            assertTrue(requested.equals(NAME) || requested.startsWith("java."), requested);
        }
    }

    // Checks that threads that initialise one isolated copy at once all read the same constants with the same enums
    @Test
    void concurrentInitialisationPublishesOneSetOfConstants() throws Exception {
        IsolatedLoader loader = new IsolatedLoader();
        CyclicBarrier start = new CyclicBarrier(THREADS);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            List<Future<Object[]>> results = new ArrayList<>();
            for (int t = 0; t < THREADS; t++) {
                results.add(pool.submit(() -> {
                    start.await();
                    Class<?> type = Class.forName(NAME, true, loader);
                    return (Object[]) type.getMethod("values").invoke(null);
                }));
            }
            Object[] first = results.get(0).get();
            assertEquals(COUNT, first.length);
            Method getter = first[0].getClass().getMethod("getId");
            for (int i = 0; i < COUNT; i++) {
                assertEquals(GlBufferUsage.values()[i].getId(), getter.invoke(first[i]));
            }
            for (Future<Object[]> result : results.subList(1, THREADS)) {
                Object[] usages = result.get();
                assertNotSame(first, usages);
                for (int i = 0; i < COUNT; i++) {
                    assertSame(first[i], usages[i]);
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

    // Checks the public shape of the enum
    @Test
    void enumKeepsItsShape() throws ReflectiveOperationException {
        Class<GlBufferUsage> type = GlBufferUsage.class;
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
        assertEquals(List.of("id"), fields);
        Field id = type.getDeclaredField("id");
        assertEquals(Modifier.PRIVATE | Modifier.FINAL, id.getModifiers());
        assertSame(int.class, id.getType());

        List<String> methods = new ArrayList<>();
        for (Method method : type.getDeclaredMethods()) {
            if (!method.isSynthetic() && !Modifier.isPrivate(method.getModifiers())) {
                methods.add(method.getName());
            }
        }
        methods.sort(null);
        assertEquals(List.of("getId", "valueOf", "values"), methods);
        Method getter = type.getDeclaredMethod("getId");
        assertEquals(Modifier.PUBLIC, getter.getModifiers());
        assertSame(int.class, getter.getReturnType());
        assertEquals(0, getter.getParameterCount());
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
