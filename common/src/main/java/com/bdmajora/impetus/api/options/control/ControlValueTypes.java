package com.bdmajora.impetus.api.options.control;

import java.io.IOException;
import java.io.InputStream;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassSignature;
import java.lang.classfile.Signature;
import java.lang.classfile.attribute.SignatureAttribute;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.MalformedParameterizedTypeException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

// Value types of control classes read once per class.
final class ControlValueTypes {
    // Value type of each control class or Object when the argument is open.
    private static final ClassValue<Class<?>> DECLARED = new ClassValue<>() {
        @Override
        protected Class<?> computeValue(Class<?> type) {
            // Finds the bound type for control classes only.
            Class<?> found = Control.class.isAssignableFrom(type) ? find(type, Map.of()) : null;
            return found != null ? found : Object.class;
        }
    };

    private static final Map<Class<?>, Class<?>> WRAPPERS = Map.of(boolean.class, Boolean.class, byte.class, Byte.class, short.class, Short.class,
            char.class, Character.class, int.class, Integer.class, long.class, Long.class, float.class, Float.class, double.class, Double.class, void.class, Void.class);

    // Marks a parameter whose bound is being erased.
    private static final Class<?> ERASING = void.class;

    private ControlValueTypes() {
    }

    // Returns the boxed class a control class binds Control's type parameter to or Object.
    static Class<?> declared(Class<?> type) {
        return DECLARED.get(type);
    }

    // Returns whether one value can be of both types with null matching anything.
    static boolean related(Class<?> a, Class<?> b) {
        // Returns true for the same class or a null side.
        if (a == b || a == null || b == null) {
            return true;
        }
        return shared(boxed(a), boxed(b));
    }

    // Returns the wrapper class of a primitive or the class itself.
    static Class<?> boxed(Class<?> type) {
        return type.isPrimitive() ? WRAPPERS.get(type) : type;
    }

    // Returns whether some object can be an instance of both reference types.
    private static boolean shared(Class<?> x, Class<?> y) {
        if (x.isAssignableFrom(y) || y.isAssignableFrom(x)) {
            return true;
        }
        if (x.isArray() || y.isArray()) {
            Class<?> u = x.getComponentType();
            Class<?> v = y.getComponentType();
            // Returns whether two array types share a component type.
            return u != null && v != null && !u.isPrimitive() && !v.isPrimitive() && shared(u, v);
        }
        // Returns whether two classes lie on one superclass line.
        if ((!x.isInterface() && !y.isInterface()) || closed(x) || closed(y)) {
            return false;
        }
        Class<?> sealed = x.isSealed() ? x : y.isSealed() ? y : null;
        if (sealed == null) {
            return true;
        }
        // A sealed type meets the other only through one of the subtypes it permits
        Class<?> other = sealed == x ? y : x;
        for (Class<?> permitted : sealed.getPermittedSubclasses()) {
            if (shared(permitted, other)) {
                return true;
            }
        }
        return false;
    }

    // Whether no subclass can bring in an interface the class does not implement itself
    private static boolean closed(Class<?> type) {
        return Modifier.isFinal(type.getModifiers()) || type.isEnum();
    }

    // Returns what Control's parameter is bound to along the class's supertypes or null.
    private static Class<?> find(Class<?> type, Map<TypeVariable<?>, Class<?>> arguments) {
        Type[] interfaces = read(type::getGenericInterfaces, () -> rebuiltInterfaces(type, arguments));
        Type superclass = read(type::getGenericSuperclass, () -> rebuiltSuperclass(type, arguments));
        Class<?> found = walk(interfaces, superclass, arguments);
        // Falls back to the erased supertypes when the signature misses Control.
        return found != null ? found : walk(type.getInterfaces(), type.getSuperclass(), Map.of());
    }

    // The first answer along the interfaces and then the superclass
    private static Class<?> walk(Type[] interfaces, Type superclass, Map<TypeVariable<?>, Class<?>> arguments) {
        for (Type parent : interfaces) {
            Class<?> found = through(parent, arguments);
            if (found != null) {
                return found;
            }
        }
        return superclass != null ? through(superclass, arguments) : null;
    }

    // Follows one supertype and passes on what it binds its own parameters to.
    private static Class<?> through(Type parent, Map<TypeVariable<?>, Class<?>> arguments) {
        if (parent instanceof ParameterizedType parameterized) {
            Class<?> raw = (Class<?>) parameterized.getRawType();
            // Prunes supertypes such as Comparable that cannot lead to Control
            if (!Control.class.isAssignableFrom(raw)) {
                return null;
            }
            if (raw == Control.class) {
                Class<?> given = erase(parameterized.getActualTypeArguments()[0], arguments);
                return given != null ? given : Object.class;
            }
            return find(raw, bind(parameterized, arguments));
        }
        Class<?> raw = (Class<?>) parent;
        // A raw Control binds nothing
        if (raw == Control.class) {
            return Object.class;
        }
        // Returns the cached answer for a raw parent.
        return Control.class.isAssignableFrom(raw) ? declared(raw) : null;
    }

