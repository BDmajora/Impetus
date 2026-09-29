package com.bdmajora.testing;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;

// Reflection helpers for the static state production code caches at class-init (final fields included) and for private static helpers
public final class Statics {
    private Statics() {}

    // Writes even a static final field, through Unsafe, so cached environment facts can be swapped per test
    public static void set(Class<?> owner, String name, Object value) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            Unsafe unsafe = TestNativeMemory.UNSAFE;
            Object base = unsafe.staticFieldBase(field);
            long offset = unsafe.staticFieldOffset(field);
            if (field.getType() == boolean.class) {
                unsafe.putBoolean(base, offset, (Boolean) value);
            } else if (field.getType() == int.class) {
                unsafe.putInt(base, offset, (Integer) value);
            } else if (field.getType() == long.class) {
                unsafe.putLong(base, offset, (Long) value);
            } else {
                unsafe.putObject(base, offset, value);
            }
        } catch (NoSuchFieldException e) {
            throw new AssertionError(e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    // Invokes a private instance method by name and argument count
    @SuppressWarnings("unchecked")
    public static <T> T callInstance(Object target, String name, Object... args) {
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            for (Method method : c.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == args.length) {
                    method.setAccessible(true);
                    try {
                        return (T) method.invoke(target, args);
                    } catch (InvocationTargetException e) {
                        if (e.getCause() instanceof RuntimeException r) throw r;
                        if (e.getCause() instanceof Error err) throw err;
                        throw new RuntimeException(e.getCause());
                    } catch (IllegalAccessException e) {
                        throw new AssertionError(e);
                    }
                }
            }
        }
        throw new AssertionError("no method " + name + "/" + args.length + " on " + target.getClass().getName());
    }

    // Invokes a private static method by name and argument count
    @SuppressWarnings("unchecked")
    public static <T> T call(Class<?> owner, String name, Object... args) {
        for (Method method : owner.getDeclaredMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == args.length) {
                method.setAccessible(true);
                try {
                    return (T) method.invoke(null, args);
                } catch (InvocationTargetException e) {
                    if (e.getCause() instanceof RuntimeException r) throw r;
                    if (e.getCause() instanceof Error err) throw err;
                    throw new RuntimeException(e.getCause());
                } catch (IllegalAccessException e) {
                    throw new AssertionError(e);
                }
            }
        }
        throw new AssertionError("no static method " + name + "/" + args.length + " on " + owner.getName());
    }
}
