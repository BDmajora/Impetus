package com.bdmajora.impetus.core;

import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.modifier.FieldManifestation;
import net.bytebuddy.description.modifier.Ownership;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.minecraft.launchwrapper.Launch;
import org.junit.jupiter.api.Test;

import java.net.URL;

import static org.junit.jupiter.api.Assertions.*;

class VintageFixCompatTest {
    // Path of the VintageFix coremod class file.
    private static final String CORE_FILE = VintageFixCompat.VINTAGEFIX_CORE.replace('.', '/') + ".class";

    // Loader that shows a VintageFixCore class file and defines the given bytes for it, or fails to when they are null.
    private static final class VintageFixLoader extends ClassLoader {
        private final byte[] core;

        VintageFixLoader(byte[] core) {
            super(VintageFixCompatTest.class.getClassLoader());
            this.core = core;
        }

        @Override
        public URL getResource(String name) {
            return name.equals(CORE_FILE) ? VintageFixCompatTest.class.getResource("VintageFixCompatTest.class") : super.getResource(name);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (!name.equals(VintageFixCompat.VINTAGEFIX_CORE) || this.core == null) {
                throw new ClassNotFoundException(name);
            }
            return defineClass(name, this.core, 0, this.core.length);
        }
    }

    private static ByteBuddy buddy() {
        return new ByteBuddy();
    }

    // A core with the public static boolean flag VintageFix declares.
    private static byte[] core() {
        return buddy().subclass(Object.class).name(VintageFixCompat.VINTAGEFIX_CORE)
                .defineField("VINTAGIUM", boolean.class, Visibility.PUBLIC, Ownership.STATIC).make().getBytes();
    }

    // A core whose static initialiser throws, so touching the flag fails to initialise the class.
    private static byte[] brokenInitialiser() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        String name = VintageFixCompat.VINTAGEFIX_CORE.replace('.', '/');
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, name, null, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "VINTAGIUM", "Z", null, null).visitEnd();
        MethodVisitor init = writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        init.visitCode();
        init.visitTypeInsn(Opcodes.NEW, "java/lang/IllegalStateException");
        init.visitInsn(Opcodes.DUP);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/IllegalStateException", "<init>", "()V", false);
        init.visitInsn(Opcodes.ATHROW);
        init.visitMaxs(0, 0);
        init.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static boolean flag(ClassLoader loader) throws Exception {
        return Class.forName(VintageFixCompat.VINTAGEFIX_CORE, false, loader).getField("VINTAGIUM").getBoolean(null);
    }

    @Test
    void withoutVintageFixThereIsNothingToTell() {
        assertFalse(VintageFixCompat.claimChunkRenderer(null));
        assertFalse(VintageFixCompat.claimChunkRenderer(VintageFixCompatTest.class.getClassLoader()));
        assertNotNull(Mixins.construct(VintageFixCompat.class));
    }

    @Test
    void thePublicEntryUsesTheGameLoaderAndFallsBackToItsOwn() {
        ClassLoader previous = Launch.classLoader;
        try {
            // A game loader that holds VintageFix is the one asked
            VintageFixLoader game = new VintageFixLoader(core());
            Statics.set(Launch.class, "classLoader", null);
            VintageFixCompat.claimChunkRenderer();
            // Nothing to tell through the fallback loader, which has no VintageFix
            assertNull(Launch.classLoader);
            Statics.set(Launch.class, "classLoader", wrap(game));
            VintageFixCompat.claimChunkRenderer();
            assertTrue(flag(game));
        } catch (Exception e) {
            fail(e);
        } finally {
            Statics.set(Launch.class, "classLoader", previous);
        }
    }

    // Wraps a plain loader in the LaunchClassLoader type the field holds, delegating everything to it.
    private static net.minecraft.launchwrapper.LaunchClassLoader wrap(ClassLoader inner) {
        return new net.minecraft.launchwrapper.LaunchClassLoader(new URL[0]) {
            @Override
            public URL getResource(String name) {
                return inner.getResource(name);
            }

            @Override
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                return inner.loadClass(name);
            }

            @Override
            public Class<?> findClass(String name) throws ClassNotFoundException {
                return inner.loadClass(name);
            }
        };
    }

    @Test
    void impetusSetsTheFlagVintageFixSetsForCeleritas() throws Exception {
        VintageFixLoader loader = new VintageFixLoader(core());
        assertFalse(flag(loader));
        assertTrue(VintageFixCompat.claimChunkRenderer(loader));
        assertTrue(flag(loader));
        // A second claim finds it already set and changes nothing
        assertTrue(VintageFixCompat.claimChunkRenderer(loader));
        assertTrue(flag(loader));
    }

    @Test
    void aFlagVintageFixAlreadySetIsLeftAsItIs() throws Exception {
        VintageFixLoader loader = new VintageFixLoader(core());
        Class.forName(VintageFixCompat.VINTAGEFIX_CORE, false, loader).getField("VINTAGIUM").setBoolean(null, true);
        assertTrue(VintageFixCompat.claimChunkRenderer(loader));
        assertTrue(flag(loader));
    }

    @Test
    void aVintageFixWithoutAUsableFlagIsLoggedNotFatal() {
        // No flag at all
        assertFalse(VintageFixCompat.claimChunkRenderer(new VintageFixLoader(buddy().subclass(Object.class)
                .name(VintageFixCompat.VINTAGEFIX_CORE).make().getBytes())));
        // A private flag is not reachable through getField
        assertFalse(VintageFixCompat.claimChunkRenderer(new VintageFixLoader(buddy().subclass(Object.class).name(VintageFixCompat.VINTAGEFIX_CORE)
                .defineField("VINTAGIUM", boolean.class, Visibility.PRIVATE, Ownership.STATIC).make().getBytes())));
        // An instance field cannot be read without an instance
        assertFalse(VintageFixCompat.claimChunkRenderer(new VintageFixLoader(buddy().subclass(Object.class).name(VintageFixCompat.VINTAGEFIX_CORE)
                .defineField("VINTAGIUM", boolean.class, Visibility.PUBLIC).make().getBytes())));
        // A final flag refuses the write
        assertFalse(VintageFixCompat.claimChunkRenderer(new VintageFixLoader(buddy().subclass(Object.class).name(VintageFixCompat.VINTAGEFIX_CORE)
                .defineField("VINTAGIUM", boolean.class, Visibility.PUBLIC, Ownership.STATIC, FieldManifestation.FINAL).make().getBytes())));
        // A flag of another type cannot be read as a boolean
        assertFalse(VintageFixCompat.claimChunkRenderer(new VintageFixLoader(buddy().subclass(Object.class).name(VintageFixCompat.VINTAGEFIX_CORE)
                .defineField("VINTAGIUM", int.class, Visibility.PUBLIC, Ownership.STATIC).make().getBytes())));
    }

    @Test
    void aCoreThatFailsToLoadOrInitialiseIsLoggedNotFatal() {
        // The class file is visible but the class cannot be defined
        assertFalse(VintageFixCompat.claimChunkRenderer(new VintageFixLoader(null)));
        // The class loads but its static initialiser throws when the flag is touched
        VintageFixLoader broken = new VintageFixLoader(brokenInitialiser());
        assertFalse(VintageFixCompat.claimChunkRenderer(broken));
        // The class is now unusable for good and a second claim still answers false
        assertFalse(VintageFixCompat.claimChunkRenderer(broken));
        // Garbage bytes fail to define
        assertFalse(VintageFixCompat.claimChunkRenderer(new VintageFixLoader(new byte[] {1, 2, 3})));
    }
}
