package com.bdmajora.impetus.engine.impl.gl.buffer;

import com.bdmajora.impetus.engine.impl.gl.util.EnumBit;
import com.bdmajora.impetus.engine.impl.gl.util.EnumBitField;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

// Tests of every member of the buffer storage flag enum
class GlBufferStorageFlagsTest {
    // Number of storage flags in the enum
    private static final int COUNT = 5;
    // Union of the bits of every storage flag
    private static final int ALL_BITS = 0x02C3;
    // Union of every bit that glBufferStorage accepts
    private static final int ACCEPTED_BITS = 0x03C3;
    // Bit of the dynamic storage flag that the enum leaves out
    private static final int DYNAMIC_STORAGE_BIT = 0x0100;
    // Number of reads in the repeated read test
    private static final int REPEATS = 1_000_000;
    // Number of threads in the concurrent read test
    private static final int THREADS = 8;
    // Flag names in declaration order
    private static final List<String> NAMES = List.of("PERSISTENT", "MAP_READ", "MAP_WRITE", "CLIENT_STORAGE", "COHERENT");

    // Returns the bit that the GL specification assigns to the flag
    private static int specificationBit(GlBufferStorageFlags flag) {
        return switch (flag) {
            case MAP_READ -> 0x0001;
            case MAP_WRITE -> 0x0002;
            case PERSISTENT -> 0x0040;
            case COHERENT -> 0x0080;
            case CLIENT_STORAGE -> 0x0200;
        };
    }

    // Returns the map flag that carries the same bit as the storage flag or null when none does
    private static GlBufferMapFlags mapCounterpart(GlBufferStorageFlags flag) {
        return switch (flag) {
            case MAP_READ -> GlBufferMapFlags.READ;
            case MAP_WRITE -> GlBufferMapFlags.WRITE;
            case PERSISTENT -> GlBufferMapFlags.PERSISTENT;
            case COHERENT -> GlBufferMapFlags.COHERENT;
            case CLIENT_STORAGE -> null;
        };
    }

    // Checks that every flag reports the bit that the GL specification assigns
    @ParameterizedTest
    @EnumSource(GlBufferStorageFlags.class)
    void everyFlagReportsItsSpecificationBit(GlBufferStorageFlags flag) {
        assertEquals(specificationBit(flag), flag.getBits());
    }

    // Checks that every flag holds one bit inside the accepted mask and below the sign bit
    @ParameterizedTest
    @EnumSource(GlBufferStorageFlags.class)
    void everyFlagIsOnePositiveBitInsideTheAcceptedMask(GlBufferStorageFlags flag) {
        int bits = flag.getBits();
        assertEquals(1, Integer.bitCount(bits));
        assertTrue(bits > 0);
        assertEquals(0, bits & ~ACCEPTED_BITS);
        assertEquals(Integer.toUnsignedLong(bits), (long) bits);
        assertEquals(bits, (int) (long) bits);
    }

    // Checks that no two flags share a bit and that their union and their long sum both equal the full set
    @Test
    void flagsShareNoBitAndFillTheFullSet() {
        int union = 0;
        long sum = 0;
        for (GlBufferStorageFlags flag : GlBufferStorageFlags.values()) {
            assertEquals(0, union & flag.getBits(), flag.name());
            union |= flag.getBits();
            sum += flag.getBits();
        }
        assertEquals(ALL_BITS, union);
        assertEquals(ALL_BITS, sum);
        assertEquals(ACCEPTED_BITS, union | DYNAMIC_STORAGE_BIT);
        assertEquals(0, union & DYNAMIC_STORAGE_BIT);
    }

    // Checks that every flag with a map counterpart carries its bit and the others share no map bit
    @ParameterizedTest
    @EnumSource(GlBufferStorageFlags.class)
    void everyFlagMatchesItsMapCounterpart(GlBufferStorageFlags flag) {
        GlBufferMapFlags counterpart = mapCounterpart(flag);
        for (GlBufferMapFlags map : GlBufferMapFlags.values()) {
            assertEquals(map == counterpart, (map.getBits() & flag.getBits()) != 0, map.name());
        }
        if (counterpart != null) {
            assertEquals(counterpart.getBits(), flag.getBits());
        }
    }

    // Checks that repeated reads of every flag return the first bit
    @ParameterizedTest
    @EnumSource(GlBufferStorageFlags.class)
    void repeatedReadsReturnTheFirstBit(GlBufferStorageFlags flag) {
        int first = flag.getBits();
        for (int i = 0; i < REPEATS; i++) {
            if (flag.getBits() != first) {
                fail("Read " + i + " of " + flag + " changed");
            }
        }
    }

