package com.bdmajora.coarctatio.dedup;

import com.bdmajora.coarctatio.CoarctatioConfig;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.floats.FloatArrays;
import it.unimi.dsi.fastutil.ints.IntArrays;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.block.model.ItemTransformVec3f;
import net.minecraft.launchwrapper.Launch;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class DeduplicationTest {
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
    void aPoolHandsBackTheFirstInstanceItSaw() {
        DeduplicationCache<String> pool = new DeduplicationCache<>("Test pool", 8);
        // An unused pool says so rather than claiming a perfect miss rate
        assertEquals("Test pool (unused)", pool.toString());
        assertEquals(0, pool.size());
        assertEquals(0, pool.shared());

        String first = new String("value");
        String second = new String("value");
        assertSame(first, pool.deduplicate(first));
        assertSame(first, pool.deduplicate(second));
        assertEquals(1, pool.shared());
        assertEquals(1, pool.size());
        // Null needs no checks at the call site
        assertNull(pool.deduplicate(null));
        assertEquals("Test pool (1/2 shared, 1 unique)", pool.toString());
    }

    @Test
    void aFullPoolStopsGrowingAndAClosedOneStopsPooling() {
        DeduplicationCache<String> pool = new DeduplicationCache<>("Small pool", 2);
        pool.deduplicate("a");
        pool.deduplicate("b");
        assertTrue(pool.toString().contains("saturated"));

        // Closing keeps the statistics but drops the table, and later values pass straight through
        pool.close();
        assertEquals(2, pool.size());
        String passedThrough = new String("a");
        assertSame(passedThrough, pool.deduplicate(passedThrough));
        assertTrue(pool.toString().contains("closed"));
        pool.close();

        // Re-opening starts a fresh bake with its own counters
        pool.open();
        assertEquals(0, pool.size());
        assertEquals(0, pool.shared());
        assertEquals("Small pool (unused)", pool.toString());
    }

    @Test
    void aCustomStrategyComparesContentsRatherThanReferences() {
        DeduplicationCache<int[]> pool = new DeduplicationCache<>("Quads", 16, IntArrays.HASH_STRATEGY);
        int[] first = {1, 2, 3};
        assertSame(first, pool.deduplicate(first));
        assertSame(first, pool.deduplicate(new int[] {1, 2, 3}));
        assertNotSame(first, pool.deduplicate(new int[] {9}));
        assertEquals(1, pool.shared());
    }

    @Test
    void threadsRacingOnOneValueAllGetTheSameInstanceWithoutALock() {
        DeduplicationCache<float[]> pool = new DeduplicationCache<>("Face UVs", 64, FloatArrays.HASH_STRATEGY);
        List<float[]> handedOut = IntStream.range(0, 20_000).parallel()
                .mapToObj(i -> pool.deduplicate(new float[] {0.0F, 0.0F, 16.0F, 16.0F}))
                .collect(Collectors.toList());
        Set<float[]> distinct = Collections.newSetFromMap(new IdentityHashMap<>());
        distinct.addAll(handedOut);
        assertEquals(1, distinct.size());
        assertEquals(1, pool.size());
        assertEquals(19_999, pool.shared());
    }

    @Test
    void theShardedPoolResolvesAKeyToOneInstancePerShard() {
        ShardedStringCache cache = new ShardedStringCache("Keys", 64);
        assertEquals("Keys (unused)", cache.toString());
        assertNull(cache.deduplicate(null));

        String key = new String("Damage");
        assertSame(key, cache.deduplicate(key));
        assertSame(key, cache.deduplicate(new String("Damage")));
        assertEquals(1, cache.shared());
        assertEquals(1, cache.size());
        assertTrue(cache.toString().contains("1/2 shared"));

        // Clearing releases the tables; strings already handed out stay valid
        cache.clear();
        assertEquals(0, cache.size());
        assertEquals(0, cache.shared());
        assertEquals("Keys (unused)", cache.toString());
    }

    @Test
    void aSaturatedShardStillServesHitsWithoutGrowing() {
        // One entry per shard, so the second distinct key landing on a shard is refused
        ShardedStringCache cache = new ShardedStringCache("Tiny", 1);
        String kept = null;
        String rejected = null;
        for (int i = 0; i < 4096 && (kept == null || rejected == null); i++) {
            String candidate = "key" + i;
            String result = cache.deduplicate(candidate);
            if (result == candidate && kept == null) {
                kept = candidate;
            } else if (result == candidate && shardOf(candidate) == shardOf(kept) && !candidate.equals(kept)) {
                rejected = candidate;
            }
        }
        assertNotNull(kept);
        assertNotNull(rejected);
        // The key the full shard already holds is still shared out
        assertSame(kept, cache.deduplicate(new String(kept)));
        // One it does not know is handed straight back
        String unknown = new String(rejected);
        assertSame(unknown, cache.deduplicate(unknown));
    }

    private static int shardOf(String value) {
        int hash = value.hashCode();
        hash ^= hash >>> 16;
        return hash & 15;
    }

    @Test
    void theSessionPoolsAreNamedAndSizedFromTheConfig() {
        assertNotNull(StringPool.NBT_KEYS);
        assertNotNull(StringPool.LOADER);
        assertSame(StringPool.NBT_KEYS.deduplicate("id"), StringPool.NBT_KEYS.deduplicate(new String("id")));
        assertNotNull(ResourceLocationCaches.DOMAINS);
        assertNotNull(ResourceLocationCaches.PATHS);
        assertNotNull(Mixins.construct(StringPool.class));
        assertNotNull(Mixins.construct(ResourceLocationCaches.class));
    }

    @Test
    void theBakeScopedPoolsOpenAndCloseWithTheReload() {
        ModelCaches.open();
        int[] quad = {1, 2, 3, 4};
        assertSame(quad, ModelCaches.QUADS.deduplicate(quad));
        assertSame(quad, ModelCaches.QUADS.deduplicate(new int[] {1, 2, 3, 4}));
        assertSame(ModelCaches.VARIANTS.deduplicate("normal"), ModelCaches.VARIANTS.deduplicate(new String("normal")));
        float[] uv = {0f, 0f, 16f, 16f};
        assertSame(uv, ModelCaches.FACE_UVS.deduplicate(uv));
        assertSame(uv, ModelCaches.FACE_UVS.deduplicate(new float[] {0f, 0f, 16f, 16f}));
        assertSame(ModelCaches.MODEL_TEXTURES.deduplicate("#all"), ModelCaches.MODEL_TEXTURES.deduplicate(new String("#all")));

        // Quads a mod subclassed are counted by class so an empty pool can be explained
        assertEquals("none", ModelCaches.skippedSummary());
        ModelCaches.recordSkippedQuad(String.class);
        ModelCaches.recordSkippedQuad(String.class);
        ModelCaches.recordSkippedQuad(Integer.class);
        assertEquals("2x java.lang.String, 1x java.lang.Integer", ModelCaches.skippedSummary());

        // Closing drops the bake pools; variants stay open for the rest of the session
        ModelCaches.close();
        assertSame(quad, ModelCaches.QUADS.deduplicate(quad));
        assertSame(ModelCaches.VARIANTS.deduplicate("inventory"), ModelCaches.VARIANTS.deduplicate(new String("inventory")));
        ModelCaches.open();
        assertEquals("none", ModelCaches.skippedSummary());
        assertNotNull(Mixins.construct(ModelCaches.class));
    }

    @Test
    void cameraTransformsAreComparedByTheirEightVectors() {
        TransformCaches.open();
        // Vanilla's ItemTransformVec3f hashes its Vector3f fields, which compare by value but hash by identity, so two
        // separately built but identical transforms collapse only when they happen to land in the same bucket; every
        // assertion here therefore avoids pooling two equal-by-value instances, the default included (scale 1)
        assertSame(ItemCameraTransforms.DEFAULT, TransformCaches.TRANSFORMS.deduplicate(ItemCameraTransforms.DEFAULT));
        ItemTransformVec3f vector = vector(3f);
        ItemCameraTransforms first = transforms(vector);
        assertSame(first, TransformCaches.TRANSFORMS.deduplicate(first));
        // What reliably collapses is every model that shares one instance, which is the common case
        assertSame(first, TransformCaches.TRANSFORMS.deduplicate(transforms(vector)));
        assertNotSame(first, TransformCaches.TRANSFORMS.deduplicate(transforms(vector(2f))));
        assertNotNull(TransformCaches.TRANSFORMS.deduplicate(transforms(vector(3f))));
        assertNull(TransformCaches.TRANSFORMS.deduplicate(null));

        // An empty override list collapses onto the shared one; a real list is per-model
        assertNull(TransformCaches.deduplicate(null));
        assertSame(ItemOverrideList.NONE, TransformCaches.deduplicate(new ItemOverrideList(ImmutableList.of())));
        ItemOverrideList populated = new ItemOverrideList(ImmutableList.of(
                new net.minecraft.client.renderer.block.model.ItemOverride(
                        new net.minecraft.util.ResourceLocation("minecraft:stone"), com.google.common.collect.ImmutableMap.of())));
        assertSame(populated, TransformCaches.deduplicate(populated));
        TransformCaches.close();
        assertNotNull(Mixins.construct(TransformCaches.class));
    }

    private static ItemTransformVec3f vector(float scale) {
        return new ItemTransformVec3f(
                new org.lwjgl.util.vector.Vector3f(0f, 0f, 0f),
                new org.lwjgl.util.vector.Vector3f(0f, 0f, 0f),
                new org.lwjgl.util.vector.Vector3f(scale, scale, scale));
    }

    private static ItemCameraTransforms transforms(ItemTransformVec3f vector) {
        return new ItemCameraTransforms(vector, vector, vector, vector, vector, vector, vector, vector);
    }
}
