package com.bdmajora.coarctatio.client.model.dynamic;

import com.bdmajora.coarctatio.mixin.client.model.dynamic.ModelBakeryAccessor;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ModelBlockDefinition;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.statemap.BlockStateMapper;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.ModelLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModelLocationsTest {
    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
    }

    @Test
    void everyKnownLocationIsGatheredWithoutLoadingAModel() {
        Mixins.set(ModelLocations.class, "tablesBuilt", false);
        ModelLoader loader = Mc.mock(ModelLoader.class);
        Map<Item, List<String>> variants = new HashMap<>();
        variants.put(Items.DIAMOND_SWORD, new ArrayList<>(List.of("minecraft:diamond_sword", "minecraft:broken_sword")));
        when(((ModelBakeryAccessor) (Object) loader).coarctatio$variantNames()).thenReturn(variants);

        BlockStateMapper mapper = mock(BlockStateMapper.class);
        ResourceLocation blockstate = new ResourceLocation("minecraft:stone");
        when(mapper.getBlockstateLocations(any())).thenReturn(Collections.emptySet());
        when(mapper.getBlockstateLocations(Blocks.STONE)).thenReturn(java.util.Set.of(blockstate));
        ModelResourceLocation variant = new ModelResourceLocation("minecraft:stone", "normal");
        when(mapper.getVariants(any())).thenReturn(Collections.emptyMap());
        when(mapper.getVariants(Blocks.STONE)).thenReturn(Map.of(Blocks.STONE.getDefaultState(), variant));

        ModelLocations.init(loader, mapper);
        assertTrue(ModelLocations.ALL_KNOWN.contains(variant));
        assertTrue(ModelLocations.ALL_KNOWN.contains(new ModelResourceLocation("minecraft:diamond_sword", "inventory")));
        // The item frame's models are loaded by the entity renderer, so nothing else would have listed them
        assertTrue(ModelLocations.ALL_KNOWN.contains(new ModelResourceLocation("item_frame", "normal")));
        assertSame(Blocks.STONE, ModelLocations.blockFor(blockstate));
        assertTrue(ModelLocations.isKnownVariant(blockstate, variant));
        assertFalse(ModelLocations.isKnownVariant(blockstate, new ModelResourceLocation("minecraft:stone", "odd")));
        assertFalse(ModelLocations.isKnownVariant(new ResourceLocation("minecraft:nothing"), variant));

        // Built once; a second reload only refreshes the item variant names
        ModelLocations.init(loader, mapper);
        assertEquals(List.of("minecraft:diamond_sword", "minecraft:broken_sword"),
                ModelLocations.variantNames(Items.DIAMOND_SWORD));
        // An item nothing registered variants for answers with its own registry name
        assertEquals(List.of("minecraft:stone"), ModelLocations.variantNames(Item.getItemFromBlock(Blocks.STONE)));
        assertNotNull(Mixins.construct(ModelLocations.class));
    }

    @Test
    void anItemVariantNamesItsOwnModelFile() {
        assertEquals(new ResourceLocation("minecraft", "item/diamond_sword"),
                ModelLocations.itemFile("minecraft:diamond_sword"));
        // The variant part of a name is not part of the file it is loaded from
        assertEquals(new ResourceLocation("minecraft", "item/diamond_sword"),
                ModelLocations.itemFile("minecraft:diamond_sword#inventory"));

        assertEquals(new ModelResourceLocation("minecraft:diamond_sword", "inventory"),
                ModelLocations.inventoryVariant("minecraft:diamond_sword"));
        // A name that carries its own variant keeps it
        assertEquals(new ModelResourceLocation("minecraft:diamond_sword", "broken"),
                ModelLocations.inventoryVariant("minecraft:diamond_sword#broken"));

        // An override model found inside an item model is another variant to remember
        ModelResourceLocation override = new ModelResourceLocation("minecraft:bow", "pulling");
        assertNull(ModelLocations.itemFileFor(override));
        ModelLocations.addItemVariantFile(override, new ResourceLocation("minecraft", "item/bow_pulling_0"));
        assertEquals(new ResourceLocation("minecraft", "item/bow_pulling_0"), ModelLocations.itemFileFor(override));
    }

    @Test
    void aBlockstateFileIsParsedWithItsBlocksStateContainer() throws Exception {
        Mixins.set(ModelLocations.class, "tablesBuilt", true);
        Minecraft client = Mc.client();
        IResourceManager resources = mock(IResourceManager.class);
        when(client.getResourceManager()).thenReturn(resources);

        String json = "{\"multipart\":[{\"apply\":{\"model\":\"minecraft:fence_post\"}}]}";
        IResource resource = mock(IResource.class);
        when(resource.getInputStream()).thenReturn(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        when(resource.getResourceLocation()).thenReturn(new ResourceLocation("minecraft:blockstates/oak_fence.json"));
        when(resource.getResourcePackName()).thenReturn("test pack");
        when(resources.getAllResources(any())).thenReturn(List.of(resource));

        ResourceLocation fence = new ResourceLocation("minecraft:oak_fence");
        Mixins.<Map<ResourceLocation, net.minecraft.block.Block>>get(ModelLocations.class, "BLOCK_BY_BLOCKSTATE")
                .put(fence, Blocks.OAK_FENCE);
        ModelBlockDefinition definition = ModelLocations.loadDefinition(fence);
        assertTrue(definition.hasMultipartData());
        // The selectors need the block's own state container to build their predicates
        assertSame(Blocks.OAK_FENCE.getBlockState(), definition.getMultipartData().getStateContainer());

        // A file that cannot be read, and one that is not valid JSON, are both reported against the model
        when(resources.getAllResources(any())).thenThrow(new IOException("no such file"));
        assertThrows(RuntimeException.class, () -> ModelLocations.loadDefinition(fence));
        IResource broken = mock(IResource.class);
        when(broken.getInputStream()).thenReturn(new ByteArrayInputStream("not json".getBytes(StandardCharsets.UTF_8)));
        IResourceManager brokenResources = mock(IResourceManager.class);
        when(brokenResources.getAllResources(any())).thenReturn(List.of(broken));
        when(client.getResourceManager()).thenReturn(brokenResources);
        assertThrows(RuntimeException.class, () -> ModelLocations.loadDefinition(fence));
    }

    @Test
    void aNamespaceGetsAHandfulOfErrorsAndThenSilence() {
        Mixins.<it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap<String>>get(ModelLocations.class, "ERRORS_BY_NAMESPACE").clear();
        for (int i = 0; i < 6; i++) {
            assertTrue(ModelLocations.canLogError("brokenmod"), "error " + i + " should still be logged");
        }
        // The limit is announced once, then the namespace goes quiet
        assertFalse(ModelLocations.canLogError("brokenmod"));
        assertFalse(ModelLocations.canLogError("brokenmod"));
        assertTrue(ModelLocations.canLogError("anothermod"));
    }
}
