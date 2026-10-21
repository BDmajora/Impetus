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

// Value types of control classes, read once per class from the type argument each one gives Control
final class ControlValueTypes {
    // Object for a class that leaves the argument open or implements Control raw, since such a control may hold anything
    private static final ClassValue<Class<?>> DECLARED = new ClassValue<>() {
        @Override
        protected Class<?> computeValue(Class<?> type) {
            // Neither Control itself nor a class outside it binds the parameter, so their signatures are never read
            Class<?> found = Control.class.isAssignableFrom(type) ? find(type, Map.of()) : null;
            return found != null ? found : Object.class;
        }
    };

    private static final Map<Class<?>, Class<?>> WRAPPERS = Map.of(boolean.class, Boolean.class, byte.class, Byte.class, short.class, Short.class,
            char.class, Character.class, int.class, Integer.class, long.class, Long.class, float.class, Float.class, double.class, Double.class, void.class, Void.class);

    // Marks a parameter whose bound is being erased, which no argument can erase to since arguments are never primitive
    private static final Class<?> ERASING = void.class;

    private ControlValueTypes() {
    }

    // The boxed class the control class binds Control's type parameter to, or Object when it cannot tell
    static Class<?> declared(Class<?> type) {
        return DECLARED.get(type);
    }

    // Whether one value can be of both types, so only types no value shares count as a mismatch; null is unknown and matches anything
    static boolean related(Class<?> a, Class<?> b) {
        // Identity first, which is the usual case once both sides are boxed
        if (a == b || a == null || b == null) {
            return true;
        }
        return shared(boxed(a), boxed(b));
    }

    // The wrapper class of a primitive, else the class itself
    static Class<?> boxed(Class<?> type) {
        return type.isPrimitive() ? WRAPPERS.get(type) : type;
    }

    // Whether some object is an instance of both reference types, by the rules of a legal cast between them
    private static boolean shared(Class<?> x, Class<?> y) {
        if (x.isAssignableFrom(y) || y.isAssignableFrom(x)) {
            return true;
        }
        if (x.isArray() || y.isArray()) {
            Class<?> u = x.getComponentType();
            Class<?> v = y.getComponentType();
            // Only arrays meet arrays, and unequal primitive components never do, so int[] and Integer[] stay apart
            return u != null && v != null && !u.isPrimitive() && !v.isPrimitive() && shared(u, v);
        }
        // Two classes meet only along one line of superclasses, and an enum's constants cannot add interfaces
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

    // What Control's parameter is bound to along the class's supertypes, given what its own parameters are bound to; null when no supertype leads to Control
    private static Class<?> find(Class<?> type, Map<TypeVariable<?>, Class<?>> arguments) {
        Type[] interfaces = read(type::getGenericInterfaces, () -> rebuiltInterfaces(type, arguments));
        Type superclass = read(type::getGenericSuperclass, () -> rebuiltSuperclass(type, arguments));
        Class<?> found = walk(interfaces, superclass, arguments);
        // The JVM never checks a signature against the class, so a stale one left by a transformer may miss Control where the erased supertypes cannot
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

    // Follows one supertype, passing on what it binds its own parameters to
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
        // A raw parent binds nothing of its own, so its answer is the one cached for it as a class
        return Control.class.isAssignableFrom(raw) ? declared(raw) : null;
    }

    // What a parameterized parent binds the parameters of its class and of each generic class around it to, as Outer<String>.Inner binds Outer's
    private static Map<TypeVariable<?>, Class<?>> bind(ParameterizedType parent, Map<TypeVariable<?>, Class<?>> arguments) {
        Map<TypeVariable<?>, Class<?>> passed = new HashMap<>();
        for (Type level = parent; level instanceof ParameterizedType parameterized; level = parameterized.getOwnerType()) {
            // A parent rebuilt over a class whose own signature is malformed has no parameters to bind, and was built with no arguments
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

    // The class an argument stands for, null where nothing is known, with a parameter nothing binds standing for its first bound
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
                // Met again while its own bound is erased, the parameter lies on a cycle only hand-made bytecode can hold, which says nothing
                return bound != ERASING ? bound : null;
            }
            Map<TypeVariable<?>, Class<?>> erasing = new HashMap<>(arguments);
            erasing.put(variable, ERASING);
            Type[] bounds = read(variable::getBounds, () -> null);
            return bounds != null ? erase(bounds[0], erasing) : null;
        }
        // A wildcard, which only bytecode javac did not write can put in a supertype's arguments, stands for its upper bound
        if (type instanceof WildcardType wildcard) {
            Type[] upper = read(wildcard::getUpperBounds, () -> null);
            return upper != null ? erase(upper[0], arguments) : null;
        }
        return null;
    }

