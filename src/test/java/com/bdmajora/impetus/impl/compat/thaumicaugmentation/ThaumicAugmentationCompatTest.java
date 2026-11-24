package com.bdmajora.impetus.impl.compat.thaumicaugmentation;

import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.*;

class ThaumicAugmentationCompatTest {
    private static final String FILE = ThaumicAugmentationCompat.HOOKS.replace('.', '/') + ".class";

    // Shows a TAHooksClient class file and defines the given bytes for it.
    private static final class TaLoader extends ClassLoader {
        private final byte[] hooks;

        TaLoader(byte[] hooks) {
            super(ThaumicAugmentationCompatTest.class.getClassLoader());
            this.hooks = hooks;
        }

        @Override
        public URL getResource(String name) {
            return name.equals(FILE) ? ThaumicAugmentationCompatTest.class.getResource("ThaumicAugmentationCompatTest.class") : super.getResource(name);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (!name.equals(ThaumicAugmentationCompat.HOOKS) || this.hooks == null) {
                throw new ClassNotFoundException(name);
            }
            return defineClass(name, this.hooks, 0, this.hooks.length);
        }
    }

    // TAHooksClient with public static int last, count and onRenderEntities(int) that records its pass, or throws when asked.
    private static byte[] hooks(boolean throwing, int access) {
        return hooks(throwing ? "java/lang/IllegalStateException" : null, access);
    }

