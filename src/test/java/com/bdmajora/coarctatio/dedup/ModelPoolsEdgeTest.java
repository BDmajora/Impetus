package com.bdmajora.coarctatio.dedup;

import com.bdmajora.coarctatio.Coarctatio;
import com.bdmajora.coarctatio.CoarctatioConfig;
import com.bdmajora.coarctatio.MemoryReport;
import com.bdmajora.coarctatio.mixin.client.ModelManagerMixin;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.launchwrapper.Launch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ModelPoolsEdgeTest {
    @TempDir
    static Path dir;

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
        Statics.set(Launch.class, "minecraftHome", dir.toFile());
        Mixins.set(CoarctatioConfig.class, "instance", null);
    }

    @BeforeEach
    void openPools() {
        ModelCaches.open();
        Mixins.<AtomicLong>get(MemoryReport.class, "REMAPPER_ENTRIES").set(0);
    }

    @AfterEach
    void reopenPools() {
        ModelCaches.open();
        Mixins.<AtomicLong>get(MemoryReport.class, "REMAPPER_ENTRIES").set(0);
    }

    @Test
    void faceUvsAreSharedByContentNotByIdentity() {
        float[] first = {0f, 0f, 16f, 16f};
        assertSame(first, ModelCaches.FACE_UVS.deduplicate(first));
        assertSame(first, ModelCaches.FACE_UVS.deduplicate(new float[] {0f, 0f, 16f, 16f}));
        // A different order, length or a negative zero is another array
        float[] reordered = {16f, 16f, 0f, 0f};
        assertSame(reordered, ModelCaches.FACE_UVS.deduplicate(reordered));
        float[] shorter = {0f, 0f, 16f};
        assertSame(shorter, ModelCaches.FACE_UVS.deduplicate(shorter));
        float[] empty = {};
        assertSame(empty, ModelCaches.FACE_UVS.deduplicate(empty));
        assertSame(empty, ModelCaches.FACE_UVS.deduplicate(new float[0]));
        // NaN arrays still compare equal to themselves by bits
        float[] nan = {Float.NaN};
        assertSame(nan, ModelCaches.FACE_UVS.deduplicate(nan));
        assertSame(nan, ModelCaches.FACE_UVS.deduplicate(new float[] {Float.NaN}));
        assertNull(ModelCaches.FACE_UVS.deduplicate(null));
    }

    @Test
    void textureNamesAreSharedByEquality() {
        String first = new String("minecraft:blocks/stone");
        assertSame(first, ModelCaches.MODEL_TEXTURES.deduplicate(first));
        assertSame(first, ModelCaches.MODEL_TEXTURES.deduplicate(new String("minecraft:blocks/stone")));
        // Case matters
        String upper = new String("minecraft:blocks/STONE");
        assertSame(upper, ModelCaches.MODEL_TEXTURES.deduplicate(upper));
        assertSame("", ModelCaches.MODEL_TEXTURES.deduplicate(""));
        assertNull(ModelCaches.MODEL_TEXTURES.deduplicate(null));
    }

    @Test
    void openingTwiceStartsEmptyAndClosingTwiceKeepsTheSize() {
        String name = new String("#all");
        ModelCaches.MODEL_TEXTURES.deduplicate(name);
        ModelCaches.MODEL_TEXTURES.deduplicate(new String("#side"));
        assertEquals(2, ModelCaches.MODEL_TEXTURES.size());
        ModelCaches.open();
        assertEquals(0, ModelCaches.MODEL_TEXTURES.size());
        assertEquals(0, ModelCaches.MODEL_TEXTURES.shared());
        ModelCaches.MODEL_TEXTURES.deduplicate(new String("#top"));
        ModelCaches.close();
        assertEquals(1, ModelCaches.MODEL_TEXTURES.size());
        ModelCaches.close();
        assertEquals(1, ModelCaches.MODEL_TEXTURES.size());
        // A closed pool hands back what it is given and counts nothing
        String fresh = new String("#top");
        assertSame(fresh, ModelCaches.MODEL_TEXTURES.deduplicate(fresh));
        assertEquals(0, ModelCaches.MODEL_TEXTURES.shared());
        assertTrue(ModelCaches.MODEL_TEXTURES.toString().endsWith(", closed)") || ModelCaches.MODEL_TEXTURES.toString().contains("unused"),
                ModelCaches.MODEL_TEXTURES.toString());
    }

    @Test
    void theReloadHookBracketsEveryModelPartPool() {
        ModelManagerMixin manager = Mixins.instance(ModelManagerMixin.class);
        IResourceManager resources = mock(IResourceManager.class);
        ModelCaches.close();
        Mixins.call(manager, "coarctatio$openPools", resources, Mixins.ci());
        float[] uvs = {1f, 2f, 3f, 4f};
        String texture = new String("#north");
        ModelCaches.FACE_UVS.deduplicate(uvs);
        ModelCaches.MODEL_TEXTURES.deduplicate(texture);
        assertSame(uvs, ModelCaches.FACE_UVS.deduplicate(new float[] {1f, 2f, 3f, 4f}));
        assertSame(texture, ModelCaches.MODEL_TEXTURES.deduplicate(new String("#north")));
        Mixins.call(manager, "coarctatio$closePools", resources, Mixins.ci());
        // Models baked after the reload, as VintageFix does on demand, are left alone
        float[] late = {1f, 2f, 3f, 4f};
        assertSame(late, ModelCaches.FACE_UVS.deduplicate(late));
        assertEquals(1, ModelCaches.FACE_UVS.size());
        // A reload that starts again throws away the old pool rather than growing it
        Mixins.call(manager, "coarctatio$openPools", resources, Mixins.ci());
        assertEquals(0, ModelCaches.FACE_UVS.size());
        // Closing without opening is harmless
        Coarctatio.onResourceReloadFinish();
        Coarctatio.onResourceReloadFinish();
        assertNull(Mixins.get(ModelCaches.FACE_UVS, "pool"));
    }

    @Test
    void aSaturatedPoolStopsAddingButStillShares() {
        DeduplicationCache<String> small = new DeduplicationCache<>("small", 2);
        String a = new String("a");
        String b = new String("b");
        assertSame(a, small.deduplicate(a));
        assertSame(b, small.deduplicate(b));
        String c = new String("c");
        assertSame(c, small.deduplicate(c));
        assertEquals(2, small.size());
        assertTrue(small.toString().contains("saturated"));
        assertSame(a, small.deduplicate(new String("a")));
        assertSame(b, small.deduplicate(new String("b")));
        // A new open lifts the saturation
        small.open();
        assertFalse(small.toString().contains("saturated"));
    }

    @Test
    void leavingAWorldClearsOnlyThePlayPoolsNotTheModelParts() {
        String texture = new String("#up");
        ModelCaches.MODEL_TEXTURES.deduplicate(texture);
        StringPool.NBT_KEYS.deduplicate(new String("id"));
        Coarctatio.onWorldLeave();
        assertEquals(0, StringPool.NBT_KEYS.size());
        assertEquals(1, ModelCaches.MODEL_TEXTURES.size());
        assertSame(texture, ModelCaches.MODEL_TEXTURES.deduplicate(new String("#up")));
    }

    @Test
    void theStatisticsNameOnlyThePoolsThatStillExist() {
        List<String> lines = Coarctatio.statistics();
        assertEquals("Coarctatio memory statistics", lines.get(0));
        String all = String.join("\n", lines);
        for (String gone : List.of("Quad vertex data", "Model variants", "Camera transforms", "Multipart", "Block states", "Property maps",
                "Quads not pooled", "Dynamic")) {
            assertFalse(all.contains(gone), gone);
        }
        for (String kept : List.of("Resource domains", "Resource paths", "Face UVs", "Texture names", "NBT keys")) {
            assertTrue(all.contains(kept), kept);
        }
        assertEquals(6, lines.size());
    }

    @Test
    void theReportSkipsEmptyLinesAndFormatsSizesAtTheirBoundaries() {
        // With nothing saved only the header, total and heap lines are left
        Mixins.set(ResourceLocationCaches.DOMAINS, "hits", new java.util.concurrent.atomic.LongAdder());
        Mixins.set(ResourceLocationCaches.PATHS, "hits", new java.util.concurrent.atomic.LongAdder());
        Mixins.set(StringPool.NBT_KEYS, "hits", new java.util.concurrent.atomic.LongAdder());
        List<String> empty = MemoryReport.lines();
        assertEquals(3, empty.size(), empty.toString());
        assertEquals(0, MemoryReport.totalBytes());
        assertEquals("0 KB", MemoryReport.summary());
        assertTrue(empty.get(1).contains("TOTAL") && empty.get(1).contains("0 KB"));
        for (String gone : List.of("Texture pixel data", "Class loader cache", "Block state tables", "State property maps", "Quad vertex data",
                "Model transforms", "Multipart predicates", "Model variants")) {
            assertFalse(String.join("\n", empty).contains(gone), gone);
        }

        // A negative count is clamped in its line
        MemoryReport.recordRemapperEntries(-3);
        assertEquals(3, MemoryReport.lines().size());
        MemoryReport.recordRemapperEntries(4);
        List<String> one = MemoryReport.lines();
        assertEquals(4, one.size(), one.toString());
        assertTrue(one.get(1).contains("Remapper caches") && one.get(1).endsWith("estimated"));
        assertEquals(8192, MemoryReport.totalBytes());

        assertEquals("0 KB", MemoryReport.mib(0));
        assertEquals("0 KB", MemoryReport.mib(511));
        assertEquals("1 KB", MemoryReport.mib(1024));
        assertEquals("1024 KB", MemoryReport.mib(1024L * 1024L - 1));
        assertEquals("1.0 MB", MemoryReport.mib(1024L * 1024L));
        assertEquals("1.5 MB", MemoryReport.mib(1024L * 1024L * 3 / 2));
        assertEquals("8192.0 MB", MemoryReport.mib(8L * 1024 * 1024 * 1024));
    }
}
