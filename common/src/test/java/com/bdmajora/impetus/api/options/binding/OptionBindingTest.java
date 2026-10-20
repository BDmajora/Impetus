package com.bdmajora.impetus.api.options.binding;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class OptionBindingTest {
    @Test
    void shapeIsTwoAbstractMethodsOverTwoUnboundedTypeParameters() throws NoSuchMethodException {
        Class<?> type = OptionBinding.class;
        assertTrue(type.isInterface() && !type.isAnnotation());
        assertTrue(Modifier.isPublic(type.getModifiers()));
        assertEquals(0, type.getInterfaces().length);
        assertEquals(0, type.getDeclaredClasses().length);
        assertEquals(0, Arrays.stream(type.getDeclaredFields()).filter(f -> !f.isSynthetic()).count());
        TypeVariable<?>[] parameters = type.getTypeParameters();
        assertEquals(List.of("S", "T"), Arrays.stream(parameters).map(TypeVariable::getName).toList());
        for (TypeVariable<?> parameter : parameters) {
            assertArrayEquals(new Type[]{Object.class}, parameter.getBounds(), parameter::toString);
        }
        // Erasures are what compiled mods link against; a new abstract method breaks every implementor, and a default or static one is new API
        Set<String> methods = Arrays.stream(type.getDeclaredMethods())
                .filter(m -> !m.isSynthetic())
                .peek(m -> assertTrue(Modifier.isPublic(m.getModifiers()) && Modifier.isAbstract(m.getModifiers()) && !m.isDefault(), m::toString))
                .map(m -> m.getReturnType().getName() + " " + m.getName() + Arrays.toString(m.getParameterTypes()))
                .collect(Collectors.toSet());
        assertEquals(Set.of("void setValue[class java.lang.Object, class java.lang.Object]", "java.lang.Object getValue[class java.lang.Object]"), methods);
        // Generic signatures, so the compiler ties the storage to S and the value to T
        Method set = type.getMethod("setValue", Object.class, Object.class);
        assertArrayEquals(new Type[]{parameters[0], parameters[1]}, set.getGenericParameterTypes());
        Method get = type.getMethod("getValue", Object.class);
        assertArrayEquals(new Type[]{parameters[0]}, get.getGenericParameterTypes());
        assertEquals(parameters[1], get.getGenericReturnType());
        assertEquals(0, set.getExceptionTypes().length + get.getExceptionTypes().length);
    }
}
