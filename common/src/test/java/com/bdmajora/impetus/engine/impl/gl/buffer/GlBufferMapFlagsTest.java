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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

// Tests of every member of the buffer map flag enum
class GlBufferMapFlagsTest {
    // Number of map flags that the GL specification defines
    private static final int COUNT = 8;
    // Union of every map bit that the GL specification defines
    private static final int ALL_BITS = 0xFF;
    // Number of reads in the repeated read test
    private static final int REPEATS = 1_000_000;
    // Flag names in declaration order
    private static final List<String> NAMES = List.of(
            "READ", "WRITE", "PERSISTENT", "INVALIDATE_BUFFER", "INVALIDATE_RANGE", "EXPLICIT_FLUSH", "COHERENT", "UNSYNCHRONIZED");

    // Returns the bit that the GL specification assigns to the flag
    private static int specificationBit(GlBufferMapFlags flag) {
        return switch (flag) {
            case READ -> 0x0001;
            case WRITE -> 0x0002;
            case INVALIDATE_RANGE -> 0x0004;
            case INVALIDATE_BUFFER -> 0x0008;
            case EXPLICIT_FLUSH -> 0x0010;
            case UNSYNCHRONIZED -> 0x0020;
            case PERSISTENT -> 0x0040;
            case COHERENT -> 0x0080;
        };
    }

    // Checks that every flag reports the bit that the GL specification assigns
    @ParameterizedTest
    @EnumSource(GlBufferMapFlags.class)
    void everyFlagReportsItsSpecificationBit(GlBufferMapFlags flag) {
        assertEquals(specificationBit(flag), flag.getBits());
    }

    // Checks that every flag holds one bit inside the defined mask and below the sign bit
    @ParameterizedTest
    @EnumSource(GlBufferMapFlags.class)
    void everyFlagIsOnePositiveBitInsideTheMask(GlBufferMapFlags flag) {
        int bits = flag.getBits();
        assertEquals(1, Integer.bitCount(bits));
        assertTrue(bits > 0);
        assertEquals(0, bits & ~ALL_BITS);
        assertEquals(Integer.toUnsignedLong(bits), (long) bits);
        assertEquals(bits, (int) (long) bits);
    }

    // Checks that no two flags share a bit and that their union and their long sum both equal the mask
    @Test
    void flagsShareNoBitAndFillTheMask() {
        int union = 0;
        long sum = 0;
        for (GlBufferMapFlags flag : GlBufferMapFlags.values()) {
            assertEquals(0, union & flag.getBits(), flag.name());
            union |= flag.getBits();
            sum += flag.getBits();
        }
        assertEquals(ALL_BITS, union);
        assertEquals(ALL_BITS, sum);
    }

    // Checks that repeated reads of every flag return the first bit
    @ParameterizedTest
    @EnumSource(GlBufferMapFlags.class)
    void repeatedReadsReturnTheFirstBit(GlBufferMapFlags flag) {
        int first = flag.getBits();
        for (int i = 0; i < REPEATS; i++) {
            if (flag.getBits() != first) {
                fail("Read " + i + " of " + flag + " changed");
            }
        }
    }

    // Checks that a read through the EnumBit interface matches the direct read
    @ParameterizedTest
    @EnumSource(GlBufferMapFlags.class)
    void interfaceReadMatchesTheDirectRead(GlBufferMapFlags flag) {
        EnumBit bit = flag;
        assertEquals(flag.getBits(), bit.getBits());
    }

    // Checks that a field of one flag carries that bit alone and contains that flag alone
    @ParameterizedTest
    @EnumSource(GlBufferMapFlags.class)
    void singleFlagFieldCarriesItsBitAlone(GlBufferMapFlags flag) {
        EnumBitField<GlBufferMapFlags> field = EnumBitField.of(flag);
        assertEquals(flag.getBits(), field.getBitField());
        for (GlBufferMapFlags other : GlBufferMapFlags.values()) {
            assertEquals(other == flag, field.contains(other), other.name());
        }
    }

    // Checks that a field of every flag carries the full mask
    @Test
    void fieldOfEveryFlagCarriesTheFullMask() {
        assertEquals(ALL_BITS, EnumBitField.of(GlBufferMapFlags.values()).getBitField());
    }

    // Checks that a field built from repeated flags carries each bit once
    @Test
    void fieldOfRepeatedFlagsCarriesEachBitOnce() {
        EnumBitField<GlBufferMapFlags> field = EnumBitField.of(GlBufferMapFlags.WRITE, GlBufferMapFlags.WRITE, GlBufferMapFlags.COHERENT, GlBufferMapFlags.WRITE);
        assertEquals(GlBufferMapFlags.WRITE.getBits() | GlBufferMapFlags.COHERENT.getBits(), field.getBitField());
    }

    // Checks that the flags with a storage counterpart carry the bit of that storage flag
    @Test
    void sharedFlagsMatchTheStorageFlagBits() {
        assertEquals(GlBufferStorageFlags.MAP_READ.getBits(), GlBufferMapFlags.READ.getBits());
        assertEquals(GlBufferStorageFlags.MAP_WRITE.getBits(), GlBufferMapFlags.WRITE.getBits());
        assertEquals(GlBufferStorageFlags.PERSISTENT.getBits(), GlBufferMapFlags.PERSISTENT.getBits());
        assertEquals(GlBufferStorageFlags.COHERENT.getBits(), GlBufferMapFlags.COHERENT.getBits());
    }

