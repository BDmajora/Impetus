package com.bdmajora.coarctatio;

import com.bdmajora.coarctatio.dedup.ModelCaches;
import com.bdmajora.coarctatio.dedup.ResourceLocationCaches;
import com.bdmajora.coarctatio.dedup.StringPool;
import com.bdmajora.coarctatio.util.ClassDefineTool;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import com.google.common.collect.ImmutableMap;
import net.minecraft.launchwrapper.Launch;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CoarctatioCoreTest {
    @TempDir
    Path dir;

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
    }

    @BeforeEach
    void freshConfig() {
        Statics.set(Launch.class, "minecraftHome", dir.toFile());
        Mixins.set(CoarctatioConfig.class, "instance", null);
    }

    @Test
    void theConfigStartsFromDefaultsAndWritesEverySwitchBack() throws Exception {
        CoarctatioConfig config = CoarctatioConfig.get();
        // Cached, so every reader shares one instance
        assertSame(config, CoarctatioConfig.get());
        assertTrue(config.deduplicateResourceLocations);
        assertFalse(config.dynamicModels);
        assertEquals(12, config.nbtArrayMapThreshold);
        assertArrayEquals(new String[] {"jds.bibliocraft"}, config.blockStateBlacklist);

        // A fresh file lists every switch, so a user who never opened it still discovers them
        Path file = dir.resolve("config/impetus-coarctatio.cfg");
        assertTrue(Files.isRegularFile(file));
        String written = Files.readString(file);
        assertTrue(written.contains("poolSizeLimit=262144"));
        assertTrue(written.contains("dynamicModelsEagerNamespaces=thebetweenlands,dynamictrees"));

        // A value set in the file is read back verbatim
        Files.writeString(file, written.replace("stripChunkNbt=true", "stripChunkNbt=false"));
        Mixins.set(CoarctatioConfig.class, "instance", null);
        assertFalse(CoarctatioConfig.get().stripChunkNbt);
        CoarctatioConfig.get().save();
        assertTrue(Files.readString(file).contains("stripChunkNbt=false"));
    }

    @Test
    void theReloadHooksReopenTheBakeScopedPools() {
        Coarctatio.onResourceReloadStart();
        int[] quad = {1, 2, 3};
        assertSame(quad, ModelCaches.QUADS.deduplicate(quad));
        Coarctatio.onResourceReloadFinish();
        // Closed pools hand their argument straight back
        assertSame(quad, ModelCaches.QUADS.deduplicate(quad));

        // Leaving a world drops the pools that grow with play
        StringPool.NBT_KEYS.deduplicate("Damage");
        ResourceLocationCaches.PATHS.deduplicate("blocks/stone");
        Coarctatio.onWorldLeave();
        assertEquals(0, StringPool.NBT_KEYS.size());
        assertEquals(0, ResourceLocationCaches.PATHS.size());
        assertNotNull(Mixins.construct(Coarctatio.class));
    }

    @Test
    void theStatisticsCoverEveryPoolAndTheOptionalFeatures() {
        CoarctatioConfig.get().optimizeBlockStates = false;
        CoarctatioConfig.get().dynamicModels = false;
        List<String> plain = Coarctatio.statistics();
        assertEquals("Coarctatio memory statistics", plain.get(0));
        assertTrue(plain.stream().anyMatch(line -> line.contains("NBT keys")));
        assertTrue(plain.stream().noneMatch(line -> line.contains("Block states")));

        // With the state and model features on, their own counters are appended
        CoarctatioConfig.get().optimizeBlockStates = true;
        CoarctatioConfig.get().dynamicModels = true;
        List<String> full = Coarctatio.statistics();
        assertTrue(full.stream().anyMatch(line -> line.contains("Block states")));
        assertTrue(full.stream().anyMatch(line -> line.contains("Property maps")));
        assertTrue(full.size() >= plain.size() + 2);

        assertTrue(Coarctatio.debugOverlayLine().startsWith("Coarctatio: ~"));
    }

    @Test
    void theReportSeparatesMeasuredBytesFromEstimatedOnes() {
        MemoryReport.recordSpriteBytes(4L * 1024 * 1024);
        MemoryReport.recordClassLoaderBytes(512L * 1024);
        MemoryReport.recordPackedStates(1000);
        MemoryReport.recordRemapperEntries(3);

        List<String> lines = MemoryReport.lines();
        assertEquals("Coarctatio memory saved", lines.get(0));
        assertTrue(lines.stream().anyMatch(line -> line.contains("Texture pixel data") && line.contains("measured")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("Block state tables") && line.contains("estimated")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("TOTAL")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("Heap now")));
        assertTrue(MemoryReport.totalBytes() > 0);
        assertTrue(MemoryReport.summary().endsWith("MB"));

        // Megabytes above a megabyte, kilobytes below, so a small saving does not read as nothing
        assertEquals("1.0 MB", MemoryReport.mib(1024L * 1024L));
        assertEquals("512 KB", MemoryReport.mib(512L * 1024L));
        assertNotNull(Mixins.construct(MemoryReport.class));
    }

    @Test
    void definingIntoAnotherPackageReusesWhatIsAlreadyThere() {
        // The property map ships with us and is already loaded, so the injection finds it rather than defining it twice
        Class<?> defined = ClassDefineTool.defineClass(ImmutableMap.class, "com.google.common.collect.CoarctatioPropertyMap");
        assertNotNull(defined);
        assertSame(defined, ClassDefineTool.defineClass(ImmutableMap.class, "com.google.common.collect.CoarctatioPropertyMap"));
        // A class we do not ship has no bytecode to read, which is a refusal rather than a crash
        assertNull(ClassDefineTool.defineClass(ImmutableMap.class, "com.google.common.collect.NotOurs"));
        assertNotNull(Mixins.construct(ClassDefineTool.class));
        // Bytes that are not a class are refused by both tiers, and the bootstrap loader is never touched
        assertNull(Mixins.call(ClassDefineTool.class, "defineWithLookup", ImmutableMap.class, new byte[] {1, 2, 3}));
        assertNull(Mixins.call(ClassDefineTool.class, "defineWithClassLoader", ImmutableMap.class, "com.google.common.collect.Junk", new byte[] {1, 2, 3}));
        assertNull(Mixins.call(ClassDefineTool.class, "defineWithClassLoader", String.class, "java.lang.Junk", new byte[] {1, 2, 3}));
    }
}
