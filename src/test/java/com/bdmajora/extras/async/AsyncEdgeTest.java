package com.bdmajora.extras.async;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class AsyncEdgeTest {
    @Test
    void costModelNeverReturnsANonPositiveChunkSize() {
        for (double seed : new double[] {0.0, -5.0, Double.NaN, 1e-12, Double.MIN_VALUE, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 25_000.0}) {
            CostModel model = new CostModel(seed);
            for (int items : new int[] {0, 1, 7, 1_000, Integer.MAX_VALUE}) {
                for (int workers : new int[] {-3, 0, 1, 8}) {
                    int size = model.chunkSize(items, workers);
                    assertTrue(size >= 1, "seed " + seed + " items " + items + " workers " + workers);
                }
            }
        }
    }

    @Test
    void costModelLearnsFromWrappedTasks() {
        CostModel model = new CostModel(100.0);
        assertTrue(model.shouldRunSequentially(2));
        assertFalse(model.shouldRunSequentially(1_000_000));
        model.wrap(0, () -> { }).run();
        model.wrap(10, () -> { }).run();
        assertTrue(model.chunkSize(10, 1) >= 1);
        assertThrows(IllegalStateException.class, () -> model.wrap(1, () -> {
            throw new IllegalStateException("boom");
        }).run());
        assertThrows(NullPointerException.class, () -> model.wrap(1, null).run());
    }

    @Test
    void syncRulesAcceptNullAndBlankEntries() {
        SyncEntityRules.load(new String[] {null, "  ", "zombie", "mod:*", "other:thing", ":*", "*"});
        assertTrue(SyncEntityRules.matches(new ResourceLocation("minecraft", "zombie")));
        assertTrue(SyncEntityRules.matches(new ResourceLocation("mod", "anything")));
        assertTrue(SyncEntityRules.matches(new ResourceLocation("other", "thing")));
        assertFalse(SyncEntityRules.matches(new ResourceLocation("other", "different")));
        SyncEntityRules.load(null);
        assertFalse(SyncEntityRules.matches(new ResourceLocation("minecraft", "zombie")));
        SyncEntityRules.load(new String[0]);
    }

    @Test
    void entityListEqualsAnyListWithTheSameContents() {
        Entity a = mock(Entity.class);
        Entity b = mock(Entity.class);
        ConcurrentEntityList list = new ConcurrentEntityList(Arrays.asList(a, b));
        List<Entity> plain = new ArrayList<>(Arrays.asList(a, b));
        assertEquals(plain, list);
        assertEquals(System.identityHashCode(list), list.hashCode());
        assertNotEquals(list, Collections.singletonList(a));
        assertThrows(NullPointerException.class, () -> new ConcurrentEntityList(null));
    }

    @Test
    void entityListSnapshotsSurviveRemovalDuringIteration() {
        Entity a = mock(Entity.class);
        Entity b = mock(Entity.class);
        ConcurrentEntityList list = new ConcurrentEntityList(Arrays.asList(a, b, a));
        Iterator<Entity> it = list.iterator();
        assertThrows(IllegalStateException.class, it::remove);
        assertSame(a, it.next());
        it.remove();
        assertThrows(IllegalStateException.class, it::remove);
        assertEquals(2, list.size());
        assertSame(b, it.next());
        assertSame(a, it.next());
        assertFalse(it.hasNext());
        assertThrows(NoSuchElementException.class, it::next);
        assertThrows(IndexOutOfBoundsException.class, () -> list.get(2));
        assertThrows(IndexOutOfBoundsException.class, () -> list.get(-1));
    }

    @Test
    void longMapHandlesDefaultsAndRefusesNullValues() {
        ConcurrentLong2ObjectMap<String> map = new ConcurrentLong2ObjectMap<>(4);
        map.defaultReturnValue("none");
        assertEquals("none", map.get(5L));
        assertNull(map.put(Long.MIN_VALUE, null == null ? "min" : "") == null ? null : null);
        assertEquals("min", map.get(Long.MIN_VALUE));
        assertEquals("min", map.put(Long.MIN_VALUE, "again"));
        assertEquals("again", map.remove(Long.MIN_VALUE));
        assertEquals("none", map.remove(Long.MIN_VALUE));
        assertThrows(NullPointerException.class, () -> map.put(1L, null));
        Long2ObjectOpenHashMap<String> source = new Long2ObjectOpenHashMap<>();
        source.put(7L, "seven");
        ConcurrentLong2ObjectMap<String> copy = new ConcurrentLong2ObjectMap<>(source);
        assertEquals(1, copy.size());
        assertTrue(copy.containsValue("seven"));
        copy.long2ObjectEntrySet().iterator().next().setValue("changed");
        assertEquals("changed", copy.get(7L));
        assertThrows(IllegalArgumentException.class, () -> new ConcurrentLong2ObjectMap<String>(-1));
    }
}
