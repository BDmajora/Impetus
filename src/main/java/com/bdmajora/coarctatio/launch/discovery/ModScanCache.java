package com.bdmajora.coarctatio.launch.discovery;

import com.bdmajora.coarctatio.Coarctatio;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.common.ForgeVersion;
import net.minecraftforge.fml.common.discovery.asm.ASMModParser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

// Disk cache of the mod class scan results.
public final class ModScanCache {
    // Version of the file layout.
    private static final int FORMAT = 1;
    // Cache file.
    private static final File FILE = new File(Launch.minecraftHome, "impetus-cache/mod-scan.bin");

    // Records read from disk or null before the first read.
    private static Map<String, Map<String, byte[]>> loaded;
    // Records used in this run.
    private static final Map<String, Map<String, byte[]>> current = new HashMap<>();
    // Count of results served from the cache.
    private static final java.util.concurrent.atomic.AtomicInteger served = new java.util.concurrent.atomic.AtomicInteger();
    // Count of results scanned fresh.
    private static final java.util.concurrent.atomic.AtomicInteger scanned = new java.util.concurrent.atomic.AtomicInteger();
    // Largest accepted size of one stored result.
    private static final int MAX_BLOB_BYTES = 1 << 24;

    // Prevents instantiation
    private ModScanCache() {
    }

    // Returns the key of a jar from its path and size and time.
    public static String keyFor(File jar) {
        return jar.getAbsolutePath() + '|' + jar.length() + '|' + jar.lastModified();
    }

    // Returns the record of the jar and creates it when absent.
    public static synchronized Map<String, byte[]> recordFor(String jarKey) {
        if (loaded == null) {
            loaded = read();
        }
        Map<String, byte[]> record = current.get(jarKey);
        if (record == null) {
            record = loaded.get(jarKey);
            if (record == null) {
                record = new HashMap<>();
            }
            current.put(jarKey, record);
        }
        return record;
    }

    // Returns the parser stored in the blob or null when it cannot be read.
    public static ASMModParser decode(byte[] blob) {
        if (!ParserCodec.canDecode()) {
            return null;
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(blob))) {
            ASMModParser parser = ParserCodec.decode(in);
            served.incrementAndGet();
            return parser;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    // Returns the stored form of the parser or null when it cannot be encoded.
    public static byte[] encode(ASMModParser parser) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(128);
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                ParserCodec.encode(parser, out);
            }
            scanned.incrementAndGet();
            return bytes.toByteArray();
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    // Writes the used records to disk in the background.
    public static synchronized void save() {
        if (current.isEmpty()) {
            return;
        }
        Coarctatio.LOGGER.info("Mod scan cache served {} classes and scanned {}", served.get(), scanned.get());
        Map<String, Map<String, byte[]>> snapshot = new HashMap<>(current);
        loaded = null;
        Thread.ofVirtual().name("Impetus Mod Scan Cache Writer").start(() -> write(snapshot));
    }

    // Returns the records read from disk and an empty map when the file is unusable.
    private static Map<String, Map<String, byte[]>> read() {
        Map<String, Map<String, byte[]>> map = new HashMap<>();
        if (!FILE.isFile()) {
            return map;
        }
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(Files.newInputStream(FILE.toPath()), 65536))) {
            if (in.readInt() != FORMAT || !ForgeVersion.getVersion().equals(in.readUTF())) {
                Coarctatio.LOGGER.info("Mod scan cache is from another format or Forge version, starting over");
                return map;
            }
            int jars = in.readInt();
            if (jars < 0) {
                throw new IOException("Negative jar count");
            }
            for (int i = 0; i < jars; i++) {
                String key = in.readUTF();
                int entries = in.readInt();
                if (entries < 0) {
                    throw new IOException("Negative entry count");
                }
                Map<String, byte[]> record = new HashMap<>(Math.max(16, Math.min(entries, 1 << 16) * 2));
                for (int j = 0; j < entries; j++) {
                    String name = in.readUTF();
                    int length = in.readInt();
                    if (length < 0 || length > MAX_BLOB_BYTES) {
                        throw new IOException("Implausible blob length " + length);
                    }
                    byte[] blob = new byte[length];
                    in.readFully(blob);
                    record.put(name, blob);
                }
                map.put(key, record);
            }
        } catch (IOException | RuntimeException e) {
            Coarctatio.LOGGER.warn("Could not read the mod scan cache, starting over: {}", e.toString());
            map.clear();
        }
        return map;
    }

    // Writes the records to disk and replaces the old file.
    private static void write(Map<String, Map<String, byte[]>> snapshot) {
        Path target = FILE.toPath();
        Path temp = target.resolveSibling(FILE.getName() + ".tmp");
        try {
            Files.createDirectories(target.getParent());
            try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(temp), 65536))) {
                out.writeInt(FORMAT);
                out.writeUTF(ForgeVersion.getVersion());
                out.writeInt(snapshot.size());
                for (Map.Entry<String, Map<String, byte[]>> jar : snapshot.entrySet()) {
                    out.writeUTF(jar.getKey());
                    Map<String, byte[]> record = jar.getValue();
                    out.writeInt(record.size());
                    for (Map.Entry<String, byte[]> entry : record.entrySet()) {
                        out.writeUTF(entry.getKey());
                        out.writeInt(entry.getValue().length);
                        out.write(entry.getValue());
                    }
                }
            }
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
            }
            Coarctatio.LOGGER.warn("Could not write the mod scan cache: {}", e.toString());
        }
    }
}
