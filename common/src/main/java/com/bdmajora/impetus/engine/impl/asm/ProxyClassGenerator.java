package com.bdmajora.impetus.engine.impl.asm;

import org.objectweb.asm.*;
import org.objectweb.asm.util.CheckClassAdapter;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

public class ProxyClassGenerator<DELEGATE, INTERFACE> {
    private static Definer DEFINER;

    // Picks Lookup.defineClass, or the unlocked ClassLoader.defineClass when the engine runs as the downgraded Java 8 jar; reflective so neither path depends on a JVM Downgrader stub
    private static synchronized Definer getDefiner() {
        if (DEFINER != null) {
            return DEFINER;
        }
        try {
            DEFINER = modernDefiner();
        } catch (Exception x) {
            DEFINER = legacyDefiner();
        }
        return DEFINER;
    }

    // The path every Java 25 runtime takes; these public methods just don't exist on Java 8
    private static Definer modernDefiner() throws Exception {
        Method makePrivateLookup = MethodHandles.class.getMethod(
                "privateLookupIn", Class.class, MethodHandles.Lookup.class
        );
        Object privateLookup = makePrivateLookup.invoke(null, ProxyClassGenerator.class, MethodHandles.lookup());
        Method defineClass = MethodHandles.Lookup.class.getMethod("defineClass", byte[].class);
        return (bytes, name) -> (Class<?>) defineClass.invoke(privateLookup, (Object) bytes);
    }

    // Legacy-jar path; fails outright if the protected defineClass is missing too
    private static Definer legacyDefiner() {
        try {
            Method defineClass = ClassLoader.class.getDeclaredMethod(
                    "defineClass", String.class, byte[].class, int.class, int.class
            );
            defineClass.setAccessible(true);
            ClassLoader loader = ProxyClassGenerator.class.getClassLoader();
            return (bytes, name) -> (Class<?>) defineClass.invoke(loader, name, bytes, 0, bytes.length);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private final Class<?> delegateClass;
    private final Class<?> proxyClass;
    private final MethodHandle proxyClassConstructor;
    private final String proxyClassName;
    private final String proxyClassNameDesc;

    public ProxyClassGenerator(Class<DELEGATE> realClass, String proxyClassName, Class<INTERFACE> primaryInterface) {
        this.delegateClass = realClass;
        this.proxyClassName = "com/bdmajora/impetus/engine/impl/asm/" + proxyClassName;
        this.proxyClassNameDesc = "L" + this.proxyClassName + ";";
        this.proxyClass = createWrapperClass();
        try {
            this.proxyClassConstructor = MethodHandles.publicLookup().findConstructor(this.proxyClass, MethodType.methodType(void.class, realClass)).asType(MethodType.methodType(primaryInterface, realClass));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    // Instantiates the generated wrapper around a delegate
    @SuppressWarnings("unchecked")
    public INTERFACE generateWrapper(DELEGATE delegate) {
        try {
            return (INTERFACE)this.proxyClassConstructor.invoke(delegate);
        } catch(Throwable e) {
            throw new RuntimeException("Exception creating wrapper", e);
        }
    }

    private static final boolean VERIFY = false;

    // Emits a class implementing the interface whose every method forwards to the delegate field
    private byte[] createWrapperClassBytecode() {
        String worldSliceDesc = Type.getDescriptor(this.delegateClass);
        ClassWriter classWriter = new ClassWriter(0);
        ClassVisitor classVisitor = VERIFY ? new CheckClassAdapter(classWriter) : classWriter;

        FieldVisitor fieldVisitor;
        MethodVisitor methodVisitor;

        Class<?>[] interfaces = this.delegateClass.getInterfaces();

        int version = Opcodes.V1_8;

        classVisitor.visit(version, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, this.proxyClassName, null, "java/lang/Object",
                Arrays.stream(interfaces).map(Type::getInternalName).toArray(String[]::new));

        fieldVisitor = classVisitor.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "view", worldSliceDesc, null, null);
        fieldVisitor.visitEnd();

        classVisitor.visitSource(null, null);

        // Generate constructor first
        methodVisitor = classVisitor.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(" + worldSliceDesc + ")V", null, null);
        methodVisitor.visitCode();
        Label label0 = new Label();
        methodVisitor.visitLabel(label0);
        methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
        methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
        methodVisitor.visitVarInsn(Opcodes.ALOAD, 1);
        methodVisitor.visitFieldInsn(Opcodes.PUTFIELD, this.proxyClassName, "view", worldSliceDesc);
        methodVisitor.visitInsn(Opcodes.RETURN);
        Label label3 = new Label();
        methodVisitor.visitLabel(label3);
        methodVisitor.visitLocalVariable("this", this.proxyClassNameDesc, null, label0, label3, 0);
        methodVisitor.visitLocalVariable("view", worldSliceDesc, null, label0, label3, 1);
        methodVisitor.visitMaxs(2, 2);
        methodVisitor.visitEnd();

        // Now generate delegates for each method on WorldSlice's interfaces
        for(Method method : this.delegateClass.getMethods()) {
            // Only delegate for public, non-static methods implemented by WorldSlice or an interface
            if(Modifier.isPublic(method.getModifiers()) && !Modifier.isStatic(method.getModifiers()) && !method.getDeclaringClass().isAssignableFrom(Object.class)) {
                int maxStack = 0;
                String methodDescription = Type.getMethodDescriptor(method);
                methodVisitor = classVisitor.visitMethod(Opcodes.ACC_PUBLIC, method.getName(), methodDescription, null, null);
                methodVisitor.visitCode();
                // push WorldSlice
                methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
                methodVisitor.visitFieldInsn(Opcodes.GETFIELD, this.proxyClassName, "view", worldSliceDesc);
                maxStack++;
                // push each argument
                int maxLocals = 1;
                for (Type t : Type.getArgumentTypes(method)) {
                    int size = t.getSize();
                    methodVisitor.visitVarInsn(t.getOpcode(Opcodes.ILOAD), maxLocals);
                    maxLocals += size;
                    maxStack += size;
                }
                // invoke the method on WorldSlice
                boolean itf = method.getDeclaringClass().isInterface();
                methodVisitor.visitMethodInsn(itf ? Opcodes.INVOKEINTERFACE : Opcodes.INVOKEVIRTUAL, Type.getInternalName(method.getDeclaringClass()), method.getName(), methodDescription, itf);
                Type returnType = Type.getReturnType(methodDescription);
                methodVisitor.visitInsn(returnType.getOpcode(Opcodes.IRETURN));
                methodVisitor.visitMaxs(maxStack, maxLocals);
                methodVisitor.visitEnd();
            }
        }

        classVisitor.visitEnd();

        return classWriter.toByteArray();
    }

    // Defines the generated bytecode into the interface's loader, cached per interface
    private Class<?> createWrapperClass() {
        byte[] bytes = createWrapperClassBytecode();
        try {
            return getDefiner().define(bytes, this.proxyClassName.replace('/', '.'));
        } catch(Exception e) {
            throw new RuntimeException("Error defining WorldSlice wrapper", e);
        }
    }

    private interface Definer {
        Class<?> define(byte[] bytes, String name) throws Exception;
    }
}
