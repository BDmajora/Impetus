package com.bdmajora.coarctatio.mixin.client.model.dynamic;

import com.bdmajora.coarctatio.client.model.dynamic.BakedModelProvider;
import com.bdmajora.coarctatio.client.model.dynamic.DefinitionLoader;
import com.bdmajora.coarctatio.client.model.dynamic.DynamicModels;
import com.bdmajora.coarctatio.client.model.dynamic.ModelLocations;
import com.bdmajora.coarctatio.client.model.dynamic.UnbakedModelProvider;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelBlock;
import net.minecraft.client.renderer.block.model.ModelBlockDefinition;
import net.minecraft.client.renderer.block.model.ModelManager;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.statemap.BlockStateMapper;
import net.minecraft.client.renderer.texture.Stitcher;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.common.model.TRSRTransformation;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.registries.IRegistryDelegate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DynamicModelMixinsTest {
    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
        // ModelLoaderRegistry registers reload listeners against the client's manager as it initialises
        Mc.client();
    }

    @AfterEach
    void forgetProviders() {
        Mixins.set(DynamicModels.class, "unbaked", null);
        Mixins.set(DynamicModels.class, "baked", null);
        UnbakedModelProvider.textureCapture = null;
    }

    private static UnbakedModelProvider installUnbaked() {
        UnbakedModelProvider provider = new UnbakedModelProvider(new LinkedHashSet<>());
        Mixins.set(DynamicModels.class, "unbaked", provider);
        return provider;
    }

    @Test
    void aStateHoldsItsModelSoftly() {
        BlockStateBaseModelCacheMixin state = Mixins.instance(BlockStateBaseModelCacheMixin.class);
        assertNull(state.coarctatio$cachedModel());
        IBakedModel model = mock(IBakedModel.class);
        state.coarctatio$cacheModel(model);
        assertSame(model, state.coarctatio$cachedModel());
        // Clearing drops the reference itself, not just what it points at
        state.coarctatio$cacheModel(null);
        assertNull(state.coarctatio$cachedModel());
    }

    @Test
    void aStateRemembersItsModelOnceDrawn() {
        BlockModelShapesDynamicMixin shapes = Mixins.instance(BlockModelShapesDynamicMixin.class);
        ModelManager manager = mock(ModelManager.class);
        BlockStateMapper mapper = mock(BlockStateMapper.class);
        Mixins.set(shapes, "modelManager", manager);
        Mixins.set(shapes, "blockStateMapper", mapper);
        IBakedModel missing = mock(IBakedModel.class);
        when(manager.getMissingModel()).thenReturn(missing);

        BlockStateBaseModelCacheMixin holder = Mixins.instance(BlockStateBaseModelCacheMixin.class);
        IBlockState state = (IBlockState) holder;
        doReturn(Blocks.STONE).when(state).getBlock();
        ModelResourceLocation location = new ModelResourceLocation("minecraft:stone", "normal");
        when(mapper.getVariants(Blocks.STONE)).thenReturn(Map.of(state, location));
        IBakedModel stone = mock(IBakedModel.class);
        when(manager.getModel(location)).thenReturn(stone);

        assertSame(location, shapes.coarctatio$locationForState(state));
        assertSame(stone, shapes.getModelForState(state));
        // The state answers from then on, without asking the manager again
        assertSame(stone, shapes.getModelForState(state));
        verify(manager, times(1)).getModel(location);

        // A state that cannot hold a model is looked up each time, and one with no model answers the missing one
        IBlockState dirt = Blocks.DIRT.getDefaultState();
        when(mapper.getVariants(Blocks.DIRT)).thenReturn(Map.of(dirt, new ModelResourceLocation("minecraft:dirt", "normal")));
        assertSame(missing, shapes.getModelForState(dirt));
        // Mods pass null states, which vanilla's table answered with the missing model
        assertSame(missing, shapes.getModelForState(null));
    }

    @Test
    void aReloadTurnsTheStateTableIntoAViewOverTheRegistry() {
        BlockModelShapesDynamicMixin shapes = Mixins.instance(BlockModelShapesDynamicMixin.class);
        Mixins.set(shapes, "blockStateMapper", mock(BlockStateMapper.class));
        // Before any dynamic reload there is nothing to view, so the field is left alone
        shapes.reloadModels();
        assertNull(Mixins.get(shapes, "bakedModelStore"));

        BakedModelProvider baked = new BakedModelProvider(installUnbaked());
        Mixins.set(DynamicModels.class, "baked", baked);
        shapes.reloadModels();
        assertSame(baked.stateStore(), Mixins.get(shapes, "bakedModelStore"));
    }

    @Test
    void theMesherKeepsLocationsAndBakesOnFirstLookup() {
        ModelManager manager = mock(ModelManager.class);
        ItemModelMesherForgeDynamicMixin mesher = Mixins.instance(ItemModelMesherForgeDynamicMixin.class, manager);
        Map<IRegistryDelegate<Item>, Int2ObjectMap<ModelResourceLocation>> locations = new IdentityHashMap<>();
        Mixins.set(mesher, "locations", locations);
        ModelResourceLocation diamond = new ModelResourceLocation("minecraft:diamond", "inventory");
        IBakedModel model = mock(IBakedModel.class);
        when(manager.getModel(diamond)).thenReturn(model);

        // Registering only records the location; fetching the model would bake it
        mesher.register(Items.DIAMOND, 0, diamond);
        mesher.register(Items.DIAMOND, 1, diamond);
        verify(manager, never()).getModel(any());
        assertEquals(2, locations.get(Items.DIAMOND.delegate).size());

        assertSame(model, Mixins.call(mesher, "getItemModel", Items.DIAMOND, 0));
        assertSame(model, Mixins.call(mesher, "getItemModel", Items.DIAMOND, 0));
        verify(manager, times(1)).getModel(diamond);
        // An unregistered meta or item answers null, as vanilla's mesher did
        assertNull(Mixins.call(mesher, "getItemModel", Items.DIAMOND, 7));
        assertNull(Mixins.call(mesher, "getItemModel", Items.APPLE, 0));

        // A rebuild forgets the cached models, so the next lookup asks the registry again
        mesher.rebuildCache();
        assertSame(model, Mixins.call(mesher, "getItemModel", Items.DIAMOND, 0));
        verify(manager, times(2)).getModel(diamond);
    }

    @Test
    void theMissingTextureReportIsSilenced() {
        MissingTextureLogMixin log = Mixins.instance(MissingTextureLogMixin.class);
        CallbackInfo ci = Mixins.ci();
        Mixins.call(log, "coarctatio$silenceScanNoise", ci);
        assertTrue(ci.isCancelled());
    }

    @Test
    void theBakeEventRemembersWhichModItIsRunningFor() {
        // ModelBakeEvent is spliced, so the mixin gets a generated subclass rather than a Mockito one
        ModelBakeEventContextMixin event = Mixins.concrete(ModelBakeEventContextMixin.class);
        ModContainer mod = mock(ModContainer.class);
        event.setModContainer(mod);
        assertSame(mod, event.coarctatio$lastMod());
        event.setModContainer(null);
        assertNull(event.coarctatio$lastMod());
    }

    @Test
    void blockstateDefinitionsComeFromTheSoftCache() throws IOException {
        IResourceManager resources = Minecraft.getMinecraft().getResourceManager();
        ResourceLocation broken = new ResourceLocation("examplemod", "blockstates/broken.json");
        when(resources.getAllResources(broken)).thenThrow(new IOException("unreadable"));

        ModelLoaderDefinitionMixin loader = Mixins.instance(ModelLoaderDefinitionMixin.class);
        // Every variant of a block shares one definition, keyed by the plain location
        ModelBlockDefinition machine = loader.coarctatio$definition(new ModelResourceLocation("examplemod:machine", "normal"));
        assertSame(machine, loader.coarctatio$definition(new ModelResourceLocation("examplemod:machine", "facing=north")));
        // A file that cannot be read is an empty definition, on which the variant load then fails
        assertFalse(loader.coarctatio$definition(new ResourceLocation("examplemod:broken")).hasMultipartData());

        CallbackInfoReturnable<ModelBlockDefinition> cir = Mixins.cir();
        Mixins.call(loader, "coarctatio$cachedDefinition", new ResourceLocation("examplemod:machine"), cir);
        assertSame(machine, cir.getReturnValue());
    }

    @Test
    void modelRequestsGoThroughTheDynamicProvider() throws Exception {
        assertNotNull(Mixins.instance(ModelLoaderRegistryDynamicMixin.class));
        ResourceLocation location = new ResourceLocation("examplemod:block/machine");
        // Asked before the first reload there is no provider, which Forge reports as a loader failure
        assertThrows(ModelLoaderRegistry.LoaderException.class, () -> ModelLoaderRegistryDynamicMixin.getModel(location));

        IModel model = mock(IModel.class);
        installUnbaked().putObject(location, model);
        assertSame(model, ModelLoaderRegistryDynamicMixin.getModel(location));
    }

    @Test
    void theStaticAccessorBodiesAreMixinPlaceholders() {
        assertThrows(AssertionError.class, ModelLoaderRegistryAccessor::coarctatio$loaders);
        assertThrows(AssertionError.class, ModelLoaderRegistryAccessor::coarctatio$cache);
        assertThrows(AssertionError.class, ModelLoaderRegistryAccessor::coarctatio$textures);
        assertThrows(AssertionError.class, ModelBakeryAccessor::coarctatio$builtinTextures);
    }

    @Test
    void theModelManagerTakesTheRegistryAndDefaultTheReloadHandsIt() {
        ModelManagerDynamicMixin manager = Mixins.instance(ModelManagerDynamicMixin.class);
        BakedModelProvider registry = new BakedModelProvider(installUnbaked());
        IBakedModel missing = mock(IBakedModel.class);
        manager.coarctatio$setModelRegistry(registry);
        manager.coarctatio$setDefaultModel(missing);
        assertSame(registry, Mixins.get(manager, "modelRegistry"));
        assertSame(missing, Mixins.get(manager, "defaultModel"));
    }

    @Test
    void onlyTheTextureCreatorIsKeptAsideFromTheReloadOrder() {
        ReloadListenerDeferralMixin resources = Mixins.concrete(ReloadListenerDeferralMixin.class);
        CallbackInfo ordinary = Mixins.ci();
        Mixins.call(resources, "coarctatio$deferListener", mock(IResourceManagerReloadListener.class), ordinary);
        assertFalse(ordinary.isCancelled());

        CallbackInfo creator = Mixins.ci();
        Mixins.call(resources, "coarctatio$deferListener", new slimeknights.tconstruct.library.client.CustomTextureCreator(), creator);
        assertTrue(creator.isCancelled());
    }

    @Test
    void theItemPrebakeRestartsWithEachItemReload() {
        RenderItemPrebakeMixin renderItem = Mixins.instance(RenderItemPrebakeMixin.class);
        // With no dynamic registry yet the restart has nothing to bake
        Mixins.call(renderItem, "coarctatio$prebakeItems", mock(IResourceManager.class), Mixins.ci());
        assertNull(Mixins.get(com.bdmajora.coarctatio.client.model.dynamic.ItemModelPrebake.class, "running"));
    }

    private static Stitcher.Holder holder(String name, int size) {
        TextureAtlasSprite sprite = Mc.uninitialized(TextureAtlasSprite.class);
        Mixins.set(sprite, "iconName", name);
        Mixins.set(sprite, "width", size);
        Mixins.set(sprite, "height", size);
        return new Stitcher.Holder(sprite, 0);
    }

    @Test
    void theStitcherDropsUnreferencedThenLargestSprites() {
        StitcherDropMixin stitcher = Mixins.instance(StitcherDropMixin.class);
        Set<Stitcher.Holder> holders = new LinkedHashSet<>();
        Stitcher.Holder small = holder("examplemod:blocks/small", 16);
        Stitcher.Holder large = holder("examplemod:blocks/large", 64);
        Stitcher.Holder scanned = holder("examplemod:blocks/scanned", 32);
        holders.add(small);
        holders.add(large);
        holders.add(scanned);
        List<Stitcher.Slot> slots = new ArrayList<>();
        slots.add(new Stitcher.Slot(0, 0, 16, 16));
        Mixins.set(stitcher, "setStitchHolders", holders);
        Mixins.set(stitcher, "stitchSlots", slots);
        Mixins.set(stitcher, "currentWidth", 256);
        Mixins.set(stitcher, "currentHeight", 256);

        // Only a weakly registered sprite no model references is dropped, and the failed attempt is reset
        DynamicModels.rememberWeakSprites(Set.of("examplemod:blocks/scanned", "examplemod:blocks/large"));
        stitcher.coarctatio$retainSprites(Set.of("examplemod:blocks/large"));
        assertEquals(Set.of(small, large), holders);
        assertTrue(slots.isEmpty());
        assertEquals(0, (int) Mixins.<Integer>get(stitcher, "currentWidth"));
        assertEquals(0, (int) Mixins.<Integer>get(stitcher, "currentHeight"));

        // Then the largest remaining, one at a time, until nothing is left
        assertTrue(stitcher.coarctatio$dropLargestSprite());
        assertEquals(Set.of(small), holders);
        assertTrue(stitcher.coarctatio$dropLargestSprite());
        assertFalse(stitcher.coarctatio$dropLargestSprite());
        DynamicModels.rememberWeakSprites(Set.of());
    }

    @Test
    void scannedSpritesAreWeakUntilAModRegistersTheSameName() {
        TextureMapWeakSpriteMixin atlas = Mixins.instance(TextureMapWeakSpriteMixin.class);
        Map<String, TextureAtlasSprite> registered = new HashMap<>();
        Mixins.set(atlas, "mapRegisteredSprites", registered);
        Mixins.set(atlas, "coarctatio$weakSprites", new ObjectOpenHashSet<String>());
        doAnswer(invocation -> {
            String name = invocation.getArgument(0).toString();
            TextureAtlasSprite sprite = Mc.uninitialized(TextureAtlasSprite.class);
            Mixins.set(sprite, "iconName", name);
            registered.put(name, sprite);
            return sprite;
        }).when(atlas).registerSprite(any());

        ResourceLocation ore = new ResourceLocation("examplemod:blocks/ore");
        atlas.coarctatio$registerSpriteWeak(ore);
        // A second weak registration, or one for a name already present, does nothing
        atlas.coarctatio$registerSpriteWeak(ore);
        verify(atlas, times(1)).registerSprite(ore);
        registered.put("examplemod:blocks/gem", registered.get("examplemod:blocks/ore"));
        atlas.coarctatio$registerSpriteWeak(new ResourceLocation("examplemod:blocks/gem"));
        verify(atlas, never()).registerSprite(new ResourceLocation("examplemod:blocks/gem"));

        // A mod registering the same name replaces the weak entry
        Mixins.call(atlas, "coarctatio$replaceWeak", ore, Mixins.cir());
        assertFalse(registered.containsKey("examplemod:blocks/ore"));
        // A name that was never weak is left alone
        Mixins.call(atlas, "coarctatio$replaceWeak", new ResourceLocation("examplemod:blocks/gem"), Mixins.cir());
        assertTrue(registered.containsKey("examplemod:blocks/gem"));

        // So does a mod setting an entry of its own, which names the sprite rather than its location
        atlas.coarctatio$registerSpriteWeak(ore);
        Mixins.call(atlas, "coarctatio$replaceWeakEntry", registered.get("examplemod:blocks/ore"), Mixins.<Boolean>cir());
        assertFalse(registered.containsKey("examplemod:blocks/ore"));

        // Once stitching starts the weak set is handed over, and only its names stay droppable
        atlas.coarctatio$registerSpriteWeak(ore);
        Mixins.call(atlas, "coarctatio$handOverWeakSprites", mock(IResourceManager.class), Mixins.ci());
        assertTrue(DynamicModels.isDroppable("examplemod:blocks/ore", Set.of()));
        assertFalse(DynamicModels.isDroppable("examplemod:blocks/gem", Set.of()));

        // Loading sprites starts a fresh weak set; this atlas is not the one reloading, so nothing is scanned into it
        Mixins.call(atlas, "coarctatio$registerScannedSprites", mock(IResourceManager.class),
                mock(net.minecraft.client.renderer.texture.ITextureMapPopulator.class), Mixins.ci());
        assertTrue(Mixins.<Set<String>>get(atlas, "coarctatio$weakSprites").isEmpty());

        // The stitch goes through the fallback, which on success forgets the droppable names
        Stitcher stitcher = mock(Stitcher.class);
        Mixins.call(atlas, "coarctatio$stitchWithFallback", stitcher);
        verify(stitcher).doStitch();
        assertFalse(DynamicModels.isDroppable("examplemod:blocks/ore", Set.of()));
    }

    @Test
    void forgesWrapperAliasesItsOverridesAndBakesDirectly() {
        VanillaModelWrapperDynamicMixin wrapper = Mixins.instance(VanillaModelWrapperDynamicMixin.class);
        ModelBlock model = ModelBlock.deserialize(new StringReader(
                "{\"overrides\":[{\"predicate\":{\"pulling\":1},\"model\":\"examplemod:item/bow_pulling\"}]}"));
        Mixins.set(wrapper, "model", model);

        // Nothing goes into the loader's stateModels, and no override is loaded while listing dependencies
        Map<Object, Object> stateModels = new HashMap<>();
        assertNull(Mixins.call(wrapper, "coarctatio$skipStateModelPut", stateModels, "key", "value"));
        assertTrue(stateModels.isEmpty());
        assertNull(Mixins.call(wrapper, "coarctatio$skipOverrideLoad", new ResourceLocation("examplemod:item/bow_pulling"), "error"));

        // The override's inventory variant resolves to the override's own file instead
        UnbakedModelProvider unbaked = installUnbaked();
        IModel pulling = mock(IModel.class);
        unbaked.putObject(new ResourceLocation("examplemod:item/bow_pulling"), pulling);
        Mixins.call(wrapper, "coarctatio$aliasOverrides", Mixins.cir());
        assertSame(pulling, unbaked.getObject(new ModelResourceLocation("examplemod:item/bow_pulling", "inventory")));

        // A bake skips Forge's short-lived cache and goes straight to bakeImpl
        IBakedModel baked = mock(IBakedModel.class);
        doReturn(baked).when(wrapper).bakeImpl(any(), any(), any());
        CallbackInfoReturnable<IBakedModel> cir = Mixins.cir();
        Function<ResourceLocation, TextureAtlasSprite> textures = location -> null;
        Mixins.call(wrapper, "coarctatio$bakeDirectly", TRSRTransformation.identity(), DefaultVertexFormats.ITEM, textures, cir);
        assertSame(baked, cir.getReturnValue());
    }

    // A model Forge's variant constructors can wrap: it keeps its own state and processes to itself
    private static IModel part() {
        IModel model = mock(IModel.class);
        when(model.uvlock(anyBoolean())).thenReturn(model);
        when(model.smoothLighting(anyBoolean())).thenReturn(model);
        when(model.gui3d(anyBoolean())).thenReturn(model);
        when(model.getDefaultState()).thenReturn(TRSRTransformation.identity());
        return model;
    }

    private static ModelBlockDefinition definition(String json, String name) {
        return ModelBlockDefinition.parseFromReader(new StringReader(json), new ResourceLocation(name));
    }

    @Test
    void variantsResolveAgainstTheDynamicDefinitions() throws Exception {
        VariantLoaderDynamicMixin variants = Mixins.instance(VariantLoaderDynamicMixin.class);
        ModelLoader loader = Mc.mock(ModelLoader.class, DefinitionLoader.class);
        Mixins.set(variants, "loader", loader);
        // The variants' model files resolve from Forge's cache, read reflectively as mixin-package calls are not rewritten
        Mixins.<Map<ResourceLocation, IModel>>get(ModelLoaderRegistry.class, "cache")
                .put(new ResourceLocation("examplemod:block/machine"), part());

        ModelResourceLocation normal = new ModelResourceLocation("examplemod:machine", "normal");
        when(((DefinitionLoader) (Object) loader).coarctatio$definition(normal))
                .thenReturn(definition("{\"variants\":{\"normal\":{\"model\":\"examplemod:machine\"}}}", "examplemod:machine"));
        assertNotNull(variants.loadModel(normal));

        // Forge's own blockstate format writes the default variant as the empty name
        ModelResourceLocation forge = new ModelResourceLocation("examplemod:forge_machine", "normal");
        when(((DefinitionLoader) (Object) loader).coarctatio$definition(forge))
                .thenReturn(definition("{\"variants\":{\"\":{\"model\":\"examplemod:machine\"}}}", "examplemod:forge_machine"));
        assertNotNull(variants.loadModel(forge));

        // A variant the definition lacks, with no multipart to fall back on, is missing
        ModelResourceLocation odd = new ModelResourceLocation("examplemod:machine", "odd");
        when(((DefinitionLoader) (Object) loader).coarctatio$definition(odd))
                .thenReturn(definition("{\"variants\":{\"normal\":{\"model\":\"examplemod:machine\"}}}", "examplemod:machine"));
        assertThrows(ModelBlockDefinition.MissingVariantException.class, () -> variants.loadModel(odd));

        // A multipart definition answers only for variants the state mapper produced for that block
        ModelResourceLocation fence = new ModelResourceLocation("examplemod:fence", "north=true");
        when(((DefinitionLoader) (Object) loader).coarctatio$definition(fence))
                .thenReturn(definition("{\"multipart\":[{\"apply\":{\"model\":\"examplemod:machine\"}}]}", "examplemod:fence"));
        assertThrows(Exception.class, () -> variants.loadModel(fence));
        ModelLocations.VARIANTS_BY_BLOCKSTATE.put(new ResourceLocation("examplemod:fence"), new ObjectOpenHashSet<>(List.of(fence)));
        try {
            assertNotNull(variants.loadModel(fence));
        } finally {
            ModelLocations.VARIANTS_BY_BLOCKSTATE.remove(new ResourceLocation("examplemod:fence"));
        }

        // A class that is not there is reported as it is looked up
        assertThrows(RuntimeException.class, () -> Mixins.call(VariantLoaderDynamicMixin.class, "constructor", "examplemod.Missing", new Class<?>[0]));
    }

    @Test
    void weightedModelsLoadTheirVariantsOnlyWhileTexturesAreGathered() {
        WeightedRandomModelDynamicMixin weighted = Mixins.instance(WeightedRandomModelDynamicMixin.class);
        ResourceLocation generated = new ResourceLocation("minecraft:builtin/generated");
        assertNull(Mixins.call(weighted, "coarctatio$deferDependencies", generated));

        // Forge seeds its cache with the generated item model on every clear
        ModelLoaderRegistry.clearModelCache(mock(IResourceManager.class));
        UnbakedModelProvider.textureCapture = new java.util.HashSet<>();
        assertNotNull(Mixins.call(weighted, "coarctatio$deferDependencies", generated));
    }
}
