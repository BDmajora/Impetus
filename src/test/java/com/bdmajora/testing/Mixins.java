package com.bdmajora.testing;

import org.mockito.Mockito;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

// Drives mixin classes as plain Java.
public final class Mixins {
    private Mixins() {}

    // Returns a mixin instance with its constructor run.
    public static <T> T instance(Class<T> mixin) {
        try {
            return Mockito.mock(mixin, Mockito.withSettings().useConstructor().defaultAnswer(Mockito.CALLS_REAL_METHODS));
        } catch (RuntimeException | Error e) {
            return Mockito.mock(mixin, Mockito.withSettings().defaultAnswer(Mockito.CALLS_REAL_METHODS));
        }
    }

    // Returns a mixin instance carrying extra interfaces.
    public static <T> T instanceWith(Class<T> mixin, Class<?>... interfaces) {
        try {
            return Mockito.mock(mixin, Mockito.withSettings().extraInterfaces(interfaces).useConstructor()
                    .defaultAnswer(Mockito.CALLS_REAL_METHODS));
        } catch (RuntimeException | Error e) {
            return Mockito.mock(mixin, Mockito.withSettings().extraInterfaces(interfaces)
                    .defaultAnswer(Mockito.CALLS_REAL_METHODS));
        }
    }

    // Verifies a call Mockito cannot see.
    public static void verifyCall(Object mock, String name, Object... args) {
        Method method = find(mock.getClass(), name, args);
        method.setAccessible(true);
        try {
            method.invoke(Mockito.verify(mock), args);
        } catch (InvocationTargetException | IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    // Returns a mixin instance built with the given arguments.
    public static <T> T instance(Class<T> mixin, Object... constructorArgs) {
        return Mockito.mock(mixin, Mockito.withSettings().useConstructor(constructorArgs).defaultAnswer(Mockito.CALLS_REAL_METHODS));
    }

    // Stubs a method Mockito cannot see for any arguments.
    public static void stub(Object mock, String name, org.mockito.stubbing.Answer<?> answer) {
        Method method = null;
        for (Class<?> c = mock.getClass(); c != null && method == null; c = c.getSuperclass()) {
            for (Method candidate : c.getDeclaredMethods()) {
                if (candidate.getName().equals(name) && !candidate.isSynthetic()) {
                    method = candidate;
                    break;
                }
            }
        }
        if (method == null) {
            throw new AssertionError("no method " + name + " on " + mock.getClass().getName());
        }
        method.setAccessible(true);
        Mockito.doAnswer(answer).when(mock);
        Object[] args = new Object[method.getParameterCount()];
        for (int i = 0; i < args.length; i++) {
            Class<?> type = method.getParameterTypes()[i];
            org.mockito.ArgumentMatchers.any();
            args[i] = type.isPrimitive() ? defaultValue(type) : null;
        }
        try {
            method.invoke(mock, args);
        } catch (InvocationTargetException | IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == char.class) return (char) 0;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        return 0;
    }

    public static CallbackInfo ci() {
        return new CallbackInfo("test", true);
    }

    public static <R> CallbackInfoReturnable<R> cir() {
        return new CallbackInfoReturnable<>("test", true);
    }

    public static <R> CallbackInfoReturnable<R> cir(R initial) {
        return new CallbackInfoReturnable<>("test", true, initial);
    }

    // Invokes a handler by name on an instance or class.
    @SuppressWarnings("unchecked")
    public static <R> R call(Object target, String name, Object... args) {
        Class<?> type = target instanceof Class<?> c ? c : target.getClass();
        Object receiver = target instanceof Class<?> ? null : target;
        Method method = find(type, name, args);
        method.setAccessible(true);
        try {
            return (R) method.invoke(receiver, args);
        } catch (InvocationTargetException e) {
            throw rethrow(e.getCause());
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    // Returns an allocated instance of a mixin whose target Mockito cannot instrument.
    public static <T> T concrete(Class<T> mixin) {
        Class<? extends T> made = concreteClass(mixin);
        // Runs the mixin's constructor when it can.
        try {
            java.lang.reflect.Constructor<?> constructor = made.getDeclaredConstructor();
            constructor.setAccessible(true);
            return mixin.cast(constructor.newInstance());
        } catch (ReflectiveOperationException | RuntimeException | Error e) {
            return mixin.cast(Mc.uninitialized(made));
        }
    }

    // Returns a concrete instance built with these arguments.
    public static <T> T concrete(Class<T> mixin, Object... args) {
        Class<? extends T> made = concreteClass(mixin);
        for (java.lang.reflect.Constructor<?> constructor : made.getDeclaredConstructors()) {
            if (constructor.getParameterCount() != args.length || !matches(constructor.getParameterTypes(), args)) {
                continue;
            }
            constructor.setAccessible(true);
            try {
                return mixin.cast(constructor.newInstance(args));
            } catch (InvocationTargetException e) {
                throw rethrow(e.getCause());
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        }
        throw new AssertionError("no constructor/" + args.length + " on " + made.getName());
    }

    private static <T> Class<? extends T> concreteClass(Class<T> mixin) {
        Class<? extends T> made;
        try {
            // Defines the subclass in the mixin's own package.
            java.lang.invoke.MethodHandles.Lookup lookup =
                    java.lang.invoke.MethodHandles.privateLookupIn(mixin, java.lang.invoke.MethodHandles.lookup());
            made = subclass(mixin, net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy.Default.IMITATE_SUPER_CLASS, lookup);
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        } catch (RuntimeException e) {
            // Allocates the instance bare when the constructor cannot be copied.
            try {
                made = subclass(mixin, net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy.Default.NO_CONSTRUCTORS,
                        java.lang.invoke.MethodHandles.privateLookupIn(mixin, java.lang.invoke.MethodHandles.lookup()));
            } catch (IllegalAccessException second) {
                throw new AssertionError(second);
            }
        }
        return made;
    }

    private static <T> Class<? extends T> subclass(Class<T> mixin,
                                                  net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy strategy,
                                                  java.lang.invoke.MethodHandles.Lookup lookup) {
        return new net.bytebuddy.ByteBuddy()
                .subclass(mixin, strategy)
                .method(net.bytebuddy.matcher.ElementMatchers.isAbstract())
                .intercept(net.bytebuddy.implementation.StubMethod.INSTANCE)
                .make()
                .load(mixin.getClassLoader(), net.bytebuddy.dynamic.loading.ClassLoadingStrategy.UsingLookup.of(lookup))
                .getLoaded();
    }

    // Runs a private constructor.
    @SuppressWarnings("unchecked")
    public static <T> T construct(Class<T> type, Object... args) {
        for (java.lang.reflect.Constructor<?> c : type.getDeclaredConstructors()) {
            if (c.getParameterCount() != args.length) {
                continue;
            }
            c.setAccessible(true);
            try {
                return (T) c.newInstance(args);
            } catch (InvocationTargetException e) {
                throw rethrow(e.getCause());
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        }
        throw new AssertionError("no constructor/" + args.length + " on " + type.getName());
    }

    public static void set(Object target, String field, Object value) {
        Field f = field(target instanceof Class<?> c ? c : target.getClass(), field);
        try {
            f.set(target instanceof Class<?> ? null : target, value);
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(Object target, String field) {
        Field f = field(target instanceof Class<?> c ? c : target.getClass(), field);
        try {
            return (T) f.get(target instanceof Class<?> ? null : target);
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    private static Field field(Class<?> type, String name) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getName().equals(name)) {
                    f.setAccessible(true);
                    return f;
                }
            }
        }
        throw new AssertionError("no field " + name + " on " + type.getName());
    }

    // Finds a declared method on the nearest class.
    private static Method find(Class<?> type, String name, Object[] args) {
        Method fallback = null;
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals(name) || m.getParameterCount() != args.length || m.isSynthetic()) {
                    continue;
                }
                if (matches(m.getParameterTypes(), args)) {
                    return m;
                }
                fallback = fallback == null ? m : fallback;
            }
        }
        if (fallback != null) {
            return fallback;
        }
        throw new AssertionError("no method " + name + "/" + args.length + " on " + type.getName() + " taking " + Arrays.toString(args));
    }

    private static boolean matches(Class<?>[] params, Object[] args) {
        for (int i = 0; i < params.length; i++) {
            if (args[i] == null) {
                if (params[i].isPrimitive()) {
                    return false;
                }
                continue;
            }
            if (!box(params[i]).isInstance(args[i])) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> box(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == boolean.class) return Boolean.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == char.class) return Character.class;
        return type;
    }

    public static boolean isStatic(Method m) {
        return Modifier.isStatic(m.getModifiers());
    }

    private static RuntimeException rethrow(Throwable t) {
        if (t instanceof RuntimeException r) return r;
        if (t instanceof Error e) throw e;
        return new RuntimeException(t);
    }
}