    // Returns what a parameterized parent binds the parameters of its class and outer classes to.
    private static Map<TypeVariable<?>, Class<?>> bind(ParameterizedType parent, Map<TypeVariable<?>, Class<?>> arguments) {
        Map<TypeVariable<?>, Class<?>> passed = new HashMap<>();
        for (Type level = parent; level instanceof ParameterizedType parameterized; level = parameterized.getOwnerType()) {
            // Reads the type parameters or none for a malformed signature.
            TypeVariable<?>[] parameters = read(((Class<?>) parameterized.getRawType())::getTypeParameters, () -> new TypeVariable<?>[0]);
            Type[] actual = parameterized.getActualTypeArguments();
            for (int i = 0; i < parameters.length; i++) {
                Class<?> given = erase(actual[i], arguments);
                // An argument that is unknown or says no more than Object leaves the parameter to its own bound
                if (given != null && given != Object.class) {
                    passed.put(parameters[i], given);
                }
            }
        }
        return passed;
    }

    // Returns the class an argument stands for or null.
    private static Class<?> erase(Type type, Map<TypeVariable<?>, Class<?>> arguments) {
        if (type instanceof Class<?> plain) {
            return plain;
        }
        if (type instanceof ParameterizedType parameterized) {
            return (Class<?>) parameterized.getRawType();
        }
        if (type instanceof GenericArrayType array) {
            Class<?> component = erase(array.getGenericComponentType(), arguments);
            return component != null ? arrayOf(component) : null;
        }
        if (type instanceof TypeVariable<?> variable) {
            Class<?> bound = arguments.get(variable);
            if (bound != null) {
                // Returns the bound unless the parameter is still being erased.
                return bound != ERASING ? bound : null;
            }
            Map<TypeVariable<?>, Class<?>> erasing = new HashMap<>(arguments);
            erasing.put(variable, ERASING);
            Type[] bounds = read(variable::getBounds, () -> null);
            return bounds != null ? erase(bounds[0], erasing) : null;
        }
        // Reads a wildcard as its upper bound.
        if (type instanceof WildcardType wildcard) {
            Type[] upper = read(wildcard::getUpperBounds, () -> null);
            return upper != null ? erase(upper[0], arguments) : null;
        }
        return null;
    }

    // Returns the array class of the component or null past 255 dimensions.
    private static Class<?> arrayOf(Class<?> component) {
        return read(component::arrayType, () -> null);
    }

    // Rebuilds the class's interfaces one by one from its class file.
    private static Type[] rebuiltInterfaces(Class<?> type, Map<TypeVariable<?>, Class<?>> arguments) {
        ClassSignature signature = signature(type);
        List<Signature.ClassTypeSig> named = signature != null ? signature.superinterfaceSignatures() : List.of();
        Class<?>[] erased = type.getInterfaces();
        Type[] rebuilt = new Type[erased.length];
        for (int i = 0; i < erased.length; i++) {
            rebuilt[i] = rebuild(type, erased[i], named, arguments);
        }
        return rebuilt;
    }

    // The class's superclass rebuilt from its class file the same way
    private static Type rebuiltSuperclass(Class<?> type, Map<TypeVariable<?>, Class<?>> arguments) {
        ClassSignature signature = signature(type);
        return rebuild(type, type.getSuperclass(), signature != null ? List.of(signature.superclassSignature()) : List.of(), arguments);
    }

