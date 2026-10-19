package com.bdmajora.testing;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;

// Reflection helpers for the static state production code caches at class-init (final fields included) and for private static helpers
public final class Statics {
    // sun.misc.Unsafe is the only way to write a static final; it is reached by name, since naming the type in source draws javac's proprietary-API and removal warnings
    private static final MethodHandle ALLOCATE_INSTANCE, STATIC_FIELD_BASE, STATIC_FIELD_OFFSET, PUT_BOOLEAN, PUT_INT, PUT_LONG, PUT_OBJECT;

    static {
        try {
            Class<?> type = Class.forName("sun.misc.Unsafe");
            Field theUnsafe = type.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            Object unsafe = theUnsafe.get(null);
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            ALLOCATE_INSTANCE = lookup.findVirtual(type, "allocateInstance", MethodType.methodType(Object.class, Class.class)).bindTo(unsafe);
            STATIC_FIELD_BASE = lookup.findVirtual(type, "staticFieldBase", MethodType.methodType(Object.class, Field.class)).bindTo(unsafe);
            STATIC_FIELD_OFFSET = lookup.findVirtual(type, "staticFieldOffset", MethodType.methodType(long.class, Field.class)).bindTo(unsafe);
            PUT_BOOLEAN = lookup.findVirtual(type, "putBoolean", MethodType.methodType(void.class, Object.class, long.class, boolean.class)).bindTo(unsafe);
            PUT_INT = lookup.findVirtual(type, "putInt", MethodType.methodType(void.class, Object.class, long.class, int.class)).bindTo(unsafe);
            PUT_LONG = lookup.findVirtual(type, "putLong", MethodType.methodType(void.class, Object.class, long.class, long.class)).bindTo(unsafe);
            PUT_OBJECT = lookup.findVirtual(type, "putObject", MethodType.methodType(void.class, Object.class, long.class, Object.class)).bindTo(unsafe);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private Statics() {}

    // A bare instance, no constructor run, for classes whose constructors need a running game
    public static <T> T allocate(Class<T> type) {
        try {
            return type.cast(ALLOCATE_INSTANCE.invoke(type));
        } catch (Throwable e) {
            throw new AssertionError(e);
        }
    }

    // Writes even a static final field, through Unsafe, so cached environment facts can be swapped per test
    public static void set(Class<?> owner, String name, Object value) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            Object base = STATIC_FIELD_BASE.invoke(field);
            long offset = (long) STATIC_FIELD_OFFSET.invoke(field);
            if (field.getType() == boolean.class) {
                PUT_BOOLEAN.invoke(base, offset, (boolean) (Boolean) value);
            } else if (field.getType() == int.class) {
                PUT_INT.invoke(base, offset, (int) (Integer) value);
            } else if (field.getType() == long.class) {
                PUT_LONG.invoke(base, offset, (long) (Long) value);
            } else {
                PUT_OBJECT.invoke(base, offset, value);
            }
        } catch (Throwable e) {
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
