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

// Forge's mod discovery inflates and ASM-scans every class in every jar on every launch to find annotations, tens of seconds on a large pack (VintageFix's jar discoverer cache); the scan result per class is kept here between launches, keyed by the jar's path, size and modification time, so an unchanged jar is served from the cache. Jars absent from a launch drop out of the file, so it tracks the current mod set
public final class ModScanCache {
    private static final int FORMAT = 1;
    private static final File FILE = new File(Launch.minecraftHome, "impetus-cache/mod-scan.bin");

    // jar key -> (class entry name -> encoded parser)
    private static Map<String, Map<String, byte[]>> loaded;
    private static final Map<String, Map<String, byte[]>> current = new HashMap<>();
    private static int served;
    private static int scanned;

    private ModScanCache() {
    }

    public static String keyFor(File jar) {
        return jar.getAbsolutePath() + '|' + jar.length() + '|' + jar.lastModified();
    }

    // The record for this jar: the previous run's if the jar is unchanged, else a fresh one that fills as the scan proceeds
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

    public static ASMModParser decode(byte[] blob) {
        if (!ParserCodec.canDecode()) {
            return null;
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(blob))) {
            ASMModParser parser = ParserCodec.decode(in);
            served++;
            return parser;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    public static byte[] encode(ASMModParser parser) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(128);
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                ParserCodec.encode(parser, out);
            }
            scanned++;
            return bytes.toByteArray();
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    // Called once discovery is over; written off-thread since the file can run to megabytes and nothing reads it again this launch
    public static synchronized void save() {
        if (current.isEmpty()) {
            return;
        }
        Coarctatio.LOGGER.info("Mod scan cache served {} classes and scanned {}", served, scanned);
        Map<String, Map<String, byte[]>> snapshot = new HashMap<>(current);
        loaded = null;
        Thread writer = new Thread(() -> write(snapshot), "Impetus Mod Scan Cache Writer");
        writer.setDaemon(true);
        writer.start();
    }

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
            for (int i = 0; i < jars; i++) {
                String key = in.readUTF();
                int entries = in.readInt();
                Map<String, byte[]> record = new HashMap<>(Math.max(16, entries * 2));
                for (int j = 0; j < entries; j++) {
                    String name = in.readUTF();
                    byte[] blob = new byte[in.readInt()];
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
            Coarctatio.LOGGER.warn("Could not write the mod scan cache: {}", e.toString());
        }
    }
}