    // The array class of the component, null past the 255 dimensions the JVM allows
    private static Class<?> arrayOf(Class<?> component) {
        return read(component::arrayType, () -> null);
    }

    // The class's interfaces rebuilt one by one from its class file, so an argument reflection cannot read costs that argument alone
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

    // The class's own signature from its class file; null without this very class's file or a signature in it that parses
    private static ClassSignature signature(Class<?> type) {
        String name = type.getName().replace('.', '/');
        byte[] bytes = classFile(type, name);
        if (bytes == null) {
            return null;
        }
        try {
            ClassModel model = ClassFile.of().parse(bytes);
            // A loader may hand out other bytes than it defined the class from, so only a file of this class counts
            if (!model.thisClass().asInternalName().equals(name)) {
                return null;
            }
            return model.findAttribute(Attributes.signature()).map(SignatureAttribute::asClassSignature).orElse(null);
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    // The class file as the class's loader hands it out, null without one or when it cannot be read
    private static byte[] classFile(Class<?> type, String name) {
        try (InputStream file = type.getResourceAsStream("/" + name + ".class")) {
            return file != null ? file.readAllBytes() : null;
        } catch (IOException unreadable) {
            return null;
        }
    }

    // The supertype among those a signature names that is this class, its arguments erased in the scope of the class reading it; the class itself when none is
    private static Type rebuild(Class<?> type, Class<?> raw, List<Signature.ClassTypeSig> named, Map<TypeVariable<?>, Class<?>> arguments) {
        for (Signature.ClassTypeSig signature : named) {
            if (raw != null && binaryName(signature).equals(raw.getName())) {
                Type owner = rebuild(type, read(raw::getDeclaringClass, () -> null), signature.outerType().map(List::of).orElse(List.of()), arguments);
                TypeVariable<?>[] parameters = read(raw::getTypeParameters, () -> new TypeVariable<?>[0]);
                List<Signature.TypeArg> given = signature.typeArgs();
                Type[] actual = new Type[parameters.length];
                // Arguments that do not fit the parameters, as an older shape of the class gives, bind none of them
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

    // The binary name of the class a signature names, with an inner class joined to the classes around it
    private static String binaryName(Signature.ClassTypeSig signature) {
        return signature.outerType().map(outer -> binaryName(outer) + "$" + signature.className()).orElse(signature.className().replace('/', '.'));
    }

    // What one argument of a signature erases to, a wildcard reaching up to its bound and one reaching down saying no more than Object
    private static Class<?> argument(Class<?> type, Signature.TypeArg given, Map<TypeVariable<?>, Class<?>> arguments) {
        if (given instanceof Signature.TypeArg.Bounded bounded && bounded.wildcardIndicator() != Signature.TypeArg.Bounded.WildcardIndicator.SUPER) {
            return resolve(type, bounded.boundType(), arguments);
        }
        return Object.class;
    }

    // The class a signature stands for in the class's scope, null where it names a class that will not load or a parameter nothing declares
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

    // The class of that name, null when it is missing or will not link
    private static Class<?> load(String name, ClassLoader loader) {
        try {
            return Class.forName(name, false, loader);
        } catch (ClassNotFoundException | LinkageError missing) {
            return null;
        }
    }

    // The parameter a class's signature names, looked up as reflection does in the class, the method or constructor around it and outwards from there
    private static TypeVariable<?> parameter(Class<?> type, String name) {
        // Hand-made enclosing method attributes can point two classes at each other, so each scope is searched once
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

    // The method, constructor or class around a class, or the class that declares a method or constructor
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

    // One lookup of what is around a class, null when its attributes name nothing real, which reflection reports as an InternalError
    private static <T> T around(Supplier<T> lookup) {
        try {
            return lookup.get();
        } catch (TypeNotPresentException | LinkageError | InternalError malformed) {
            return null;
        }
    }

    // Reads a signature, or falls back when it names a class that will not load, gives a class the wrong number of arguments, nests an array too deep or is malformed
    private static <T> T read(Supplier<T> generic, Supplier<T> fallback) {
        try {
            return generic.get();
        } catch (TypeNotPresentException | MalformedParameterizedTypeException | UnsupportedOperationException | LinkageError unreadable) {
            return fallback.get();
        }
    }

    // A supertype rebuilt from a class file, its arguments erased already and null where unknown
    private record Rebuilt(Class<?> getRawType, Type[] getActualTypeArguments, Type getOwnerType) implements ParameterizedType {
    }
}
