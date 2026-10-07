package com.bdmajora.coarctatio.mixin.client.resources;

import com.bdmajora.coarctatio.client.resources.ResourceLookupCaches;
import com.bdmajora.coarctatio.client.resources.StacklessFileNotFoundException;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ResourcePackMixinsTest {
    @TempDir
    Path dir;

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
    }

    @Test
    void theBuiltInPackAnswersEachLocationOnceUntilTheNextReload() {
        DefaultResourcePackMixin pack = Mixins.concrete(DefaultResourcePackMixin.class);
        Mixins.set(pack, "coarctatio$known", new Object2BooleanOpenHashMap<ResourceLocation>());
        Mixins.set(pack, "coarctatio$knownGeneration", -1);
        ResourceLocation stone = new ResourceLocation("minecraft:textures/blocks/stone.png");

        // Nothing known yet, so vanilla's own lookup runs and the answer is remembered
        CallbackInfoReturnable<Boolean> first = Mixins.cir();
        Mixins.call(pack, "coarctatio$answerFromCache", stone, first);
        assertFalse(first.isCancelled());
        Mixins.call(pack, "coarctatio$remember", stone, Mixins.cir(true));

        CallbackInfoReturnable<Boolean> second = Mixins.cir();
        Mixins.call(pack, "coarctatio$answerFromCache", stone, second);
        assertTrue(second.isCancelled());
        assertTrue(second.getReturnValue());

        // A reload drops what was known, since a pack may have been added underneath
        ResourceLookupCaches.onReload();
        CallbackInfoReturnable<Boolean> afterReload = Mixins.cir();
        Mixins.call(pack, "coarctatio$answerFromCache", stone, afterReload);
        assertFalse(afterReload.isCancelled());
    }

    @Test
    void aZipPackIsIndexedOnceAndOnlyForWhatCanBeAskedFor() throws Exception {
        File zipFile = dir.resolve("pack.zip").toFile();
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(zipFile.toPath()))) {
            zip.putNextEntry(new ZipEntry("assets/minecraft/textures/blocks/stone.png"));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("pack.mcmeta"));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("com/example/Mod.class"));
            zip.closeEntry();
        }

        FileResourcePackMixin pack = Mixins.instance(FileResourcePackMixin.class);
        ZipFile zip = new ZipFile(zipFile);
        Mixins.stub(pack, "getResourcePackZipFile", invocation -> zip);

        CallbackInfoReturnable<Boolean> hit = Mixins.cir();
        Mixins.call(pack, "coarctatio$lookupInIndex", "assets/minecraft/textures/blocks/stone.png", hit);
        assertTrue(hit.isCancelled());
        assertTrue(hit.getReturnValue());

        CallbackInfoReturnable<Boolean> miss = Mixins.cir();
        Mixins.call(pack, "coarctatio$lookupInIndex", "assets/minecraft/textures/blocks/dirt.png", miss);
        assertTrue(miss.isCancelled());
        assertFalse(miss.getReturnValue());

        Set<String> paths = pack.coarctatio$indexedPaths();
        assertTrue(paths.contains("pack.mcmeta"));
        // Class files are never asked for, so they stay out of the set
        assertFalse(paths.contains("com/example/Mod.class"));
        assertSame(paths, pack.coarctatio$indexedPaths());
        zip.close();
    }

    @Test
    void aPackWhoseZipCannotBeReadFallsBackToVanilla() {
        FileResourcePackMixin pack = Mixins.instance(FileResourcePackMixin.class);
        Mixins.stub(pack, "getResourcePackZipFile", invocation -> {
            throw new IOException("not a zip");
        });

        CallbackInfoReturnable<Boolean> cir = Mixins.cir();
        Mixins.call(pack, "coarctatio$lookupInIndex", "pack.mcmeta", cir);
        assertFalse(cir.isCancelled());
        assertNull(pack.coarctatio$indexedPaths());
        // Having failed once, it does not try again per lookup
        CallbackInfoReturnable<Boolean> again = Mixins.cir();
        Mixins.call(pack, "coarctatio$lookupInIndex", "pack.mcmeta", again);
        assertFalse(again.isCancelled());
    }

    @Test
    void aFolderPackAnswersFromItsIndexAndRebuildsOnReload() throws Exception {
        Files.createDirectories(dir.resolve("assets/minecraft"));
        Files.writeString(dir.resolve("assets/minecraft/sounds.json"), "{}");

        FolderResourcePackMixin pack = Mixins.concrete(FolderResourcePackMixin.class, dir.toFile());
        assertEquals(dir.toFile(), Mixins.get(pack, "resourcePackFile"));

        CallbackInfoReturnable<File> found = Mixins.cir();
        Mixins.call(pack, "coarctatio$lookupInIndex", "assets/minecraft/sounds.json", found);
        assertTrue(found.isCancelled());
        assertEquals(dir.resolve("assets/minecraft/sounds.json").toFile(), found.getReturnValue());

        // A wrong-case or missing name is not in the index, which is the answer vanilla's canonical-path check gave
        CallbackInfoReturnable<File> missing = Mixins.cir();
        Mixins.call(pack, "coarctatio$lookupInIndex", "assets/minecraft/Sounds.json", missing);
        assertNull(missing.getReturnValue());

        Set<String> first = pack.coarctatio$indexedPaths();
        assertSame(first, pack.coarctatio$indexedPaths());
        // An edited development pack is picked up after a reload, exactly as before
        Files.writeString(dir.resolve("assets/minecraft/new.json"), "{}");
        ResourceLookupCaches.onReload();
        Set<String> rebuilt = pack.coarctatio$indexedPaths();
        assertNotSame(first, rebuilt);
        assertTrue(rebuilt.contains("assets/minecraft/new.json"));
    }

    @Test
    void bothManagersThrowWithoutFillingAStackTrace() {
        FallbackResourceManagerMixin fallback = Mixins.concrete(FallbackResourceManagerMixin.class);
        assertInstanceOf(StacklessFileNotFoundException.class,
                Mixins.call(fallback, "coarctatio$stackless", "minecraft:models/block/stone.json"));
        // Streams are opened without the debug leak tracker, whatever the logger's level
        Mc.Recorded<Boolean> debugEnabled = Mc.operation(true);
        assertFalse((boolean) Mixins.call(fallback, "coarctatio$untrackedStreams", mock(Logger.class), debugEnabled));
        assertTrue(debugEnabled.calls.isEmpty());

        SimpleReloadableResourceManagerMixin manager = Mixins.concrete(SimpleReloadableResourceManagerMixin.class);
        assertInstanceOf(StacklessFileNotFoundException.class,
                Mixins.call(manager, "coarctatio$stackless", "example:models/block/thing.json"));

        // Reloading is what moves every lookup cache on to its next generation
        int before = ResourceLookupCaches.generation();
        Mixins.call(manager, "coarctatio$newGeneration", java.util.List.of(), Mixins.ci());
        assertEquals(before + 1, ResourceLookupCaches.generation());
    }
}
