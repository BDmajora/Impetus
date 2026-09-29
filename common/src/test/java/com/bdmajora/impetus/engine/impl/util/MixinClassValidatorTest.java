package com.bdmajora.impetus.engine.impl.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MixinClassValidatorTest {
    private static byte[] classBytes(String name, boolean mixin) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
        if (mixin) {
            writer.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false).visitEnd();
        }
        writer.visitEnd();
        return writer.toByteArray();
    }

    @Test
    void scanFindsOnlyAnnotatedClasses(@TempDir Path root) throws IOException {
        Path pkg = Files.createDirectories(root.resolve("a/b"));
        Files.write(pkg.resolve("Yes.class"), classBytes("a/b/Yes", true));
        Files.write(pkg.resolve("No.class"), classBytes("a/b/No", false));
        Files.write(pkg.resolve("Notes.txt"), new byte[]{1});
        List<String> found = MixinClassValidator.scanMixinFolder(root);
        assertEquals(List.of("a.b.Yes"), found);
    }

    @Test
    void scanOfMissingFolderIsEmpty(@TempDir Path root) {
        assertTrue(MixinClassValidator.scanMixinFolder(root.resolve("missing")).isEmpty());
    }

    @Test
    void unreadableClassIsNotAMixin(@TempDir Path root) {
        assertFalse(MixinClassValidator.isMixinClass(root.resolve("nope.class")));
        ClassNode bare = new ClassNode();
        assertFalse(MixinClassValidator.isMixinClass(bare));
        assertTrue(MixinClassValidator.isMixinClass(MixinClassValidator.fromBytecode(classBytes("x/Y", true))));
        new MixinClassValidator();
    }
}
