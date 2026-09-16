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

// The reload generation every resource-lookup cache keys itself on, plus the folder walk used to index directory packs; a reload bumps the generation and each cache rebuilds on its next miss, so an edited development pack is seen after F3+T exactly as before
public final class ResourceLookupCaches {
    private static volatile int generation;

    private ResourceLookupCaches() {
    }

    public static int generation() {
        return generation;
    }

    public static void onReload() {
        generation++;
    }

    // Every regular file under `root` as a forward-slash relative path, exact case; a folder pack's whole tree is read once instead of a stat per lookup
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
            // A half-indexed folder only means some lookups fall back to "missing", which is what an unreadable file would answer anyway
        }
        return paths;
    }
}
