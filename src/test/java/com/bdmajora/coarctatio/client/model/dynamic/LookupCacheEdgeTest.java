package com.bdmajora.coarctatio.client.model.dynamic;

import com.bdmajora.coarctatio.client.resources.ResourceLookupCaches;
import com.bdmajora.coarctatio.client.resources.StacklessFileNotFoundException;
import net.minecraft.client.renderer.block.model.IBakedModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class LookupCacheEdgeTest {
    @Test
    void nullLoaderIsRefused() {
        assertThrows(NullPointerException.class, () -> new ModelLookupCache<String>(null, true));
    }

    @Test
    void absentModelsAreCachedOnlyWhenAsked() {
        AtomicInteger calls = new AtomicInteger();
        String key = "k";
        ModelLookupCache<String> plain = new ModelLookupCache<>(k -> {
            calls.incrementAndGet();
            return null;
        }, false);
        assertNull(plain.get(key));
        assertNull(plain.get(key));
        assertEquals(2, calls.get());
        calls.set(0);
        ModelLookupCache<String> caching = new ModelLookupCache<>(k -> {
            calls.incrementAndGet();
            return null;
        }, true);
        assertNull(caching.get(key));
        assertNull(caching.get(key));
        assertEquals(1, calls.get());
    }

    @Test
    void cacheEvictsPastItsCapacityAndClears() {
        AtomicInteger calls = new AtomicInteger();
        IBakedModel model = mock(IBakedModel.class);
        ModelLookupCache<Object> cache = new ModelLookupCache<>(k -> {
            calls.incrementAndGet();
            return model;
        }, false);
        Object[] keys = new Object[1100];
        for (int i = 0; i < keys.length; i++) {
            keys[i] = new Object();
            assertSame(model, cache.get(keys[i]));
        }
        assertEquals(1100, calls.get());
        cache.get(keys[1099]);
        assertEquals(1100, calls.get());
        cache.get(keys[0]);
        assertEquals(1101, calls.get());
        cache.clear();
        cache.get(keys[1099]);
        assertEquals(1102, calls.get());
    }

    @Test
    void generationCountsEveryReload() {
        int before = ResourceLookupCaches.generation();
        ResourceLookupCaches.onReload();
        ResourceLookupCaches.onReload();
        assertEquals(before + 2, ResourceLookupCaches.generation());
    }

    @Test
    void folderIndexHandlesMissingEmptyAndNestedFolders(@TempDir Path dir) throws Exception {
        assertTrue(ResourceLookupCaches.indexFolder(dir.resolve("missing").toFile()).isEmpty());
        assertTrue(ResourceLookupCaches.indexFolder(dir.toFile()).isEmpty());
        Files.createDirectories(dir.resolve("a/b"));
        Files.writeString(dir.resolve("a/b/c.txt"), "x");
        Files.writeString(dir.resolve("top.json"), "x");
        Set<String> paths = ResourceLookupCaches.indexFolder(dir.toFile());
        assertEquals(Set.of("a/b/c.txt", "top.json"), paths);
        assertTrue(ResourceLookupCaches.indexFolder(dir.resolve("top.json").toFile()).isEmpty());
    }

    @Test
    void stacklessErrorKeepsItsMessageButNoTrace() {
        StacklessFileNotFoundException e = new StacklessFileNotFoundException("gone");
        assertEquals("gone", e.getMessage());
        assertEquals(0, e.getStackTrace().length);
        assertSame(e, e.fillInStackTrace());
        assertNull(new StacklessFileNotFoundException(null).getMessage());
    }
}
