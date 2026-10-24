package com.bdmajora.impetus.engine.impl.asm;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.util.CheckClassAdapter;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

// Defines a final class in this package that implements the delegate interfaces and forwards each public instance method to a wrapped delegate
public final class ProxyClassGenerator<DELEGATE, INTERFACE> {
    // Whether the emitted class is checked by the ASM verifier before it is defined
    private static final boolean VERIFY = false;

    // Lookup that defines the proxies in this package and checks access to the types they name
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    // Internal name of this package followed by a slash
    private static final String PACKAGE_PREFIX = ProxyClassGenerator.class.getPackageName().replace('.', '/') + '/';

    // Name of the proxy field that holds the delegate
    private static final String DELEGATE_FIELD = "view";

    // Constructor type with the delegate and result erased to Object
    private static final MethodType ERASED_CONSTRUCTOR = MethodType.methodType(Object.class, Object.class);

    // Proxy constructor adapted to take and return Object
    private final MethodHandle constructor;

    // Checks the name and interface and delegate class and then defines the proxy class
    public ProxyClassGenerator(Class<DELEGATE> realClass, String proxyClassName, Class<INTERFACE> primaryInterface) {
        Objects.requireNonNull(realClass, "realClass");
        Objects.requireNonNull(proxyClassName, "proxyClassName");
        Objects.requireNonNull(primaryInterface, "primaryInterface");
        if (!isIdentifier(proxyClassName)) {
            throw new IllegalArgumentException("Proxy class name is not one Java identifier: " + proxyClassName);
        }
        if (!primaryInterface.isInterface() || !primaryInterface.isAssignableFrom(realClass)) {
            throw new IllegalArgumentException(realClass.getName() + " does not implement the interface " + primaryInterface.getName());
        }
        // Rejects a hidden delegate class
        if (realClass.isHidden()) {
            throw new IllegalArgumentException("A hidden class cannot be a proxy delegate: " + realClass.getName());
        }
        // Rejects a delegate class the proxy cannot name
        if (!nameable(realClass)) {
            throw new IllegalArgumentException("Proxy delegate cannot be named from " + LOOKUP.lookupClass().getPackageName() + ": " + realClass.getName());
        }
        if (!implementable(primaryInterface)) {
            throw new IllegalArgumentException("Proxy cannot implement the interface " + primaryInterface.getName() + ": it is sealed or cannot be named from " + LOOKUP.lookupClass().getPackageName());
        }
        String proxyName = PACKAGE_PREFIX + proxyClassName;
        byte[] bytecode = createWrapperClassBytecode(realClass, proxyName);
        try {
            Class<?> proxyClass = LOOKUP.defineClass(bytecode);
            this.constructor = LOOKUP.findConstructor(proxyClass, MethodType.methodType(void.class, realClass)).asType(ERASED_CONSTRUCTOR);
        } catch (ReflectiveOperationException | LinkageError e) {
            // Wraps any failure to define the proxy class
            throw new RuntimeException("Error defining proxy " + proxyName + " for " + realClass.getName(), e);
        }
    }

    // Wraps the delegate in a new proxy instance
    @SuppressWarnings("unchecked")
    public INTERFACE generateWrapper(DELEGATE delegate) {
        // Rejects a null delegate
        Objects.requireNonNull(delegate, "delegate");
        try {
            return (INTERFACE) (Object) this.constructor.invokeExact((Object) delegate);
        } catch (Throwable e) {
            // Wraps any failure from the proxy constructor
            throw new RuntimeException("Exception creating wrapper", e);
        }
    }

