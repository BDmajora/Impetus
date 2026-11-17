package com.bdmajora.coarctatio.launch.discovery;

import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.common.discovery.asm.ASMModParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.Type;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ModScanCacheTest {
    // Cache directory.
    @TempDir
    static Path dir;

    @BeforeAll
    static void pointAtTheTempGameDirectory() {
        Statics.set(Launch.class, "minecraftHome", dir.toFile());
    }

    @BeforeEach
    void forgetWhatOtherTestsCached() throws Exception {
        Mixins.set(ModScanCache.class, "loaded", null);
        Mixins.<Map<String, Map<String, byte[]>>>get(ModScanCache.class, "current").clear();
        // Clears the cache file.
        if (Files.isDirectory(cacheFile())) {
            try (java.util.stream.Stream<Path> children = Files.list(cacheFile())) {
                for (Path child : children.toList()) {
                    Files.delete(child);
                }
            }
        }
        Files.deleteIfExists(cacheFile());
    }

    // Forgets the loaded records.
    private static void forgetLoadedRecords() {
        Mixins.set(ModScanCache.class, "loaded", null);
        Mixins.<Map<String, Map<String, byte[]>>>get(ModScanCache.class, "current").clear();
    }

    private static Path cacheFile() {
        return dir.resolve("impetus-cache/mod-scan.bin");
    }

    @Test
    void aJarIsKeyedByWhereItIsAndWhenItChanged() throws Exception {
        File jar = Files.createFile(dir.resolve("example.jar")).toFile();
        String key = ModScanCache.keyFor(jar);
        assertTrue(key.startsWith(jar.getAbsolutePath() + '|'));
        assertEquals(key, ModScanCache.keyFor(jar));
        // Touches the jar.
        assertTrue(jar.setLastModified(jar.lastModified() + 10_000L));
        assertNotEquals(key, ModScanCache.keyFor(jar));
    }

    @Test
    void aScannedClassIsWrittenOutAndServedBackOnTheNextLaunch() throws Exception {
        Map<String, byte[]> record = ModScanCache.recordFor("jar|1|2");
        assertTrue(record.isEmpty());
        // The same jar asked for twice within a launch is the same record
        assertSame(record, ModScanCache.recordFor("jar|1|2"));

        ASMModParser parser = ParserCodecTest.parser();
        byte[] blob = ModScanCache.encode(parser);
        assertNotNull(blob);
        record.put("com/example/ExampleMod.class", blob);

        ModScanCache.save();
        Path file = cacheFile();
        for (int i = 0; i < 200 && !Files.isRegularFile(file); i++) {
            Thread.sleep(10L);
        }
        assertTrue(Files.isRegularFile(file));
        assertTrue(Files.size(file) > 0);

        // A fresh launch reads the file and hands back what was scanned last time
        forgetLoadedRecords();
        Map<String, byte[]> restored = ModScanCache.recordFor("jar|1|2");
        assertArrayEquals(blob, restored.get("com/example/ExampleMod.class"));
        ASMModParser served = ModScanCache.decode(restored.get("com/example/ExampleMod.class"));
        assertNotNull(served);
        assertEquals(Type.getType("Lcom/example/ExampleMod;"), com.bdmajora.testing.Mixins.get(served, "asmType"));

        // A jar that was not in the file gets an empty record to fill
        assertTrue(ModScanCache.recordFor("other|1|2").isEmpty());
        // Nothing to write means no file rewrite at all
        forgetLoadedRecords();
        ModScanCache.save();
        assertNotNull(Mixins.construct(ModScanCache.class));
    }

    @Test
    void aDamagedOrForeignCacheIsStartedOver() throws Exception {
        Files.createDirectories(cacheFile().getParent());
        Files.write(cacheFile(), new byte[] {1, 2, 3, 4});
        assertTrue(ModScanCache.recordFor("jar|1|2").isEmpty());

        // A blob that is not a scan result is refused rather than served
        assertNull(ModScanCache.decode(new byte[] {9, 9, 9}));
    }

    @Test
    void aCacheFromAnotherFormatIsIgnored() throws Exception {
        Files.createDirectories(cacheFile().getParent());
        try (java.io.DataOutputStream out = new java.io.DataOutputStream(
                new java.util.zip.GZIPOutputStream(Files.newOutputStream(cacheFile())))) {
            out.writeInt(99);
            out.writeUTF("some other Forge");
        }
        assertTrue(ModScanCache.recordFor("jar|1|2").isEmpty());
    }

    @Test
    void anUnwritableTargetIsOnlyLogged() throws Exception {
        Map<String, Map<String, byte[]>> snapshot = new HashMap<>();
        snapshot.put("jar|1|2", java.util.Collections.singletonMap("a", new byte[] {1}));
        // Checks a failed move.
        Files.createDirectories(cacheFile());
        Mixins.call(ModScanCache.class, "write", snapshot);
        Files.deleteIfExists(cacheFile().resolveSibling("mod-scan.bin.tmp"));
    }

    private static void writeCache(java.util.function.Consumer<java.io.DataOutputStream> body) throws Exception {
        Files.createDirectories(cacheFile().getParent());
        try (java.io.DataOutputStream out = new java.io.DataOutputStream(
                new java.util.zip.GZIPOutputStream(Files.newOutputStream(cacheFile())))) {
            out.writeInt(1);
            out.writeUTF(net.minecraftforge.common.ForgeVersion.getVersion());
            body.accept(out);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    @Test
    void aHostileLengthIsRefusedWithoutAllocating() throws Exception {
        writeCache(out -> {
            try {
                out.writeInt(1);
                out.writeUTF("jar|1|2");
                out.writeInt(1);
                out.writeUTF("com/x/Y.class");
                out.writeInt(Integer.MAX_VALUE);
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        });
        assertTrue(ModScanCache.recordFor("jar|1|2").isEmpty());
    }

    @Test
    void negativeCountsAreRefused() throws Exception {
        writeCache(out -> {
            try {
                out.writeInt(-5);
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        });
        assertTrue(ModScanCache.recordFor("a").isEmpty());
        forgetLoadedRecords();
        writeCache(out -> {
            try {
                out.writeInt(1);
                out.writeUTF("a");
                out.writeInt(-1);
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        });
        assertTrue(ModScanCache.recordFor("a").isEmpty());
    }

    @Test
    void emptyBlobsAndNullBlobsDoNotDecode() {
        assertNull(ModScanCache.decode(new byte[0]));
        assertNull(ModScanCache.decode(null));
    }
}
