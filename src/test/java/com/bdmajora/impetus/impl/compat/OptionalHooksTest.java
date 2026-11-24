package com.bdmajora.impetus.impl.compat;

import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.net.URL;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

// The binder every optional mod hook goes through.
class OptionalHooksTest {
    private static final String OWNER = "some.mod.Hooks";
    private static final String FILE = OWNER.replace('.', '/') + ".class";
    private static final MethodType INT_TO_VOID = MethodType.methodType(void.class, int.class);

    // Shows the owner's class file, counts loads, and defines the bytes when asked.
    private static final class HookLoader extends ClassLoader {
        private final byte[] bytes;
        final AtomicInteger loads = new AtomicInteger();

        HookLoader(byte[] bytes) {
            super(OptionalHooksTest.class.getClassLoader());
            this.bytes = bytes;
        }

        @Override
        public URL getResource(String name) {
            return name.equals(FILE) ? OptionalHooksTest.class.getResource("OptionalHooksTest.class") : super.getResource(name);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (!name.equals(OWNER) || this.bytes == null) {
                throw new ClassNotFoundException(name);
            }
            this.loads.incrementAndGet();
            return defineClass(name, this.bytes, 0, this.bytes.length);
        }
    }

    // A class with one method of the given access and descriptor that returns its default value.
    private static byte[] owner(int classAccess, int methodAccess, String method, String descriptor) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V17, classAccess | Opcodes.ACC_SUPER, OWNER.replace('.', '/'), null, "java/lang/Object", null);
        MethodVisitor mv = writer.visitMethod(methodAccess, method, descriptor, null, null);
        if ((methodAccess & Opcodes.ACC_ABSTRACT) == 0) {
            mv.visitCode();
            switch (descriptor.charAt(descriptor.length() - 1)) {
                case 'V' -> mv.visitInsn(Opcodes.RETURN);
                case 'F' -> {
                    mv.visitInsn(Opcodes.FCONST_1);
                    mv.visitInsn(Opcodes.FRETURN);
                }
                default -> {
                    mv.visitInsn(Opcodes.ICONST_0);
                    mv.visitInsn(Opcodes.IRETURN);
                }
            }
            mv.visitMaxs(0, 0);
        }
        mv.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] publicStatic(String method, String descriptor) {
        return owner(Opcodes.ACC_PUBLIC, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, method, descriptor);
    }

    @Test
    void aPublicStaticHookBindsAndRuns() throws Throwable {
        MethodHandle handle = OptionalHooks.bind(new HookLoader(publicStatic("go", "(I)V")), OWNER, "go", INT_TO_VOID);
        assertNotNull(handle);
        assertEquals(INT_TO_VOID, handle.type());
        handle.invokeExact(3);
        MethodHandle gamma = OptionalHooks.bind(new HookLoader(publicStatic("gamma", "()F")), OWNER, "gamma", MethodType.methodType(float.class));
        assertEquals(1.0F, (float) gamma.invokeExact());
    }

    @Test
    void nullArgumentsBindNothing() {
        HookLoader loader = new HookLoader(publicStatic("go", "(I)V"));
        assertNull(OptionalHooks.bind(null, OWNER, "go", INT_TO_VOID));
        assertNull(OptionalHooks.bind(loader, null, "go", INT_TO_VOID));
        assertNull(OptionalHooks.bind(loader, OWNER, null, INT_TO_VOID));
        assertNull(OptionalHooks.bind(loader, OWNER, "go", null));
        // None of them reached the loader
        assertEquals(0, loader.loads.get());
    }

    @Test
    void anAbsentModIsNeverLoaded() {
        HookLoader loader = new HookLoader(publicStatic("go", "(I)V"));
        // The owner's file is the only one shown; another name must not be tried as a class
        assertNull(OptionalHooks.bind(loader, "other.mod.Hooks", "go", INT_TO_VOID));
        assertEquals(0, loader.loads.get());
    }

    @Test
    void theSignatureMustMatchExactly() {
        HookLoader loader = new HookLoader(publicStatic("go", "(I)V"));
        // Wrong name, wrong parameters, wrong return: no widening or boxing is allowed
        assertNull(OptionalHooks.bind(loader, OWNER, "goes", INT_TO_VOID));
        assertNull(OptionalHooks.bind(loader, OWNER, "go", MethodType.methodType(void.class)));
        assertNull(OptionalHooks.bind(loader, OWNER, "go", MethodType.methodType(void.class, long.class)));
        assertNull(OptionalHooks.bind(loader, OWNER, "go", MethodType.methodType(void.class, Integer.class)));
        assertNull(OptionalHooks.bind(loader, OWNER, "go", MethodType.methodType(int.class, int.class)));
        // The class was defined once and reused for every attempt
        assertEquals(1, loader.loads.get());
    }

    @Test
    void onlyPublicStaticMethodsOfPublicClassesBind() {
        // Package private class, protected, package private, private and instance methods are all out of the public lookup's reach
        assertNull(OptionalHooks.bind(new HookLoader(owner(0, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "go", "(I)V")), OWNER, "go", INT_TO_VOID));
        assertNull(OptionalHooks.bind(new HookLoader(owner(Opcodes.ACC_PUBLIC, Opcodes.ACC_PROTECTED | Opcodes.ACC_STATIC, "go", "(I)V")), OWNER, "go", INT_TO_VOID));
        assertNull(OptionalHooks.bind(new HookLoader(owner(Opcodes.ACC_PUBLIC, Opcodes.ACC_STATIC, "go", "(I)V")), OWNER, "go", INT_TO_VOID));
        assertNull(OptionalHooks.bind(new HookLoader(owner(Opcodes.ACC_PUBLIC, Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "go", "(I)V")), OWNER, "go", INT_TO_VOID));
        assertNull(OptionalHooks.bind(new HookLoader(owner(Opcodes.ACC_PUBLIC, Opcodes.ACC_PUBLIC, "go", "(I)V")), OWNER, "go", INT_TO_VOID));
    }

    @Test
    void aBrokenClassFileIsLoggedNotThrown() {
        // Visible but undefinable, garbage, and a class whose super type is missing
        assertNull(OptionalHooks.bind(new HookLoader(null), OWNER, "go", INT_TO_VOID));
        assertNull(OptionalHooks.bind(new HookLoader(new byte[] {(byte) 0xCA, (byte) 0xFE}), OWNER, "go", INT_TO_VOID));
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, OWNER.replace('.', '/'), null, "missing/Base", null);
        writer.visitEnd();
        assertNull(OptionalHooks.bind(new HookLoader(writer.toByteArray()), OWNER, "go", INT_TO_VOID));
    }
}
