package com.bdmajora.coarctatio.client.model.dynamic;

import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TextureDiscoveryTest {
    @TempDir
    Path gameDir;

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
    }

    // A manager holding the given json files and pngs; anything else is not found
    private static IResourceManager resources(Map<ResourceLocation, String> json, Set<ResourceLocation> files) throws IOException {
        IResourceManager manager = mock(IResourceManager.class);
        when(manager.getResource(any())).thenAnswer(invocation -> {
            ResourceLocation location = invocation.getArgument(0);
            String text = json.get(location);
            if (text != null) {
                IResource resource = mock(IResource.class);
                if (text.equals("unreadable")) {
                    when(resource.getInputStream()).thenThrow(new IOException("corrupt"));
                } else {
                    when(resource.getInputStream()).thenReturn(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
                }
                return resource;
            }
            if (files.contains(location)) {
                return mock(IResource.class);
            }
            throw new FileNotFoundException(location.toString());
        });
        return manager;
    }

    @Test
    void spritesAreFoundFromPathsAndFromWhatTheJsonNames() throws Exception {
        Minecraft client = Mc.client();
        Mixins.set(client, "gameDir", gameDir.toFile());
        // Loose resource folders next to the game are scanned like a pack
        Path loose = gameDir.resolve("resources/examplemod/textures/items/gem.png");
        Files.createDirectories(loose.getParent());
        Files.write(loose, new byte[0]);

        IResourcePack pack = Mc.mock(IResourcePack.class, IndexedResourcePack.class);
        when(((IndexedResourcePack) pack).coarctatio$indexedPaths()).thenReturn(Set.of(
                "assets/examplemod/textures/blocks/ore.png",
                "assets/examplemod/textures/entity/chest/big.png",
                // Outside the folders models draw from, so only found if a model names it
                "assets/examplemod/textures/gui/button.png"));

        // What the location tables would hold once the reload has built them
        ModelResourceLocation oreVariant = new ModelResourceLocation("examplemod:ore", "normal");
        ModelResourceLocation brokenVariant = new ModelResourceLocation("examplemod:broken", "normal");
        ModelResourceLocation gemVariant = new ModelResourceLocation("examplemod:gem", "inventory");
        ResourceLocation ore = new ResourceLocation("examplemod:ore");
        ResourceLocation broken = new ResourceLocation("examplemod:broken");
        ModelLocations.VARIANTS_BY_BLOCKSTATE.put(ore, new ObjectOpenHashSet<>(List.of(oreVariant)));
        ModelLocations.VARIANTS_BY_BLOCKSTATE.put(broken, new ObjectOpenHashSet<>(List.of(brokenVariant)));
        ModelLocations.ITEM_VARIANTS.add(gemVariant);
        ModelLocations.addItemVariantFile(gemVariant, new ResourceLocation("examplemod:item/gem"));
        ModelLocations.READY.complete(null);

        Map<ResourceLocation, String> json = new HashMap<>();
        json.put(new ResourceLocation("examplemod:blockstates/ore.json"), "{\"variants\":{\"normal\":{\"model\":\"examplemod:ore_block\"}}}");
        json.put(new ResourceLocation("examplemod:blockstates/broken.json"), "unreadable");
        // A block model names its textures and its parent, which lives under models/block
        json.put(new ResourceLocation("examplemod:models/block/ore_block.json"), "{\"parent\":\"examplemod:ore_base\",\"textures\":{\"all\":\"examplemod:blocks/ore_overlay\"}}");
        json.put(new ResourceLocation("examplemod:models/block/ore_base.json"), "{\"textures\":{\"particle\":\"gui/button\"}}");
        // An item model's parent lives under models/, and one referring back to itself is only read once
        json.put(new ResourceLocation("examplemod:models/item/gem.json"), "{\"parent\":\"examplemod:item/gem_base\"}");
        json.put(new ResourceLocation("examplemod:models/item/gem_base.json"), "{\"parent\":\"examplemod:item/gem_base\",\"textures\":{\"layer0\":\"examplemod:items/gem_shine\"}}");
        Set<ResourceLocation> files = Set.of(
                new ResourceLocation("examplemod:textures/blocks/ore_overlay.png"),
                new ResourceLocation("minecraft:textures/gui/button.png"),
                new ResourceLocation("examplemod:textures/items/gem_shine.png"));

        try {
            TextureDiscovery.start(resources(json, files), List.of(pack));
            Set<ResourceLocation> found = TextureDiscovery.take();
            assertTrue(found.contains(new ResourceLocation("examplemod:blocks/ore")));
            assertTrue(found.contains(new ResourceLocation("examplemod:entity/chest/big")));
            assertFalse(found.contains(new ResourceLocation("examplemod:gui/button")));
            assertTrue(found.contains(new ResourceLocation("examplemod:items/gem")));
            assertTrue(found.contains(new ResourceLocation("examplemod:blocks/ore_overlay")));
            assertTrue(found.contains(new ResourceLocation("minecraft:gui/button")));
            assertTrue(found.contains(new ResourceLocation("examplemod:items/gem_shine")));
            // Sprites mods register from code are always included
            assertTrue(found.contains(new ResourceLocation("mekanism:entities/robit")));
            // The result is handed over once
            assertTrue(TextureDiscovery.take().isEmpty());
        } finally {
            ModelLocations.VARIANTS_BY_BLOCKSTATE.remove(ore);
            ModelLocations.VARIANTS_BY_BLOCKSTATE.remove(broken);
            ModelLocations.ITEM_VARIANTS.remove(gemVariant);
            ModelLocations.ITEM_VARIANT_FILES.remove(gemVariant);
        }
    }
}