    // Same hook, throwing the given throwable type when one is named.
    private static byte[] hooks(String throwable, int access) {
        boolean throwing = throwable != null;
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        String name = ThaumicAugmentationCompat.HOOKS.replace('.', '/');
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, name, null, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "last", "I", null, null).visitEnd();
        writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "count", "I", null, null).visitEnd();
        MethodVisitor method = writer.visitMethod(access, ThaumicAugmentationCompat.METHOD, "(I)V", null, null);
        method.visitCode();
        if (throwing) {
            method.visitTypeInsn(Opcodes.NEW, throwable);
            method.visitInsn(Opcodes.DUP);
            method.visitMethodInsn(Opcodes.INVOKESPECIAL, throwable, "<init>", "()V", false);
            method.visitInsn(Opcodes.ATHROW);
        } else {
            // The pass sits in slot 0 of a static method and slot 1 of an instance one
            method.visitVarInsn(Opcodes.ILOAD, (access & Opcodes.ACC_STATIC) != 0 ? 0 : 1);
            method.visitFieldInsn(Opcodes.PUTSTATIC, name, "last", "I");
            method.visitFieldInsn(Opcodes.GETSTATIC, name, "count", "I");
            method.visitInsn(Opcodes.ICONST_1);
            method.visitInsn(Opcodes.IADD);
            method.visitFieldInsn(Opcodes.PUTSTATIC, name, "count", "I");
            method.visitInsn(Opcodes.RETURN);
        }
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static int read(ClassLoader loader, String field) throws Exception {
        return Class.forName(ThaumicAugmentationCompat.HOOKS, false, loader).getField(field).getInt(null);
    }

    @Test
    void withoutThaumicAugmentationTheHookIsANoOp() {
        // The test class path has no TA, so the class initialiser bound nothing
        assertFalse(ThaumicAugmentationCompat.isBound());
        ThaumicAugmentationCompat.onRenderEntities(0);
        assertNull(ThaumicAugmentationCompat.bind(null));
        assertNull(ThaumicAugmentationCompat.bind(ThaumicAugmentationCompatTest.class.getClassLoader()));
        assertNotNull(Mixins.construct(ThaumicAugmentationCompat.class));
    }

    @Test
    void everyPassReachesTasHookWithItsNumber() throws Exception {
        TaLoader loader = new TaLoader(hooks(false, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC));
        MethodHandle handle = ThaumicAugmentationCompat.bind(loader);
        assertNotNull(handle);
        MethodHandle previous = Statics.get(ThaumicAugmentationCompat.class, "hook");
        Statics.set(ThaumicAugmentationCompat.class, "hook", handle);
        try {
            ThaumicAugmentationCompat.onRenderEntities(0);
            assertEquals(0, read(loader, "last"));
            // TA itself ignores pass 1, but the choice is its own
            ThaumicAugmentationCompat.onRenderEntities(1);
            assertEquals(1, read(loader, "last"));
            assertEquals(2, read(loader, "count"));
            assertTrue(ThaumicAugmentationCompat.isBound());
        } finally {
            Statics.set(ThaumicAugmentationCompat.class, "hook", previous);
        }
    }

    @Test
    void aFailingHookIsTurnedOffAfterOneFrame() {
        MethodHandle handle = ThaumicAugmentationCompat.bind(new TaLoader(hooks(true, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)));
        assertNotNull(handle);
        MethodHandle previous = Statics.get(ThaumicAugmentationCompat.class, "hook");
        Statics.set(ThaumicAugmentationCompat.class, "hook", handle);
        try {
            assertDoesNotThrow(() -> ThaumicAugmentationCompat.onRenderEntities(0));
            assertFalse(ThaumicAugmentationCompat.isBound());
            assertDoesNotThrow(() -> ThaumicAugmentationCompat.onRenderEntities(0));
        } finally {
            Statics.set(ThaumicAugmentationCompat.class, "hook", previous);
        }
    }

    @Test
    void anUnusableHookIsLoggedNotBound() {
        // Visible file, class that cannot be defined
        assertNull(ThaumicAugmentationCompat.bind(new TaLoader(null)));
        assertNull(ThaumicAugmentationCompat.bind(new TaLoader(new byte[] {1, 2, 3})));
        // Instance method, private method: neither is a public static hook
        assertNull(ThaumicAugmentationCompat.bind(new TaLoader(hooks(false, Opcodes.ACC_PUBLIC))));
        assertNull(ThaumicAugmentationCompat.bind(new TaLoader(hooks(false, Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC))));
    }

    @Test
    void errorsFromTaAreContainedToo() {
        // A TA build missing its own classes throws NoClassDefFoundError out of the hook; a frame must not die of it
        for (String error : new String[] {"java/lang/NoClassDefFoundError", "java/lang/StackOverflowError", "java/lang/AssertionError"}) {
            MethodHandle handle = ThaumicAugmentationCompat.bind(new TaLoader(hooks(error, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)));
            assertNotNull(handle, error);
            MethodHandle previous = Statics.get(ThaumicAugmentationCompat.class, "hook");
            Statics.set(ThaumicAugmentationCompat.class, "hook", handle);
            try {
                assertDoesNotThrow(() -> ThaumicAugmentationCompat.onRenderEntities(0), error);
                assertFalse(ThaumicAugmentationCompat.isBound(), error);
            } finally {
                Statics.set(ThaumicAugmentationCompat.class, "hook", previous);
            }
        }
    }

    @Test
    void aDisabledHookStaysOffOverManyFrames() throws Exception {
        TaLoader loader = new TaLoader(hooks(false, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC));
        MethodHandle previous = Statics.get(ThaumicAugmentationCompat.class, "hook");
        Statics.set(ThaumicAugmentationCompat.class, "hook", ThaumicAugmentationCompat.bind(loader));
        try {
            for (int frame = 0; frame < 1000; frame++) {
                ThaumicAugmentationCompat.onRenderEntities(frame & 1);
            }
            // Each frame reached TA exactly once, the drain cadence TA expects
            assertEquals(1000, read(loader, "count"));
            Statics.set(ThaumicAugmentationCompat.class, "hook", null);
            for (int frame = 0; frame < 1000; frame++) {
                ThaumicAugmentationCompat.onRenderEntities(0);
            }
            assertEquals(1000, read(loader, "count"));
        } finally {
            Statics.set(ThaumicAugmentationCompat.class, "hook", previous);
        }
    }

    @Test
    void bindingLinksTaWithoutRunningItsStaticSetup() throws Exception {
        // TAHooksClient's static setup touches the client. Here it throws: binding must still succeed, so it never ran it,
        // and the first real call then contains the ExceptionInInitializerError and turns the hook off
        net.bytebuddy.jar.asm.ClassWriter writer = new net.bytebuddy.jar.asm.ClassWriter(net.bytebuddy.jar.asm.ClassWriter.COMPUTE_FRAMES | net.bytebuddy.jar.asm.ClassWriter.COMPUTE_MAXS);
        String name = ThaumicAugmentationCompat.HOOKS.replace('.', '/');
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, name, null, "java/lang/Object", null);
        MethodVisitor init = writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        init.visitCode();
        init.visitTypeInsn(Opcodes.NEW, "java/lang/IllegalStateException");
        init.visitInsn(Opcodes.DUP);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/IllegalStateException", "<init>", "()V", false);
        init.visitInsn(Opcodes.ATHROW);
        init.visitMaxs(0, 0);
        init.visitEnd();
        MethodVisitor hook = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, ThaumicAugmentationCompat.METHOD, "(I)V", null, null);
        hook.visitCode();
        hook.visitInsn(Opcodes.RETURN);
        hook.visitMaxs(0, 0);
        hook.visitEnd();
        writer.visitEnd();
        MethodHandle handle = ThaumicAugmentationCompat.bind(new TaLoader(writer.toByteArray()));
        assertNotNull(handle);
        MethodHandle previous = Statics.get(ThaumicAugmentationCompat.class, "hook");
        Statics.set(ThaumicAugmentationCompat.class, "hook", handle);
        try {
            assertDoesNotThrow(() -> ThaumicAugmentationCompat.onRenderEntities(0));
            assertFalse(ThaumicAugmentationCompat.isBound());
        } finally {
            Statics.set(ThaumicAugmentationCompat.class, "hook", previous);
        }
    }

    private static final java.util.List<Integer> RECORDED = new java.util.ArrayList<>();

    // Stands in for TA's hook and records each pass it gets.
    public static void record(int pass) {
        RECORDED.add(pass);
    }

    @Test
    void passesReachTaVerbatimIncludingExtremes() throws Exception {
        RECORDED.clear();
        MethodHandle previous = Statics.get(ThaumicAugmentationCompat.class, "hook");
        Statics.set(ThaumicAugmentationCompat.class, "hook", java.lang.invoke.MethodHandles.lookup().findStatic(
                ThaumicAugmentationCompatTest.class, "record", java.lang.invoke.MethodType.methodType(void.class, int.class)));
        try {
            int[] passes = {0, 1, -1, 2, Integer.MIN_VALUE, Integer.MAX_VALUE};
            for (int pass : passes) {
                ThaumicAugmentationCompat.onRenderEntities(pass);
            }
            assertEquals(java.util.List.of(0, 1, -1, 2, Integer.MIN_VALUE, Integer.MAX_VALUE), RECORDED);
        } finally {
            Statics.set(ThaumicAugmentationCompat.class, "hook", previous);
        }
    }

    @Test
    void aWrongTypedHandleInTheFieldIsContained() throws Exception {
        // invokeExact refuses a handle of another type with WrongMethodTypeException; that must turn the hook off, not escape
        MethodHandle previous = Statics.get(ThaumicAugmentationCompat.class, "hook");
        Statics.set(ThaumicAugmentationCompat.class, "hook", java.lang.invoke.MethodHandles.constant(int.class, 4));
        try {
            assertDoesNotThrow(() -> ThaumicAugmentationCompat.onRenderEntities(0));
            assertFalse(ThaumicAugmentationCompat.isBound());
        } finally {
            Statics.set(ThaumicAugmentationCompat.class, "hook", previous);
        }
    }

    @Test
    void aHookThatFailsOnlySometimesIsStillOffAfterTheFirstFailure() throws Exception {
        // TA failing on one frame (a TE removed mid-frame, say) still turns the hook off for the session; nothing retries
        RECORDED.clear();
        MethodHandle previous = Statics.get(ThaumicAugmentationCompat.class, "hook");
        MethodHandle recorder = java.lang.invoke.MethodHandles.lookup().findStatic(ThaumicAugmentationCompatTest.class, "record",
                java.lang.invoke.MethodType.methodType(void.class, int.class));
        MethodHandle failing = java.lang.invoke.MethodHandles.lookup().findStatic(ThaumicAugmentationCompatTest.class, "failOnTwo",
                java.lang.invoke.MethodType.methodType(void.class, int.class));
        Statics.set(ThaumicAugmentationCompat.class, "hook", recorder);
        try {
            ThaumicAugmentationCompat.onRenderEntities(1);
            Statics.set(ThaumicAugmentationCompat.class, "hook", failing);
            ThaumicAugmentationCompat.onRenderEntities(2);
            assertFalse(ThaumicAugmentationCompat.isBound());
            ThaumicAugmentationCompat.onRenderEntities(3);
            assertEquals(java.util.List.of(1), RECORDED);
        } finally {
            Statics.set(ThaumicAugmentationCompat.class, "hook", previous);
        }
    }

    public static void failOnTwo(int pass) {
        if (pass == 2) {
            throw new IllegalStateException("frame " + pass);
        }
        RECORDED.add(pass);
    }
}