    // Returns the class's own signature from its class file or null.
    private static ClassSignature signature(Class<?> type) {
        String name = type.getName().replace('.', '/');
        byte[] bytes = classFile(type, name);
        if (bytes == null) {
            return null;
        }
        try {
            ClassModel model = ClassFile.of().parse(bytes);
            // Rejects a class file of another class.
            if (!model.thisClass().asInternalName().equals(name)) {
                return null;
            }
            return model.findAttribute(Attributes.signature()).map(SignatureAttribute::asClassSignature).orElse(null);
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    // Returns the class file bytes from the class's loader or null.
    private static byte[] classFile(Class<?> type, String name) {
        try (InputStream file = type.getResourceAsStream("/" + name + ".class")) {
            return file != null ? file.readAllBytes() : null;
        } catch (IOException unreadable) {
            return null;
        }
    }

    // Returns the named supertype that is this class with its arguments erased.
    private static Type rebuild(Class<?> type, Class<?> raw, List<Signature.ClassTypeSig> named, Map<TypeVariable<?>, Class<?>> arguments) {
        for (Signature.ClassTypeSig signature : named) {
            if (raw != null && binaryName(signature).equals(raw.getName())) {
                Type owner = rebuild(type, read(raw::getDeclaringClass, () -> null), signature.outerType().map(List::of).orElse(List.of()), arguments);
                TypeVariable<?>[] parameters = read(raw::getTypeParameters, () -> new TypeVariable<?>[0]);
                List<Signature.TypeArg> given = signature.typeArgs();
                Type[] actual = new Type[parameters.length];
                // Binds the arguments only when their count matches the parameters.
                if (given.size() == parameters.length) {
                    for (int i = 0; i < parameters.length; i++) {
                        actual[i] = argument(type, given.get(i), arguments);
                    }
                }
                return new Rebuilt(raw, actual, owner);
            }
        }
        return raw;
    }

    // Returns the binary name of the class a signature names.
    private static String binaryName(Signature.ClassTypeSig signature) {
        return signature.outerType().map(outer -> binaryName(outer) + "$" + signature.className()).orElse(signature.className().replace('/', '.'));
    }

    // Returns what one signature argument erases to.
    private static Class<?> argument(Class<?> type, Signature.TypeArg given, Map<TypeVariable<?>, Class<?>> arguments) {
        if (given instanceof Signature.TypeArg.Bounded bounded && bounded.wildcardIndicator() != Signature.TypeArg.Bounded.WildcardIndicator.SUPER) {
            return resolve(type, bounded.boundType(), arguments);
        }
        return Object.class;
    }

    // Returns the class a signature stands for in the class's scope or null.
    private static Class<?> resolve(Class<?> type, Signature signature, Map<TypeVariable<?>, Class<?>> arguments) {
        return switch (signature) {
            // A base type only ever stands as an array's component
            case Signature.BaseTypeSig base -> MethodType.fromMethodDescriptorString("()" + base.baseType(), null).returnType();
            case Signature.ClassTypeSig named -> load(binaryName(named), type.getClassLoader());
            case Signature.TypeVarSig variable -> {
                TypeVariable<?> declared = parameter(type, variable.identifier());
                yield declared != null ? erase(declared, arguments) : null;
            }
            case Signature.ArrayTypeSig array -> {
                Class<?> component = resolve(type, array.componentSignature(), arguments);
                yield component != null ? arrayOf(component) : null;
            }
        };
    }

    // Returns the class of that name or null when it is missing or will not link.
    private static Class<?> load(String name, ClassLoader loader) {
        try {
            return Class.forName(name, false, loader);
        } catch (ClassNotFoundException | LinkageError missing) {
            return null;
        }
    }

    // Returns the type parameter a class signature names.
    private static TypeVariable<?> parameter(Class<?> type, String name) {
        // Scopes already searched.
        Set<GenericDeclaration> searched = new HashSet<>();
        for (GenericDeclaration scope = type; scope != null && searched.add(scope); scope = enclosing(scope)) {
            for (TypeVariable<?> variable : read(scope::getTypeParameters, () -> new TypeVariable<?>[0])) {
                if (variable.getName().equals(name)) {
                    return variable;
                }
            }
        }
        return null;
    }

    // Returns the declaration around a class or the class that declares a method.
    private static GenericDeclaration enclosing(GenericDeclaration scope) {
        if (scope instanceof Executable executable) {
            return executable.getDeclaringClass();
        }
        Class<?> type = (Class<?>) scope;
        Method method = around(type::getEnclosingMethod);
        if (method != null) {
            return method;
        }
        Constructor<?> constructor = around(type::getEnclosingConstructor);
        return constructor != null ? constructor : around(type::getEnclosingClass);
    }

    // Returns the result of one lookup or null when reflection throws.
    private static <T> T around(Supplier<T> lookup) {
        try {
            return lookup.get();
        } catch (TypeNotPresentException | LinkageError | InternalError malformed) {
            return null;
        }
    }

    // Reads a signature or returns the fallback when it cannot be read.
    private static <T> T read(Supplier<T> generic, Supplier<T> fallback) {
        try {
            return generic.get();
        } catch (TypeNotPresentException | MalformedParameterizedTypeException | UnsupportedOperationException | LinkageError unreadable) {
            return fallback.get();
        }
    }

    // A supertype rebuilt from a class file.
    private record Rebuilt(Class<?> getRawType, Type[] getActualTypeArguments, Type getOwnerType) implements ParameterizedType {
    }
}
