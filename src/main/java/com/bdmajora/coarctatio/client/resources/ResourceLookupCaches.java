package com.bdmajora.coarctatio.client.resources;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.Set;

// Shared state of resource lookup caches.
public final class ResourceLookupCaches {
    // Number of reloads seen.
    private static final java.util.concurrent.atomic.AtomicInteger generation = new java.util.concurrent.atomic.AtomicInteger();

    // Prevents instantiation
    private ResourceLookupCaches() {
    }

    // Returns the number of reloads seen.
    public static int generation() {
        return generation.get();
    }

    // Marks every cached lookup as outdated.
    public static void onReload() {
        generation.incrementAndGet();
    }

    // Returns the relative paths of every file under the folder.
    public static Set<String> indexFolder(File root) {
        Set<String> paths = new HashSet<>();
        Path base = root.toPath();
        if (!Files.isDirectory(base)) {
            return paths;
        }
        try {
            Files.walkFileTree(base, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (attrs.isRegularFile()) {
                        paths.add(base.relativize(file).toString().replace(File.separatorChar, '/'));
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException e) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
        }
        return paths;
    }
}
