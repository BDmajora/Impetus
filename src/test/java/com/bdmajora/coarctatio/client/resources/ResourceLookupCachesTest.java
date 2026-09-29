package com.bdmajora.coarctatio.client.resources;

import com.bdmajora.testing.Mixins;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ResourceLookupCachesTest {
    @TempDir
    Path dir;

    @Test
    void aReloadBumpsTheGenerationEveryCacheKeysItselfOn() {
        int before = ResourceLookupCaches.generation();
        ResourceLookupCaches.onReload();
        assertEquals(before + 1, ResourceLookupCaches.generation());
        assertNotNull(Mixins.construct(ResourceLookupCaches.class));
    }

    @Test
    void aFolderPackIsIndexedOnceAsForwardSlashPaths() throws Exception {
        Files.createDirectories(dir.resolve("assets/minecraft/textures/blocks"));
        Files.writeString(dir.resolve("assets/minecraft/textures/blocks/stone.png"), "x");
        Files.writeString(dir.resolve("pack.mcmeta"), "{}");

        Set<String> paths = ResourceLookupCaches.indexFolder(dir.toFile());
        assertTrue(paths.contains("assets/minecraft/textures/blocks/stone.png"));
        assertTrue(paths.contains("pack.mcmeta"));
        // Directories are not files a lookup can find
        assertFalse(paths.contains("assets/minecraft"));

        // Anything that is not a directory indexes to nothing at all
        assertTrue(ResourceLookupCaches.indexFolder(dir.resolve("pack.mcmeta").toFile()).isEmpty());
        assertTrue(ResourceLookupCaches.indexFolder(dir.resolve("missing").toFile()).isEmpty());
    }

    @Test
    void anUnreadableFolderIsSkippedAndTheRestIndexed() throws Exception {
        Files.createDirectories(dir.resolve("assets/locked"));
        Files.writeString(dir.resolve("assets/locked/secret.png"), "x");
        Files.writeString(dir.resolve("pack.mcmeta"), "{}");
        java.io.File locked = dir.resolve("assets/locked").toFile();
        assertTrue(locked.setReadable(false));
        try {
            Set<String> paths = ResourceLookupCaches.indexFolder(dir.toFile());
            assertTrue(paths.contains("pack.mcmeta"));
            assertFalse(paths.contains("assets/locked/secret.png"));
        } finally {
            locked.setReadable(true);
        }
    }

    @Test
    void theMissingFileExceptionCarriesNoStackTrace() {
        StacklessFileNotFoundException thrown = new StacklessFileNotFoundException("minecraft:models/block/stone.json");
        assertInstanceOf(FileNotFoundException.class, thrown);
        assertEquals("minecraft:models/block/stone.json", thrown.getMessage());
        // Callers only ever catch these by type, and filling the trace was nearly the whole cost
        assertEquals(0, thrown.getStackTrace().length);
        assertSame(thrown, thrown.fillInStackTrace());
    }
}
