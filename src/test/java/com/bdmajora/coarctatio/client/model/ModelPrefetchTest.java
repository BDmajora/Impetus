package com.bdmajora.coarctatio.client.model;

import com.bdmajora.coarctatio.mixin.client.model.dynamic.AbstractResourcePackAccessor;
import com.bdmajora.coarctatio.mixin.client.model.dynamic.FallbackResourceManagerAccessor;
import com.bdmajora.coarctatio.mixin.client.model.dynamic.LegacyV2AdapterAccessor;
import com.bdmajora.coarctatio.mixin.client.model.dynamic.SimpleReloadableResourceManagerAccessor;
import com.bdmajora.coarctatio.mixin.client.resources.FileResourcePackMixin;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import net.minecraft.client.renderer.block.model.ModelBlock;
import net.minecraft.client.resources.DefaultResourcePack;
import net.minecraft.client.resources.FallbackResourceManager;
import net.minecraft.client.resources.FolderResourcePack;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.LegacyV2Adapter;
import net.minecraft.client.resources.SimpleReloadableResourceManager;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModelPrefetchTest {
    private static final ResourceLocation TEACUP = new ResourceLocation("minecraft", "models/item/teacup");
    private static final ResourceLocation MUG = new ResourceLocation("minecraft", "models/item/mug");
    private static final ResourceLocation BROKEN = new ResourceLocation("minecraft", "models/item/broken");
    private static final ResourceLocation LAMP = new ResourceLocation("example", "models/block/lamp");

    @TempDir
    Path dir;

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
    }

    @AfterEach
    void dropWhatWasPrefetched() {
        ModelPrefetch.clear();
    }

    // A zip pack whose existence index is already built, the way the server pack is by the time models load
    static IResourcePack zipPack(String... paths) {
        FileResourcePackMixin pack = Mixins.instance(FileResourcePackMixin.class);
        Mixins.set(pack, "coarctatio$entries", Set.of(paths));
        return (IResourcePack) pack;
    }

    // A manager serving these packs in which no armature exists, as in nearly every real pack
    static SimpleReloadableResourceManager manager(IResourcePack... packs) throws Exception {
        FallbackResourceManager domain = Mc.mock(FallbackResourceManager.class);
        when(((FallbackResourceManagerAccessor) domain).coarctatio$packs()).thenReturn(new ArrayList<>(Arrays.asList(packs)));
        Map<String, FallbackResourceManager> domains = new LinkedHashMap<>();
        domains.put("minecraft", domain);
        SimpleReloadableResourceManager manager = Mc.mock(SimpleReloadableResourceManager.class);
        when(((SimpleReloadableResourceManagerAccessor) manager).coarctatio$domainManagers()).thenReturn(domains);
        when(manager.getResource(any())).thenThrow(new FileNotFoundException("no armature"));
        return manager;
    }

    @Test
    void everyPackModelIsReadOnceAcrossThePoolAndHandedOutOnce() throws Exception {
        Files.createDirectories(dir.resolve("assets/example/models/block"));
        Files.writeString(dir.resolve("assets/example/models/block/lamp.json"), "{}");
        FolderResourcePack folder = Mc.mock(FolderResourcePack.class, AbstractResourcePackAccessor.class);
        when(((AbstractResourcePackAccessor) folder).coarctatio$file()).thenReturn(dir.toFile());
        // A format-2 adapter is listed as the zip it wraps; one wrapping a kind that cannot be listed is skipped
        LegacyV2Adapter adapter = Mc.mock(LegacyV2Adapter.class);
        when(((LegacyV2AdapterAccessor) adapter).coarctatio$pack())
                .thenReturn(zipPack("assets/minecraft/models/item/teacup.json", "assets/minecraft/models/item/mug.json"));
        LegacyV2Adapter wrapsUnknown = Mc.mock(LegacyV2Adapter.class);
        when(((LegacyV2AdapterAccessor) wrapsUnknown).coarctatio$pack()).thenReturn(mock(IResourcePack.class));
        IResourcePack server = zipPack("pack.mcmeta", "assets/minecraft/textures/items/teacup.png", "assets/minecraft/blockstates/lamp.json",
                "assets/minecraft/models/item/teacup.json", "assets/minecraft/models/item/broken.json");
        // The built-in pack would mean walking the classpath, so it is never listed
        SimpleReloadableResourceManager manager = manager(Mc.mock(DefaultResourcePack.class), folder, adapter, wrapsUnknown,
                mock(IResourcePack.class), server);

        Set<ResourceLocation> asked = ConcurrentHashMap.newKeySet();
        AtomicInteger reads = new AtomicInteger();
        ModelPrefetch.run(manager, location -> {
            reads.incrementAndGet();
            asked.add(location);
            if (location.equals(BROKEN)) {
                throw new FileNotFoundException(location.toString());
            }
            ModelBlock model = ModelBlock.deserialize("{\"parent\":\"item/generated\",\"textures\":{\"layer0\":\"items/teacup\"}}");
            model.name = location.toString();
            return model;
        });
        // Each model file is read once however many packs hold it, and nothing that is not one is read at all
        assertEquals(Set.of(TEACUP, MUG, BROKEN, LAMP), asked);
        assertEquals(4, reads.get());

        ModelBlock teacup = ModelPrefetch.takeModel(TEACUP);
        assertNotNull(teacup);
        assertEquals("minecraft:models/item/teacup", teacup.name);
        // Handed out once, since the loader goes on to link its parent
        assertNull(ModelPrefetch.takeModel(TEACUP));
        ResourceLocation teacupArmature = new ResourceLocation("minecraft", "armatures/item/teacup.json");
        assertNotNull(ModelPrefetch.takeAnimation(teacupArmature));
        assertNull(ModelPrefetch.takeAnimation(teacupArmature));
        // A model that could not be read is left for the loader to report, with nothing kept beside it
        assertNull(ModelPrefetch.takeModel(BROKEN));
        assertNull(ModelPrefetch.takeAnimation(new ResourceLocation("minecraft", "armatures/item/broken.json")));

        ModelPrefetch.clear();
        assertNull(ModelPrefetch.takeModel(MUG));
        assertNull(ModelPrefetch.takeAnimation(new ResourceLocation("example", "armatures/block/lamp.json")));
        assertNotNull(Mixins.construct(ModelPrefetch.class));
    }

    @Test
    void aManagerWhosePacksCannotBeListedPrefetchesNothing() {
        // Whatever an earlier run left is dropped first
        Map<ResourceLocation, ModelBlock> blocks = Mixins.get(ModelPrefetch.class, "MODEL_BLOCKS");
        blocks.put(TEACUP, ModelBlock.deserialize("{}"));
        ModelPrefetch.run(mock(IResourceManager.class), location -> {
            throw new AssertionError("nothing should be read");
        });
        assertNull(ModelPrefetch.takeModel(TEACUP));
    }

    @Test
    void onlyFilesUnderAModelsFolderBecomeTheLocationsVanillaLoaderAsksFor() {
        assertEquals(TEACUP, ModelPrefetch.modelLocation("assets/minecraft/models/item/teacup.json"));
        assertEquals(new ResourceLocation("example", "models/block/deep/lamp.tmat"),
                ModelPrefetch.modelLocation("assets/example/models/block/deep/lamp.tmat.json"));
        assertNull(ModelPrefetch.modelLocation("pack.mcmeta"));
        assertNull(ModelPrefetch.modelLocation("assets/minecraft/blockstates/stone.json"));
        assertNull(ModelPrefetch.modelLocation("assets/minecraft/models/item/teacup.png"));
        // No namespace folder, an empty one, and an empty model name
        assertNull(ModelPrefetch.modelLocation("assets/minecraft.json"));
        assertNull(ModelPrefetch.modelLocation("assets//models/item/teacup.json"));
        assertNull(ModelPrefetch.modelLocation("assets/minecraft/models/.json"));

        assertEquals(new ResourceLocation("minecraft", "armatures/item/teacup.json"), ModelPrefetch.armatureOf(TEACUP));
    }
}
