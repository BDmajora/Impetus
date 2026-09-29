package com.bdmajora.coarctatio.client.model.dynamic;

import com.bdmajora.coarctatio.CoarctatioConfig;
import com.bdmajora.coarctatio.mixin.client.model.dynamic.FallbackResourceManagerAccessor;
import com.bdmajora.coarctatio.mixin.client.model.dynamic.ModelLoaderRegistryAccessor;
import com.bdmajora.coarctatio.mixin.client.model.dynamic.ModelManagerDynamicMixin;
import com.bdmajora.coarctatio.mixin.client.model.dynamic.SimpleReloadableResourceManagerAccessor;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockModelShapes;
import net.minecraft.client.renderer.StitcherException;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelBakery;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.statemap.BlockStateMapper;
import net.minecraft.client.renderer.texture.ITextureMapPopulator;
import net.minecraft.client.renderer.texture.Stitcher;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.FallbackResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.SimpleReloadableResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.ICustomModelLoader;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import slimeknights.tconstruct.library.client.CustomTextureCreator;

import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DynamicModelsTest {
    @TempDir
    Path gameDir;

    private final List<ICustomModelLoader> registered = new ArrayList<>();
    private final List<ModelResourceLocation> known = new ArrayList<>();
    private Minecraft client;
    private TextureAtlasSprite missingSprite;

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
    }

    @BeforeEach
    void installClient() {
        client = Mc.client();
        Mixins.set(client, "gameDir", gameDir.toFile());
        TextureMap blocks = Mc.mock(TextureMap.class);
        missingSprite = Mc.uninitialized(TextureAtlasSprite.class);
        Mixins.set(missingSprite, "iconName", "missingno");
        when(blocks.getMissingSprite()).thenReturn(missingSprite);
        when(blocks.getAtlasSprite(any())).thenReturn(missingSprite);
        when(client.getTextureMapBlocks()).thenReturn(blocks);
        CoarctatioConfig config = Mc.uninitialized(CoarctatioConfig.class);
        config.dynamicModelsEagerNamespaces = new String[] {"eagermod"};
        Mixins.set(CoarctatioConfig.class, "instance", config);
        // Guidebook's book model is drawn by a renderer that never asks for it, so it is always loaded up front
        Mc.forge("gbook");
    }

    @AfterEach
    void forgetTheReload() {
        ModelLoaderRegistryAccessor.coarctatio$loaders().removeAll(registered);
        known.forEach(ModelLocations.ALL_KNOWN::remove);
        Mixins.set(CoarctatioConfig.class, "instance", null);
        Mixins.set(DynamicModels.class, "unbaked", null);
        Mixins.set(DynamicModels.class, "baked", null);
        Mixins.set(DynamicModels.class, "reloadingAtlas", null);
        UnbakedModelProvider.textureCapture = null;
        DynamicModels.rememberWeakSprites(Set.of());
        // The scan a reload started is drained so the next test starts from nothing
        TextureDiscovery.take();
    }

    private static IModel model(String... textures) {
        IModel model = mock(IModel.class);
        List<ResourceLocation> locations = new ArrayList<>();
        for (String texture : textures) {
            locations.add(new ResourceLocation(texture));
        }
        when(model.getTextures()).thenReturn(locations);
        when(model.bake(any(), any(), any())).thenReturn(mock(IBakedModel.class));
        return model;
    }

    // A custom loader answering from the map for the locations it holds, and failing for anything else it accepts
    private ICustomModelLoader register(Map<ResourceLocation, IModel> models, String... failingNamespaces) throws Exception {
        Set<String> failing = Set.of(failingNamespaces);
        ICustomModelLoader loader = mock(ICustomModelLoader.class);
        when(loader.accepts(any())).thenAnswer(invocation -> {
            ResourceLocation location = invocation.getArgument(0);
            return models.containsKey(location) || failing.contains(location.getNamespace()) || location.getPath().contains("broken");
        });
        when(loader.loadModel(any())).thenAnswer(invocation -> {
            IModel model = models.get(invocation.<ResourceLocation>getArgument(0));
            if (model == null) {
                throw new IllegalStateException("no model at " + invocation.getArgument(0));
            }
            return model;
        });
        ModelLoaderRegistry.registerLoader(loader);
        registered.add(loader);
        return loader;
    }

    // A pack whose index the existence cache already built
    private static IResourcePack indexed(String... paths) {
        IResourcePack pack = Mc.mock(IResourcePack.class, IndexedResourcePack.class);
        when(((IndexedResourcePack) pack).coarctatio$indexedPaths()).thenReturn(Set.of(paths));
        return pack;
    }

    // The resource manager a reload is handed: its packs are listed, and every file read is not found
    private static SimpleReloadableResourceManager resources(IResourcePack pack) throws Exception {
        FallbackResourceManager domain = Mc.mock(FallbackResourceManager.class);
        when(((FallbackResourceManagerAccessor) domain).coarctatio$packs()).thenReturn(new ArrayList<>(List.of(pack)));
        SimpleReloadableResourceManager manager = Mc.mock(SimpleReloadableResourceManager.class);
        when(((SimpleReloadableResourceManagerAccessor) manager).coarctatio$domainManagers()).thenReturn(Map.of("tconstruct", domain));
        when(manager.getResource(any())).thenThrow(new FileNotFoundException("not in this pack"));
        return manager;
    }

    @Test
    void aReloadInstallsTheProvidersAndLoadsOnlyWhatMustBeThereUpFront() throws Exception {
        CustomTextureCreator creator = new CustomTextureCreator();
        assertTrue(DynamicModels.deferListener(creator));
        assertFalse(DynamicModels.deferListener(mock(IResourceManagerReloadListener.class)));

        ModelResourceLocation gear = new ModelResourceLocation("eagermod:gear", "inventory");
        ModelResourceLocation broken = new ModelResourceLocation("eagermod:broken", "inventory");
        ModelResourceLocation flaky = new ModelResourceLocation("eagermod:flaky", "normal");
        ModelLocations.addItemVariantFile(gear, new ResourceLocation("eagermod:item/gear"));
        ModelLocations.addItemVariantFile(broken, new ResourceLocation("eagermod:item/broken"));
        ModelLocations.VARIANTS_BY_BLOCKSTATE.put(new ResourceLocation("eagermod:flaky"), new ObjectOpenHashSet<>(List.of(flaky)));

        Map<ResourceLocation, IModel> models = new HashMap<>();
        IModel gearModel = model("eagermod:items/gear");
        IBakedModel gearBaked = mock(IBakedModel.class);
        // Baking asks for a sprite the scan never saw, which is reported
        when(gearModel.bake(any(), any(), any())).thenAnswer(invocation -> {
            invocation.<Function<ResourceLocation, TextureAtlasSprite>>getArgument(2).apply(new ResourceLocation("eagermod:items/unscanned"));
            return gearBaked;
        });
        models.put(gear, gearModel);
        // A model whose parent chain only resolved the first time still loads; its textures are just not known
        IModel flakyModel = model();
        when(flakyModel.getTextures()).thenReturn(List.of()).thenThrow(new IllegalStateException("parent gone"));
        models.put(flaky, flakyModel);
        models.put(new ResourceLocation("tconstruct:models/item/parts/pick.tmat"), model("tconstruct:items/pick"));
        models.put(new ResourceLocation("tconstruct:models/item/parts/pick_head.tcon"), model("tconstruct:items/pick_head"));
        models.put(new ResourceLocation("gbook:models/block/custom/book"), model("gbook:blocks/book"));
        IModel missing = model();
        IBakedModel missingBaked = mock(IBakedModel.class);
        when(missing.bake(any(), any(), any())).thenReturn(missingBaked);
        models.put(UnbakedModelProvider.MISSING, missing);
        register(models, "eagermod");

        IResourcePack pack = indexed(
                "assets/tconstruct/models/item/parts/pick.tmat.json",
                "assets/tconstruct/models/item/parts/pick_head.tcon.json",
                "assets/tconstruct/models/item/parts/broken.tmat.json",
                // Early-looking, but not shaped like a model location
                "assets/tconstruct/loose.tcon.json",
                "assets/tconstruct/textures/items/pick.png",
                "a.json");
        SimpleReloadableResourceManager resources = resources(pack);

        TextureMap atlas = Mc.mock(TextureMap.class);
        TextureMap populated = Mc.mock(TextureMap.class);
        doAnswer(invocation -> {
            invocation.<ITextureMapPopulator>getArgument(1).registerSprites(populated);
            return null;
        }).when(atlas).loadSprites(any(), any());
        BlockModelShapes shapes = Mc.mock(BlockModelShapes.class);
        when(shapes.getBlockStateMapper()).thenReturn(new BlockStateMapper());

        ModelManagerDynamicMixin manager = Mixins.instance(ModelManagerDynamicMixin.class);
        Mixins.set(manager, "texMap", atlas);
        Mixins.set(manager, "modelProvider", shapes);
        try {
            manager.onResourceManagerReload(resources);
        } finally {
            ModelLocations.ITEM_VARIANT_FILES.remove(gear);
            ModelLocations.ITEM_VARIANT_FILES.remove(broken);
            ModelLocations.VARIANTS_BY_BLOCKSTATE.remove(new ResourceLocation("eagermod:flaky"));
        }

        // The listener kept aside ran as part of the reload
        assertEquals(1, creator.reloads);
        BakedModelProvider baked = DynamicModels.baked();
        UnbakedModelProvider unbaked = DynamicModels.unbaked();
        assertSame(baked, Mixins.get(manager, "modelRegistry"));
        assertSame(missingBaked, Mixins.get(manager, "defaultModel"));
        // Eager namespaces are baked by the end of the reload, and TConstruct's material models are pinned
        assertSame(gearBaked, baked.getIfBaked(gear));
        assertTrue(unbaked.getKeys().contains(new ResourceLocation("tconstruct:item/parts/pick.tmat")));
        assertFalse(unbaked.getKeys().contains(new ResourceLocation("tconstruct:item/parts/pick_head.tcon")));
        // Every texture those models use, and the built-in ones, went to the atlas
        verify(populated).registerSprite(new ResourceLocation("eagermod:items/gear"));
        verify(populated).registerSprite(new ResourceLocation("tconstruct:items/pick"));
        verify(populated).registerSprite(new ResourceLocation("tconstruct:items/pick_head"));
        verify(populated).registerSprite(new ResourceLocation("gbook:blocks/book"));
        Set<ResourceLocation> builtin = Mixins.get(ModelBakery.class, "LOCATIONS_BUILTIN_TEXTURES");
        verify(populated).registerSprite(builtin.iterator().next());
        verify(shapes).reloadModels();
        assertEquals(2, DynamicModels.statistics().size());
    }

    @Test
    void theStatisticsAreEmptyBeforeAnyReload() {
        assertTrue(DynamicModels.statistics().isEmpty());
    }

    @Test
    void scannedAndFluidSpritesAreRegisteredWeaklyOnTheAtlasBeingReloaded() throws Exception {
        TextureMap atlas = Mc.mock(TextureMap.class, WeakSpriteTextureMap.class);
        WeakSpriteTextureMap weak = (WeakSpriteTextureMap) atlas;
        // Any other atlas is left alone
        DynamicModels.registerDiscoveredSprites(atlas);
        verify(weak, never()).coarctatio$registerSpriteWeak(any());

        Mixins.set(DynamicModels.class, "reloadingAtlas", atlas);
        // The json crawl waits for the location tables, which a reload would have built
        ModelLocations.READY.complete(null);
        TextureDiscovery.start(resources(indexed("assets/examplemod/textures/blocks/ore.png")), List.of(indexed("assets/examplemod/textures/blocks/ore.png")));
        // Lava's still texture is not in the packs, and the atlas refuses water's flowing one
        IResourceManager clientResources = client.getResourceManager();
        when(clientResources.getResource(new ResourceLocation("minecraft:textures/blocks/lava_still.png"))).thenThrow(new FileNotFoundException());
        doThrow(new IllegalArgumentException("not a sprite")).when(weak).coarctatio$registerSpriteWeak(new ResourceLocation("minecraft:blocks/water_flow"));

        DynamicModels.registerDiscoveredSprites(atlas);
        verify(weak).coarctatio$registerSpriteWeak(new ResourceLocation("examplemod:blocks/ore"));
        verify(weak).coarctatio$registerSpriteWeak(new ResourceLocation("minecraft:blocks/water_still"));
        verify(weak).coarctatio$registerSpriteWeak(new ResourceLocation("minecraft:blocks/lava_flow"));
        verify(weak, never()).coarctatio$registerSpriteWeak(new ResourceLocation("minecraft:blocks/lava_still"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void anAtlasThatDoesNotFitDropsUnreferencedSpritesThenTheLargest() throws Exception {
        // Loading a model checks it against Forge's missing model, which needs a loader in place
        ModelLoader forge = Mc.mock(ModelLoader.class);
        Mixins.stub(forge, "getMissingModel", invocation -> mock(IModel.class));
        Object vanillaLoader = Statics.get(Class.forName("net.minecraftforge.client.model.ModelLoader$VanillaLoader"), "INSTANCE");
        Mixins.set(vanillaLoader, "loader", forge);

        ModelResourceLocation thing = new ModelResourceLocation("examplemod:thing", "normal");
        ModelResourceLocation broken = new ModelResourceLocation("examplemod:broken", "normal");
        IModel thingModel = model("examplemod:blocks/used");
        when(thingModel.getDependencies()).thenReturn(List.of(new ResourceLocation("examplemod:block/parent"), thing));
        Map<ResourceLocation, IModel> models = new HashMap<>();
        models.put(thing, thingModel);
        models.put(new ResourceLocation("examplemod:models/block/parent"), model("examplemod:blocks/parent_used"));
        ICustomModelLoader loader = mock(ICustomModelLoader.class);
        when(loader.accepts(any())).thenAnswer(invocation -> models.containsKey(invocation.<ResourceLocation>getArgument(0))
                || invocation.<ResourceLocation>getArgument(0).getPath().contains("broken"));
        when(loader.loadModel(any())).thenAnswer(invocation -> {
            IModel model = models.get(invocation.<ResourceLocation>getArgument(0));
            if (model == null) {
                throw new IllegalStateException("broken");
            }
            return model;
        });
        Mixins.set(DynamicModels.class, "unbaked", new UnbakedModelProvider(new java.util.LinkedHashSet<>(List.of(loader))));
        for (ModelResourceLocation location : List.of(thing, broken)) {
            if (ModelLocations.ALL_KNOWN.add(location)) {
                known.add(location);
            }
        }

        Stitcher stitcher = Mc.mock(Stitcher.class, DroppingStitcher.class);
        DroppingStitcher dropping = (DroppingStitcher) stitcher;
        doThrow(new StitcherException(null, "too big")).doThrow(new StitcherException(null, "still too big"))
                .doAnswer(invocation -> null).when(stitcher).doStitch();
        when(dropping.coarctatio$dropLargestSprite()).thenReturn(true);
        DynamicModels.rememberWeakSprites(Set.of("examplemod:blocks/unused"));

        DynamicModels.stitchWithFallback(stitcher);
        // The first retry keeps only sprites some model references, gathered by loading every known model
        ArgumentCaptor<Set<String>> referenced = ArgumentCaptor.forClass(Set.class);
        verify(dropping).coarctatio$retainSprites(referenced.capture());
        assertTrue(referenced.getValue().containsAll(Set.of("examplemod:blocks/used", "examplemod:blocks/parent_used")));
        // The second drops the largest, and the third fits
        verify(dropping).coarctatio$dropLargestSprite();
        assertNull(UnbakedModelProvider.textureCapture);
        assertFalse(DynamicModels.isDroppable("examplemod:blocks/unused", Set.of()));

        // With nothing left to drop, the failure stands
        Stitcher full = Mc.mock(Stitcher.class, DroppingStitcher.class);
        doThrow(new StitcherException(null, "too big")).when(full).doStitch();
        assertThrows(StitcherException.class, () -> DynamicModels.stitchWithFallback(full));
    }
}