    // Emits a final class with one final delegate field and a forwarder for each distinct public instance method not declared by Object
    private static byte[] createWrapperClassBytecode(Class<?> delegate, String proxyName) {
        String delegateName = Type.getInternalName(delegate);
        String delegateDesc = Type.getDescriptor(delegate);
        ClassWriter classWriter = new ClassWriter(0);
        ClassVisitor classVisitor = VERIFY ? new CheckClassAdapter(classWriter) : classWriter;

        classVisitor.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER, proxyName, null, "java/lang/Object", implementedInterfaces(delegate));
        classVisitor.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, DELEGATE_FIELD, delegateDesc, null, null).visitEnd();

        MethodVisitor methodVisitor = classVisitor.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(" + delegateDesc + ")V", null, null);
        methodVisitor.visitCode();
        methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
        methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
        methodVisitor.visitVarInsn(Opcodes.ALOAD, 1);
        methodVisitor.visitFieldInsn(Opcodes.PUTFIELD, proxyName, DELEGATE_FIELD, delegateDesc);
        methodVisitor.visitInsn(Opcodes.RETURN);
        methodVisitor.visitMaxs(2, 2);
        methodVisitor.visitEnd();

        // Calls the delegate with an interface or virtual call matching its kind
        boolean delegateIsInterface = delegate.isInterface();
        int invoke = delegateIsInterface ? Opcodes.INVOKEINTERFACE : Opcodes.INVOKEVIRTUAL;
        // Tracks emitted signatures to skip duplicates
        Set<String> emitted = new HashSet<>();
        for (Method method : delegate.getMethods()) {
            if (Modifier.isStatic(method.getModifiers()) || method.getDeclaringClass() == Object.class) {
                continue;
            }
            String name = method.getName();
            String descriptor = Type.getMethodDescriptor(method);
            if (!emitted.add(name.concat(descriptor))) {
                continue;
            }
            methodVisitor = classVisitor.visitMethod(Opcodes.ACC_PUBLIC, name, descriptor, null, null);
            methodVisitor.visitCode();
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
            methodVisitor.visitFieldInsn(Opcodes.GETFIELD, proxyName, DELEGATE_FIELD, delegateDesc);
            // Loads each argument into the next local slot with two slots for a long or double
            int slots = 1;
            for (Type argument : Type.getArgumentTypes(descriptor)) {
                methodVisitor.visitVarInsn(argument.getOpcode(Opcodes.ILOAD), slots);
                slots += argument.getSize();
            }
            methodVisitor.visitMethodInsn(invoke, delegateName, name, descriptor, delegateIsInterface);
            Type returnType = Type.getReturnType(descriptor);
            methodVisitor.visitInsn(returnType.getOpcode(Opcodes.IRETURN));
            // Sets the maximum stack to the larger of the argument slots and the return size
            methodVisitor.visitMaxs(Math.max(slots, returnType.getSize()), slots);
            methodVisitor.visitEnd();
        }

        classVisitor.visitEnd();
        return classWriter.toByteArray();
    }

    // Returns the internal names of every interface of the delegate that the proxy may implement with each listed once
    private static String[] implementedInterfaces(Class<?> delegate) {
        ArrayDeque<Class<?>> pending = new ArrayDeque<>();
        if (delegate.isInterface()) {
            pending.add(delegate);
        } else {
            for (Class<?> type = delegate; type != null; type = type.getSuperclass()) {
                Collections.addAll(pending, type.getInterfaces());
            }
        }
        Set<Class<?>> seen = new HashSet<>();
        List<String> names = new ArrayList<>();
        while (!pending.isEmpty()) {
            Class<?> type = pending.poll();
            if (seen.add(type)) {
                // Lists only interfaces the proxy may implement and still walks their superinterfaces
                if (implementable(type)) {
                    names.add(Type.getInternalName(type));
                }
                Collections.addAll(pending, type.getInterfaces());
            }
        }
        return names.toArray(String[]::new);
    }

    // Returns true when the interface is not sealed and the proxy can name it
    private static boolean implementable(Class<?> type) {
        return !type.isSealed() && nameable(type);
    }

    // Returns true when the type is accessible and the loader of this package resolves its name to the same class
    private static boolean nameable(Class<?> type) {
        try {
            LOOKUP.accessClass(type);
            return Class.forName(type.getName(), false, LOOKUP.lookupClass().getClassLoader()) == type;
        } catch (IllegalAccessException | ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    // Returns true when the name is a single Java identifier
    private static boolean isIdentifier(String name) {
        int index = 0;
        while (index < name.length()) {
            int codePoint = name.codePointAt(index);
            if (index == 0 ? !Character.isJavaIdentifierStart(codePoint) : !Character.isJavaIdentifierPart(codePoint)) {
                return false;
            }
            index += Character.charCount(codePoint);
        }
        return index > 0;
    }
}