package com.bdmajora.impetus.umbra.shaderpack;

import com.bdmajora.impetus.umbra.shaderpack.include.AbsolutePackPath;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

// Reads a pack folder or zip into the map ShaderPack consumes with java.nio and java.util.zip only; text to one map, binary assets to another, keys relative to shaders/
public final class ShaderPackLoader {
    // Extensions read as raw bytes for the custom-texture directives and their .mcmeta sidecars; .dat is included since Photon ships 3D lookup textures that way
    private static final Set<String> BINARY_EXTENSIONS = Set.of("png", "mcmeta", "dat", "bin", "raw");

    // Text files that are metadata for OTHER mods, kept out of the source map since ShaderPackOptions scans EVERY entry for options (Iris only walks its IncludeGraph); Voxy's voxy.json opens with `#define OVERWORLD` and would register dimension macros as user toggles
    private static final Set<String> NON_GLSL_TEXT_EXTENSIONS =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList("json", "md")));

    // Guard against slurping something enormous into a String — a malformed pack, or a user's unrelated file
    private static final long MAX_TEXT_FILE_BYTES = 8L * 1024 * 1024;

    // Same guard for binary assets, set well above the few MB the largest known pack LUTs occupy
    private static final long MAX_BINARY_FILE_BYTES = 32L * 1024 * 1024;

    private ShaderPackLoader() {
    }

    // Loads a pack laid out as a folder with a shaders/ subdirectory
    public static ShaderPack loadFromDirectory(Path packRoot) throws IOException {
        return loadFromDirectory(packRoot, Collections.emptyMap());
    }

    // Walks an unzipped pack
    public static ShaderPack loadFromDirectory(Path packRoot, Map<String, String> changedConfigs) throws IOException {
        Path shadersDir = packRoot.resolve("shaders");
        if (!Files.isDirectory(shadersDir)) {
            throw new IOException("Shader pack has no shaders/ directory: " + packRoot);
        }

        Map<AbsolutePackPath, String> sources = new HashMap<>();
        Map<AbsolutePackPath, byte[]> binaries = new HashMap<>();
        Files.walkFileTree(shadersDir, new SimpleFileVisitor<Path>() {
            // Reads text files as sources and known binaries as raw bytes
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String relative = "/" + shadersDir.relativize(file).toString().replace('\\', '/');
                // Binary first: isTextPath is now "not binary", so testing it first would starve this branch.
                if (isBinaryPath(relative) && attrs.size() <= MAX_BINARY_FILE_BYTES) {
                    binaries.put(AbsolutePackPath.fromAbsolutePath(relative), Files.readAllBytes(file));
                } else if (isTextPath(relative) && attrs.size() <= MAX_TEXT_FILE_BYTES) {
                    sources.put(AbsolutePackPath.fromAbsolutePath(relative),
                            new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
                }
                return FileVisitResult.CONTINUE;
            }
        });

        return new ShaderPack(sources, changedConfigs, binaries);
    }

    // Loads a pack distributed as a .zip with a top-level shaders/ directory; keys come out relative to it, matching the folder loader
    public static ShaderPack loadFromZip(Path zipFile) throws IOException {
        return loadFromZip(zipFile, Collections.emptyMap());
    }

    // Streams a zipped pack, tolerating a single top-level folder
    public static ShaderPack loadFromZip(Path zipFile, Map<String, String> changedConfigs) throws IOException {
        Map<AbsolutePackPath, String> sources = new HashMap<>();
        Map<AbsolutePackPath, byte[]> binaries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName().replace('\\', '/');
                int idx = name.indexOf("shaders/");
                if (idx < 0) {
                    continue;
                }
                String relative = "/" + name.substring(idx + "shaders/".length());
                if (relative.equals("/")) {
                    continue;
                }
                // Binary first: isTextPath is now "not binary", so testing it first would starve this branch.
                if (isBinaryPath(relative)) {
                    byte[] contents = readBinaryEntry(zip);
                    if (contents != null) {
                        binaries.put(AbsolutePackPath.fromAbsolutePath(relative), contents);
                    }
                } else if (isTextPath(relative)) {
                    String contents = readEntry(zip);
                    if (contents != null) {
                        sources.put(AbsolutePackPath.fromAbsolutePath(relative), contents);
                    }
                }
                zip.closeEntry();
            }
        }

        if (sources.isEmpty()) {
            throw new IOException("Shader pack zip contained no shaders/ entries: " + zipFile);
        }
        return new ShaderPack(sources, changedConfigs, binaries);
    }

    // Anything not a recognised binary asset is read as text, a blacklist NOT a whitelist: Iris and OptiFine read #include paths straight off disk, but this port pre-scans into a source map, so a whitelist silently dropped miniature-shader's /shader.h and RedHat's lib/defines/*.h and every constant became an undefined-variable error
    private static boolean isTextPath(String relative) {
        int dot = relative.lastIndexOf('.');
        if (dot >= 0 && NON_GLSL_TEXT_EXTENSIONS.contains(relative.substring(dot + 1).toLowerCase(Locale.ROOT))) {
            return false;
        }
        return !isBinaryPath(relative);
    }

    // Textures and other assets that must not be decoded as text
    private static boolean isBinaryPath(String relative) {
        int dot = relative.lastIndexOf('.');
        if (dot < 0) {
            return false;
        }
        return BINARY_EXTENSIONS.contains(relative.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    // One entry as UTF-8
    private static String readEntry(ZipInputStream zip) throws IOException {
        StringBuilder sb = new StringBuilder();
        char[] buffer = new char[4096];
        long total = 0;
        // Do not close the reader; it would close the shared ZipInputStream.
        BufferedReader reader = new BufferedReader(new InputStreamReader(new UncloseableStream(zip), StandardCharsets.UTF_8));
        int read;
        while ((read = reader.read(buffer)) != -1) {
            total += read;
            if (total > MAX_TEXT_FILE_BYTES) {
                return null;
            }
            sb.append(buffer, 0, read);
        }
        return sb.toString();
    }

    // One entry as bytes
    private static byte[] readBinaryEntry(ZipInputStream zip) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = zip.read(buffer)) != -1) {
            total += read;
            if (total > MAX_BINARY_FILE_BYTES) {
                return null;
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    // Wraps a stream so close() does nothing; the reading helpers close what they are handed, but a ZipInputStream is reused across every entry
    private static final class UncloseableStream extends InputStream {
        private final InputStream delegate;

        UncloseableStream(InputStream delegate) {
            this.delegate = delegate;
        }

        // Delegates without closing the underlying zip
        @Override
        public int read() throws IOException {
            return this.delegate.read();
        }

        // Delegates without closing the underlying zip
        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return this.delegate.read(b, off, len);
        }

        // No-op, so readers can be closed without ending the zip stream
        @Override
        public void close() {
            // no-op
        }
    }
}
