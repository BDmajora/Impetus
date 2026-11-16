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

// Loader of shader packs from folders and zip files.
public final class ShaderPackLoader {
    // Binary extensions.
    private static final Set<String> BINARY_EXTENSIONS = Set.of("png", "mcmeta", "dat", "bin", "raw");

    // Text extensions that are not GLSL.
    private static final Set<String> NON_GLSL_TEXT_EXTENSIONS =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList("json", "md")));

    // Largest text file.
    private static final long MAX_TEXT_FILE_BYTES = 8L * 1024 * 1024;

    // Largest binary file.
    private static final long MAX_BINARY_FILE_BYTES = 32L * 1024 * 1024;

    // Largest total pack size.
    private static final long MAX_PACK_BYTES = 512L * 1024 * 1024;

    // Prevents instantiation
    private ShaderPackLoader() {
    }

    // Loads a pack folder with default options.
    public static ShaderPack loadFromDirectory(Path packRoot) throws IOException {
        return loadFromDirectory(packRoot, Collections.emptyMap());
    }

    // Loads a pack folder with option changes.
    public static ShaderPack loadFromDirectory(Path packRoot, Map<String, String> changedConfigs) throws IOException {
        Path shadersDir = packRoot.resolve("shaders");
        if (!Files.isDirectory(shadersDir)) {
            throw new IOException("Shader pack has no shaders/ directory: " + packRoot);
        }

        Map<AbsolutePackPath, String> sources = new HashMap<>();
        Map<AbsolutePackPath, byte[]> binaries = new HashMap<>();
        long[] loaded = new long[1];
        Files.walkFileTree(shadersDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                loaded[0] += attrs.size();
                if (loaded[0] > MAX_PACK_BYTES) {
                    throw new IOException("Shader pack is larger than " + MAX_PACK_BYTES + " bytes: " + packRoot);
                }
                String relative = "/" + shadersDir.relativize(file).toString().replace('\\', '/');
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

    // Loads a pack zip with default options.
    public static ShaderPack loadFromZip(Path zipFile) throws IOException {
        return loadFromZip(zipFile, Collections.emptyMap());
    }

    // Loads a pack zip with option changes.
    public static ShaderPack loadFromZip(Path zipFile, Map<String, String> changedConfigs) throws IOException {
        Map<AbsolutePackPath, String> sources = new HashMap<>();
        Map<AbsolutePackPath, byte[]> binaries = new HashMap<>();
        long loaded = 0L;
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
                if (isBinaryPath(relative)) {
                    byte[] contents = readBinaryEntry(zip);
                    if (contents != null) {
                        loaded += contents.length;
                        binaries.put(AbsolutePackPath.fromAbsolutePath(relative), contents);
                    }
                } else if (isTextPath(relative)) {
                    String contents = readEntry(zip);
                    if (contents != null) {
                        loaded += contents.length();
                        sources.put(AbsolutePackPath.fromAbsolutePath(relative), contents);
                    }
                }
                if (loaded > MAX_PACK_BYTES) {
                    throw new IOException("Shader pack is larger than " + MAX_PACK_BYTES + " bytes: " + zipFile);
                }
                zip.closeEntry();
            }
        }

        if (sources.isEmpty()) {
            throw new IOException("Shader pack zip contained no shaders/ entries: " + zipFile);
        }
        return new ShaderPack(sources, changedConfigs, binaries);
    }

    // Returns whether a path is read as text.
    private static boolean isTextPath(String relative) {
        int dot = relative.lastIndexOf('.');
        if (dot >= 0 && NON_GLSL_TEXT_EXTENSIONS.contains(relative.substring(dot + 1).toLowerCase(Locale.ROOT))) {
            return false;
        }
        return !isBinaryPath(relative);
    }

    // Returns whether a path is read as bytes.
    private static boolean isBinaryPath(String relative) {
        int dot = relative.lastIndexOf('.');
        if (dot < 0) {
            return false;
        }
        return BINARY_EXTENSIONS.contains(relative.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    // Reads a zip entry as text within the size limit.
    private static String readEntry(ZipInputStream zip) throws IOException {
        StringBuilder sb = new StringBuilder();
        char[] buffer = new char[4096];
        long total = 0;
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

    // Reads a zip entry as bytes within the size limit.
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

    // Stream that ignores close.
    private static final class UncloseableStream extends InputStream {
        // Stream being read.
        private final InputStream delegate;

        // Creates an uncloseable stream from its delegate.
        UncloseableStream(InputStream delegate) {
            this.delegate = delegate;
        }

        // Reads one byte.
        @Override
        public int read() throws IOException {
            return this.delegate.read();
        }

        // Reads bytes into an array.
        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return this.delegate.read(b, off, len);
        }

        // Leaves the underlying stream open.
        @Override
        public void close() {
        }
    }
}
