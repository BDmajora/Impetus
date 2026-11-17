package com.bdmajora.impetus.engine.impl.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

// Finds the mixin classes in a folder of compiled classes.
public class MixinClassValidator {
    // Logger of the scanner.
    private static final Logger LOGGER = LogManager.getLogger(MixinClassValidator.class);
    // Descriptor of the mixin annotation.
    private static final String MIXIN_DESC = "Lorg/spongepowered/asm/mixin/Mixin;";

    // Returns the names of the mixin classes below a folder and returns the classes found so far after a read failure.
    public static List<String> scanMixinFolder(Path packageRoot) {
        java.util.Objects.requireNonNull(packageRoot, "packageRoot");
        List<String> mixins = new ArrayList<>();
        try (Stream<Path> stream = Files.find(packageRoot, Integer.MAX_VALUE,
                (path, attrs) -> attrs.isRegularFile() && path.getFileName().toString().endsWith(".class"))) {
            stream.map(Path::toAbsolutePath)
                    .filter(MixinClassValidator::isMixinClass)
                    .map(path -> classifyMixin(packageRoot, path))
                    .forEach(mixins::add);
        } catch (IOException e) {
            LOGGER.error("Could not scan {} for mixin classes", packageRoot, e);
        }
        return mixins;
    }

    // Returns the class name of a file below the root.
    private static String classifyMixin(Path baseFolder, Path path) {
        try {
            String className = baseFolder.relativize(path).toString().replace('/', '.').replace('\\', '.');
            return className.substring(0, className.length() - 6);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Error relativizing " + path + " to " + baseFolder, e);
        }
    }

    // Returns whether a class file has the mixin annotation and false for a file that cannot be read.
    public static boolean isMixinClass(Path classPath) {
        byte[] bytecode;

        try {
            bytecode = Files.readAllBytes(classPath);
        } catch (IOException e) {
            LOGGER.error("Could not read class file {}", classPath, e);
            return false;
        }

        try {
            return isMixinClass(fromBytecode(bytecode));
        } catch (RuntimeException unreadable) {
            return false;
        }
    }

    // Parses the annotations of a class and rejects null.
    public static ClassNode fromBytecode(byte[] bytecode) {
        java.util.Objects.requireNonNull(bytecode, "bytecode");
        ClassNode node = new ClassNode();
        ClassReader reader = new ClassReader(bytecode);
        reader.accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        return node;
    }

    // Returns whether the class has the mixin annotation.
    public static boolean isMixinClass(ClassNode node) {
        if(node.invisibleAnnotations == null) {
            return false;
        }

        return node.invisibleAnnotations.stream().anyMatch(annotation -> annotation.desc.equals(MIXIN_DESC));
    }
}