    // Checks that reads from several threads at once return the specification bits
    @Test
    void concurrentReadsReturnTheSpecificationBits() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int t = 0; t < THREADS; t++) {
                results.add(pool.submit(() -> {
                    for (int i = 0; i < REPEATS / THREADS; i++) {
                        for (GlBufferStorageFlags flag : GlBufferStorageFlags.values()) {
                            if (flag.getBits() != specificationBit(flag)) {
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

    // Checks that a read through the EnumBit interface matches the direct read
    @ParameterizedTest
    @EnumSource(GlBufferStorageFlags.class)
    void interfaceReadMatchesTheDirectRead(GlBufferStorageFlags flag) {
        EnumBit bit = flag;
        assertEquals(flag.getBits(), bit.getBits());
    }

    // Checks that a field of one flag carries that bit alone and contains that flag alone
    @ParameterizedTest
    @EnumSource(GlBufferStorageFlags.class)
    void singleFlagFieldCarriesItsBitAlone(GlBufferStorageFlags flag) {
        EnumBitField<GlBufferStorageFlags> field = EnumBitField.of(flag);
        assertEquals(flag.getBits(), field.getBitField());
        for (GlBufferStorageFlags other : GlBufferStorageFlags.values()) {
            assertEquals(other == flag, field.contains(other), other.name());
        }
    }

    // Checks that a field of every flag carries the full set
    @Test
    void fieldOfEveryFlagCarriesTheFullSet() {
        assertEquals(ALL_BITS, EnumBitField.of(GlBufferStorageFlags.values()).getBitField());
    }

    // Checks that a field built from repeated flags carries each bit once
    @Test
    void fieldOfRepeatedFlagsCarriesEachBitOnce() {
        EnumBitField<GlBufferStorageFlags> field = EnumBitField.of(
                GlBufferStorageFlags.MAP_WRITE, GlBufferStorageFlags.MAP_WRITE, GlBufferStorageFlags.PERSISTENT, GlBufferStorageFlags.MAP_WRITE);
        assertEquals(GlBufferStorageFlags.MAP_WRITE.getBits() | GlBufferStorageFlags.PERSISTENT.getBits(), field.getBitField());
        assertFalse(field.contains(GlBufferStorageFlags.MAP_READ));
    }

    // Checks that the staging buffer flag set gives the bits that a persistent client write mapping needs
    @Test
    void stagingFlagSetGivesThePersistentClientWriteBits() {
        EnumBitField<GlBufferStorageFlags> field = EnumBitField.of(
                GlBufferStorageFlags.PERSISTENT, GlBufferStorageFlags.CLIENT_STORAGE, GlBufferStorageFlags.MAP_WRITE);
        assertEquals(0x0242, field.getBitField());
        assertEquals(0, field.getBitField() & GlBufferMapFlags.COHERENT.getBits());
        assertEquals(0, field.getBitField() & GlBufferMapFlags.READ.getBits());
    }

    // Checks that every flag name resolves back to the same constant
    @ParameterizedTest
    @EnumSource(GlBufferStorageFlags.class)
    void everyNameResolvesToItsFlag(GlBufferStorageFlags flag) {
        assertSame(flag, GlBufferStorageFlags.valueOf(flag.name()));
        assertSame(flag, Enum.valueOf(GlBufferStorageFlags.class, flag.name()));
        assertSame(flag, GlBufferStorageFlags.valueOf(new String(flag.name().toCharArray())));
    }

    // Checks that valueOf rejects a null name
    @Test
    void valueOfRejectsANullName() {
        NullPointerException error = assertThrows(NullPointerException.class, () -> GlBufferStorageFlags.valueOf(null));
        assertEquals("Name is null", error.getMessage());
    }

    // Checks that valueOf rejects every name that matches no constant exactly
    @ParameterizedTest
    @ValueSource(strings = {"", " ", "persistent", "Persistent", " PERSISTENT", "PERSISTENT ", "PERSISTENT_", "PERSISTENT\t",
            "PERSISTENT\0", "GL_MAP_PERSISTENT_BIT", "GL_CLIENT_STORAGE_BIT", "CLIENT_STORAGE_BIT", "READ", "WRITE", "MAP_PERSISTENT",
            "MAP_COHERENT", "DYNAMIC_STORAGE", "INVALIDATE_BUFFER", "UNSYNCHRONIZED", "0", "1", "-1", "2147483647", "-2147483648", "null"})
    void valueOfRejectsUnknownNames(String name) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> GlBufferStorageFlags.valueOf(name));
        assertEquals("No enum constant " + GlBufferStorageFlags.class.getCanonicalName() + "." + name, error.getMessage());
    }

    // Checks that valueOf rejects a long name that starts with a flag name
    @Test
    void valueOfRejectsALongName() {
        String name = "COHERENT" + "_".repeat(1 << 20);
        assertThrows(IllegalArgumentException.class, () -> GlBufferStorageFlags.valueOf(name));
    }

    // Checks that values lists every flag once in declaration order
    @Test
    void valuesListsEveryFlagOnceInDeclarationOrder() {
        GlBufferStorageFlags[] flags = GlBufferStorageFlags.values();
        assertEquals(COUNT, flags.length);
        List<String> names = new ArrayList<>();
        for (int i = 0; i < flags.length; i++) {
            assertEquals(i, flags[i].ordinal());
            names.add(flags[i].name());
        }
        assertEquals(NAMES, names);
        assertEquals(EnumSet.allOf(GlBufferStorageFlags.class), EnumSet.copyOf(Arrays.asList(flags)));
    }

    // Checks that values returns a fresh array that a change to an earlier array leaves intact
    @Test
    void valuesReturnsAFreshArrayEachCall() {
        GlBufferStorageFlags[] first = GlBufferStorageFlags.values();
        GlBufferStorageFlags[] second = GlBufferStorageFlags.values();
        assertNotSame(first, second);
        assertArrayEquals(first, second);
        Arrays.fill(first, null);
        assertArrayEquals(second, GlBufferStorageFlags.values());
        assertSame(GlBufferStorageFlags.PERSISTENT, GlBufferStorageFlags.values()[0]);
    }

    // Checks the first and last index of values and the failure of the indexes beside them and at the int limits
    @Test
    void valuesHonoursItsBoundaryIndexes() {
        GlBufferStorageFlags[] flags = GlBufferStorageFlags.values();
        assertSame(GlBufferStorageFlags.PERSISTENT, flags[0]);
        assertSame(GlBufferStorageFlags.COHERENT, flags[COUNT - 1]);
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> flags[-1].getBits());
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> flags[COUNT].getBits());
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> flags[Integer.MIN_VALUE].getBits());
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> flags[Integer.MAX_VALUE].getBits());
    }

    // Checks that the constructor refuses a reflective call
    @Test
    void constructorRefusesReflectiveInstantiation() {
        Constructor<?>[] constructors = GlBufferStorageFlags.class.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        Constructor<?> constructor = constructors[0];
        assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        assertArrayEquals(new Class<?>[] {String.class, int.class, int.class}, constructor.getParameterTypes());
        constructor.setAccessible(true);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> constructor.newInstance("DYNAMIC_STORAGE", COUNT, DYNAMIC_STORAGE_BIT));
        assertEquals("Cannot reflectively create enum objects", error.getMessage());
        assertEquals(COUNT, GlBufferStorageFlags.values().length);
    }

    // Checks the public shape of the enum
    @Test
    void enumKeepsItsShape() throws ReflectiveOperationException {
        Class<GlBufferStorageFlags> type = GlBufferStorageFlags.class;
        assertTrue(type.isEnum());
        assertTrue(Modifier.isPublic(type.getModifiers()));
        assertTrue(Modifier.isFinal(type.getModifiers()));
        assertSame(Enum.class, type.getSuperclass());
        assertArrayEquals(new Class<?>[] {EnumBit.class}, type.getInterfaces());

        List<String> constants = new ArrayList<>();
        for (Field field : type.getDeclaredFields()) {
            if (field.isEnumConstant()) {
                constants.add(field.getName());
            } else {
                assertTrue(field.isSynthetic() || Modifier.isPrivate(field.getModifiers()), field.getName());
            }
        }
        assertEquals(NAMES, constants);
        Field bits = type.getDeclaredField("bits");
        assertEquals(Modifier.PRIVATE | Modifier.FINAL, bits.getModifiers());
        assertSame(int.class, bits.getType());

        List<String> methods = new ArrayList<>();
        for (Method method : type.getDeclaredMethods()) {
            if (!method.isSynthetic() && !Modifier.isPrivate(method.getModifiers())) {
                methods.add(method.getName());
            }
        }
        methods.sort(null);
        assertEquals(List.of("getBits", "valueOf", "values"), methods);
        Method getter = type.getDeclaredMethod("getBits");
        assertEquals(Modifier.PUBLIC, getter.getModifiers());
        assertSame(int.class, getter.getReturnType());
    }
}
