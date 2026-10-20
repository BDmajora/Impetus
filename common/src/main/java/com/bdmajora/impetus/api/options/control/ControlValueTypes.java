package com.bdmajora.impetus.api.options.control;

import java.lang.invoke.MethodType;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.HashMap;
import java.util.Map;

// Value types of control classes, read once per class from the type argument each one gives Control
final class ControlValueTypes {
    // Object for a class that leaves the argument open or implements Control raw, since such a control may hold anything
    private static final ClassValue<Class<?>> DECLARED = new ClassValue<>() {
        @Override
        protected Class<?> computeValue(Class<?> type) {
            Class<?> found = find(type, Map.of());
            return found != null ? found : Object.class;
        }
    };

    private ControlValueTypes() {
    }

    // The boxed class the control class binds Control's type parameter to, or Object when it cannot tell
    static Class<?> declared(Class<?> type) {
        return DECLARED.get(type);
    }

    // Whether either type can be read as the other, so only unrelated types count as a mismatch; null is unknown and matches anything
    static boolean related(Class<?> a, Class<?> b) {
        // Identity first, which is the usual case once both sides are boxed
        if (a == b || a == null || b == null) {
            return true;
        }
        Class<?> x = boxed(a);
        Class<?> y = boxed(b);
        return x.isAssignableFrom(y) || y.isAssignableFrom(x);
    }

    // The wrapper class of a primitive, else the class itself
    static Class<?> boxed(Class<?> type) {
        return type.isPrimitive() ? MethodType.methodType(type).wrap().returnType() : type;
    }

    // What Control's parameter is bound to along the class's supertypes, given what its own parameters are bound to; null when no supertype leads to Control
    private static Class<?> find(Class<?> type, Map<TypeVariable<?>, Class<?>> arguments) {
        for (Type parent : type.getGenericInterfaces()) {
            Class<?> found = through(parent, arguments);
            if (found != null) {
                return found;
            }
        }
        Type parent = type.getGenericSuperclass();
        return parent != null ? through(parent, arguments) : null;
    }

    // Follows one supertype, passing on what it binds its own parameters to
    private static Class<?> through(Type parent, Map<TypeVariable<?>, Class<?>> arguments) {
        if (parent instanceof ParameterizedType parameterized) {
            Class<?> raw = (Class<?>) parameterized.getRawType();
            Type[] actual = parameterized.getActualTypeArguments();
            if (raw == Control.class) {
                return erase(actual[0], arguments);
            }
            // Prunes supertypes such as Comparable that cannot lead to Control
            if (!Control.class.isAssignableFrom(raw)) {
                return null;
            }
            TypeVariable<?>[] parameters = raw.getTypeParameters();
            Map<TypeVariable<?>, Class<?>> passed = new HashMap<>();
            for (int i = 0; i < parameters.length; i++) {
                passed.put(parameters[i], erase(actual[i], arguments));
            }
            return find(raw, passed);
        }
        Class<?> raw = (Class<?>) parent;
        // A raw Control binds nothing
        if (raw == Control.class) {
            return Object.class;
        }
        return Control.class.isAssignableFrom(raw) ? find(raw, Map.of()) : null;
    }

    // The class an argument stands for, with a parameter nothing binds standing for its first bound
    private static Class<?> erase(Type type, Map<TypeVariable<?>, Class<?>> arguments) {
        if (type instanceof Class<?> plain) {
            return plain;
        }
        if (type instanceof ParameterizedType parameterized) {
            return (Class<?>) parameterized.getRawType();
        }
        if (type instanceof GenericArrayType array) {
            return erase(array.getGenericComponentType(), arguments).arrayType();
        }
        if (type instanceof TypeVariable<?> variable) {
            Class<?> bound = arguments.get(variable);
            return bound != null ? bound : erase(variable.getBounds()[0], Map.of());
        }
        // Wildcards cannot appear in a supertype's arguments, so this only guards against the impossible
        return Object.class;
    }
}