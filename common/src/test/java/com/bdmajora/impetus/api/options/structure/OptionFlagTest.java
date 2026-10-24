package com.bdmajora.impetus.api.options.structure;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class OptionFlagTest {
    private static final List<String> NAMES = List.of(
            "REQUIRES_RENDERER_RELOAD",
            "REQUIRES_RENDERER_UPDATE",
            "REQUIRES_ASSET_RELOAD",
            "REQUIRES_GAME_RESTART",
            "REQUIRES_SHADER_PIPELINE_RELOAD");

    @Test
    void theFlagsAreTheFiveApplyKnowsInDeclarationOrder() {
        OptionFlag[] flags = OptionFlag.values();
        assertEquals(NAMES, Arrays.stream(flags).map(Enum::name).toList());
        for (int i = 0; i < flags.length; i++) {
            assertEquals(i, flags[i].ordinal());
            assertEquals(NAMES.get(i), flags[i].toString());
        }
        // A set of them iterates in that order too
        assertEquals(List.of(flags), List.copyOf(EnumSet.allOf(OptionFlag.class)));
    }

    @Test
    void theEnumCarriesNoStateOrMethodsOfItsOwn() {
        // No constant has a body, so the type is final and every flag is exactly an OptionFlag
        assertTrue(Modifier.isPublic(OptionFlag.class.getModifiers()));
        assertTrue(Modifier.isFinal(OptionFlag.class.getModifiers()));
        for (OptionFlag flag : OptionFlag.values()) {
            assertSame(OptionFlag.class, flag.getClass());
            assertSame(OptionFlag.class, flag.getDeclaringClass());
        }
        // Fields: the constants and javac's own values array, nothing per flag
        Set<String> fields = Arrays.stream(OptionFlag.class.getDeclaredFields()).filter(f -> !f.isSynthetic()).map(Field::getName).collect(Collectors.toSet());
        assertEquals(Set.copyOf(NAMES), fields);
        for (Field field : OptionFlag.class.getDeclaredFields()) {
            assertTrue(Modifier.isStatic(field.getModifiers()), field.getName());
            assertTrue(Modifier.isFinal(field.getModifiers()), field.getName());
        }
        // Methods: only the two the compiler writes
        Set<String> methods = Arrays.stream(OptionFlag.class.getDeclaredMethods()).filter(m -> !m.isSynthetic()).map(Method::getName).collect(Collectors.toSet());
        assertEquals(Set.of("values", "valueOf"), methods);
        Constructor<?>[] constructors = OptionFlag.class.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        assertTrue(Modifier.isPrivate(constructors[0].getModifiers()));
    }

    @Test
    void valueOfAnswersOnlyExactNames() {
        for (OptionFlag flag : OptionFlag.values()) {
            assertSame(flag, OptionFlag.valueOf(flag.name()));
            assertSame(flag, Enum.valueOf(OptionFlag.class, flag.name()));
            // Case and surrounding space are not forgiven
            String name = flag.name();
            assertThrows(IllegalArgumentException.class, () -> OptionFlag.valueOf(name.toLowerCase(Locale.ROOT)));
            assertThrows(IllegalArgumentException.class, () -> OptionFlag.valueOf(" " + name));
            assertThrows(IllegalArgumentException.class, () -> OptionFlag.valueOf(name + " "));
        }
        assertThrows(IllegalArgumentException.class, () -> OptionFlag.valueOf(""));
        assertThrows(IllegalArgumentException.class, () -> OptionFlag.valueOf("REQUIRES_RENDERER"));
        assertThrows(NullPointerException.class, () -> OptionFlag.valueOf(null));
    }

    @Test
    void valuesHandsOutAFreshArrayEachCall() {
        OptionFlag[] first = OptionFlag.values();
        OptionFlag[] second = OptionFlag.values();
        assertNotSame(first, second);
        // Writing into one leaves the next call's untouched
        Arrays.fill(first, null);
        assertArrayEquals(second, OptionFlag.values());
        assertEquals(NAMES.size(), OptionFlag.values().length);
    }
}