    // Checks that the flags with no storage counterpart share no bit with any storage flag
    @ParameterizedTest
    @EnumSource(value = GlBufferMapFlags.class, names = {"INVALIDATE_BUFFER", "INVALIDATE_RANGE", "EXPLICIT_FLUSH", "UNSYNCHRONIZED"})
    void mapOnlyFlagsShareNoStorageBit(GlBufferMapFlags flag) {
        for (GlBufferStorageFlags storage : GlBufferStorageFlags.values()) {
            assertEquals(0, flag.getBits() & storage.getBits(), storage.name());
        }
    }

    // Checks that every flag name resolves back to the same constant
    @ParameterizedTest
    @EnumSource(GlBufferMapFlags.class)
    void everyNameResolvesToItsFlag(GlBufferMapFlags flag) {
        assertSame(flag, GlBufferMapFlags.valueOf(flag.name()));
        assertSame(flag, Enum.valueOf(GlBufferMapFlags.class, flag.name()));
        assertSame(flag, GlBufferMapFlags.valueOf(new String(flag.name().toCharArray())));
    }

    // Checks that valueOf rejects a null name
    @Test
    void valueOfRejectsANullName() {
        NullPointerException error = assertThrows(NullPointerException.class, () -> GlBufferMapFlags.valueOf(null));
        assertEquals("Name is null", error.getMessage());
    }

    // Checks that valueOf rejects every name that matches no constant exactly
    @ParameterizedTest
    @ValueSource(strings = {"", " ", "read", "Read", " READ", "READ ", "READ_", "READ\t", "GL_MAP_READ_BIT", "MAP_READ",
            "FLUSH_EXPLICIT", "UNSYNCHRONISED", "0", "1", "-1", "2147483647", "null"})
    void valueOfRejectsUnknownNames(String name) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> GlBufferMapFlags.valueOf(name));
        assertEquals("No enum constant " + GlBufferMapFlags.class.getCanonicalName() + "." + name, error.getMessage());
    }

    // Checks that valueOf rejects a long name that starts with a flag name
    @Test
    void valueOfRejectsALongName() {
        String name = "READ" + "_".repeat(1 << 20);
        assertThrows(IllegalArgumentException.class, () -> GlBufferMapFlags.valueOf(name));
    }

    // Checks that values lists every flag once in declaration order
    @Test
    void valuesListsEveryFlagOnceInDeclarationOrder() {
        GlBufferMapFlags[] flags = GlBufferMapFlags.values();
        assertEquals(COUNT, flags.length);
        List<String> names = new ArrayList<>();
        for (int i = 0; i < flags.length; i++) {
            assertEquals(i, flags[i].ordinal());
            names.add(flags[i].name());
        }
        assertEquals(NAMES, names);
        assertEquals(EnumSet.allOf(GlBufferMapFlags.class), EnumSet.copyOf(Arrays.asList(flags)));
    }

    // Checks that values returns a fresh array that a change to an earlier array leaves intact
    @Test
    void valuesReturnsAFreshArrayEachCall() {
        GlBufferMapFlags[] first = GlBufferMapFlags.values();
        GlBufferMapFlags[] second = GlBufferMapFlags.values();
        assertNotSame(first, second);
        assertArrayEquals(first, second);
        Arrays.fill(first, null);
        assertArrayEquals(second, GlBufferMapFlags.values());
        assertSame(GlBufferMapFlags.READ, GlBufferMapFlags.values()[0]);
    }

    // Checks the first and last index of values and the failure of the indexes beside them and at the int limits
    @Test
    void valuesHonoursItsBoundaryIndexes() {
        GlBufferMapFlags[] flags = GlBufferMapFlags.values();
        assertSame(GlBufferMapFlags.READ, flags[0]);
        assertSame(GlBufferMapFlags.UNSYNCHRONIZED, flags[COUNT - 1]);
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> flags[-1].getBits());
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> flags[COUNT].getBits());
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> flags[Integer.MIN_VALUE].getBits());
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> flags[Integer.MAX_VALUE].getBits());
    }

    // Checks that the constructor refuses a reflective call
    @Test
    void constructorRefusesReflectiveInstantiation() {
        Constructor<?>[] constructors = GlBufferMapFlags.class.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        Constructor<?> constructor = constructors[0];
        assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        assertArrayEquals(new Class<?>[] {String.class, int.class, int.class}, constructor.getParameterTypes());
        constructor.setAccessible(true);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> constructor.newInstance("EXTRA", COUNT, 0x100));
        assertEquals("Cannot reflectively create enum objects", error.getMessage());
        assertEquals(COUNT, GlBufferMapFlags.values().length);
    }

    // Checks the public shape of the enum
    @Test
    void enumKeepsItsShape() throws ReflectiveOperationException {
        Class<GlBufferMapFlags> type = GlBufferMapFlags.class;
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
        Field bit = type.getDeclaredField("bit");
        assertEquals(Modifier.PRIVATE | Modifier.FINAL, bit.getModifiers());
        assertSame(int.class, bit.getType());

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
