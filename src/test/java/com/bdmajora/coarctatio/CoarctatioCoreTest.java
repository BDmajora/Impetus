package com.bdmajora.coarctatio;

import com.bdmajora.coarctatio.dedup.ModelCaches;
import com.bdmajora.coarctatio.dedup.ResourceLocationCaches;
import com.bdmajora.coarctatio.dedup.StringPool;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
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
        // Checks the config is cached.
        assertSame(config, CoarctatioConfig.get());
        assertTrue(config.deduplicateResourceLocations);
        assertEquals(12, config.nbtArrayMapThreshold);

        // Checks a fresh file lists every switch.
        Path file = dir.resolve("config/impetus-coarctatio.cfg");
        assertTrue(Files.isRegularFile(file));
        String written = Files.readString(file);
        assertTrue(written.contains("poolSizeLimit=262144"));
        assertTrue(written.contains("canonicalizeModelParts=true"));

        // A value set in the file is read back verbatim
        Files.writeString(file, written.replace("stripChunkNbt=true", "stripChunkNbt=false")
                .replace("canonicalizeModelParts=true", "canonicalizeModelParts=false"));
        Mixins.set(CoarctatioConfig.class, "instance", null);
        assertFalse(CoarctatioConfig.get().stripChunkNbt);
        assertFalse(CoarctatioConfig.get().canonicalizeModelParts);
        CoarctatioConfig.get().save();
        assertTrue(Files.readString(file).contains("stripChunkNbt=false"));
        assertTrue(Files.readString(file).contains("canonicalizeModelParts=false"));
    }

    @Test
    void theReloadHooksReopenTheBakeScopedPools() {
        Coarctatio.onResourceReloadStart();
        float[] uvs = {0, 0, 16, 16};
        assertSame(uvs, ModelCaches.FACE_UVS.deduplicate(uvs));
        assertSame(uvs, ModelCaches.FACE_UVS.deduplicate(new float[] {0, 0, 16, 16}));
        Coarctatio.onResourceReloadFinish();
        // Closed pools hand their argument straight back
        float[] other = {1, 1, 2, 2};
        assertSame(other, ModelCaches.FACE_UVS.deduplicate(other));

        // Leaving a world drops the pools that grow with play
        StringPool.NBT_KEYS.deduplicate("Damage");
        ResourceLocationCaches.PATHS.deduplicate("blocks/stone");
        Coarctatio.onWorldLeave();
        assertEquals(0, StringPool.NBT_KEYS.size());
        assertEquals(0, ResourceLocationCaches.PATHS.size());
        assertNotNull(Mixins.construct(Coarctatio.class));
    }

    @Test
    void theStatisticsCoverEveryPool() {
        List<String> lines = Coarctatio.statistics();
        assertEquals("Coarctatio memory statistics", lines.get(0));
        assertTrue(lines.stream().anyMatch(line -> line.contains("NBT keys")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("Face UVs")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("Texture names")));

        assertTrue(Coarctatio.debugOverlayLine().startsWith("Coarctatio: ~"));
    }

    @Test
    void theReportListsEstimatedSavingsAndTheHeap() {
        MemoryReport.recordRemapperEntries(300);

        List<String> lines = MemoryReport.lines();
        assertEquals("Coarctatio memory saved", lines.get(0));
        assertTrue(lines.stream().anyMatch(line -> line.contains("Remapper caches") && line.contains("estimated")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("TOTAL")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("Heap now")));
        assertTrue(MemoryReport.totalBytes() > 0);
        assertTrue(MemoryReport.summary().endsWith("MB"));

        // Checks the size units.
        assertEquals("1.0 MB", MemoryReport.mib(1024L * 1024L));
        assertEquals("512 KB", MemoryReport.mib(512L * 1024L));
        assertNotNull(Mixins.construct(MemoryReport.class));
    }

}
