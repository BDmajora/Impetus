package com.bdmajora.coarctatio.mixin.client.model;

import com.bdmajora.coarctatio.client.model.ModelPrefetch;
import com.bdmajora.coarctatio.mixin.client.model.dynamic.FallbackResourceManagerAccessor;
import com.bdmajora.coarctatio.mixin.client.model.dynamic.SimpleReloadableResourceManagerAccessor;
import com.bdmajora.coarctatio.mixin.client.resources.FileResourcePackMixin;
import com.bdmajora.coarctatio.state.BakeStateReleasable;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.ShadowStubs;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.renderer.BlockModelShapes;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelBlock;
import net.minecraft.client.renderer.block.model.ModelBlockDefinition;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.FallbackResourceManager;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.SimpleReloadableResourceManager;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.IRegistry;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.animation.ModelBlockAnimation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModelLoaderMixinsTest {
    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
        // ModelLoaderRegistry registers a reload listener as it initialises
        net.minecraft.client.Minecraft client = Mc.client();
        Mockito.when(client.getResourceManager())
                .thenReturn(mock(net.minecraft.client.resources.IReloadableResourceManager.class));
    }

    @AfterEach
    void forgetShadowAnswers() {
        ShadowStubs.clear();
        ModelPrefetch.clear();
    }

    @Test
    void theBakerysCollectionsAreSwappedForCompactOnes() {
        ModelBakeryMixin bakery = Mixins.instance(ModelBakeryMixin.class);
        Mixins.set(bakery, "sprites", new HashMap<ResourceLocation, net.minecraft.client.renderer.texture.TextureAtlasSprite>());
        Mixins.set(bakery, "blockDefinitions", new HashMap<ResourceLocation, ModelBlockDefinition>());
        Mixins.set(bakery, "variantNames", new HashMap<Item, List<String>>());

        Mixins.call(bakery, "coarctatio$compactModelGraph", Mixins.ci());
        assertInstanceOf(Object2ObjectOpenHashMap.class, Mixins.get(bakery, "sprites"));
        assertInstanceOf(Object2ObjectOpenHashMap.class, Mixins.get(bakery, "blockDefinitions"));
        // Items are canonical, so their variant names are keyed by identity
        assertInstanceOf(Reference2ObjectOpenHashMap.class, Mixins.get(bakery, "variantNames"));

        // The multipart map is private to the bakery, so it is cleared from here
        Map<ModelBlockDefinition, java.util.Collection<ModelResourceLocation>> multipart = new HashMap<>();
        multipart.put(mock(ModelBlockDefinition.class), new ArrayList<>());
        Mixins.set(bakery, "multipartVariantMap", multipart);
        assertEquals(1, bakery.coarctatio$releaseBakeryState());
        assertTrue(multipart.isEmpty());
    }

    @Test
    void theLoadersOwnMapsAreSwappedTheSameWay() {
        ModelLoaderMixin loader = Mixins.instance(ModelLoaderMixin.class);
        Mixins.set(loader, "stateModels", new HashMap<ModelResourceLocation, IModel>());
        Mixins.set(loader, "multipartDefinitions", new HashMap<ModelResourceLocation, ModelBlockDefinition>());
        Mixins.set(loader, "multipartModels", new HashMap<ModelBlockDefinition, IModel>());
        Mixins.set(loader, "missingVariants", new HashSet<ModelResourceLocation>());
        Mixins.set(loader, "loadingExceptions", new HashMap<ResourceLocation, Exception>());

        Mixins.call(loader, "coarctatio$compactLoaderMaps", Mixins.ci());
        assertInstanceOf(Object2ObjectOpenHashMap.class, Mixins.get(loader, "stateModels"));
        assertInstanceOf(Object2ObjectOpenHashMap.class, Mixins.get(loader, "multipartDefinitions"));
        assertInstanceOf(Object2ObjectOpenHashMap.class, Mixins.get(loader, "multipartModels"));
        assertInstanceOf(ObjectOpenHashSet.class, Mixins.get(loader, "missingVariants"));
        assertInstanceOf(Object2ObjectOpenHashMap.class, Mixins.get(loader, "loadingExceptions"));
    }

    @Test
    void theBakeStateIsFreedOnceTheBakeEventHasFired() {
        ModelLoaderCleanupMixin loader = Mixins.instanceWith(ModelLoaderCleanupMixin.class, BakeStateReleasable.class);
        Map<ModelResourceLocation, IModel> stateModels = new HashMap<>();
        stateModels.put(new ModelResourceLocation("minecraft:stone", "normal"), mock(IModel.class));
        Map<ResourceLocation, Exception> errors = new HashMap<>();
        errors.put(new ResourceLocation("minecraft:broken"), new RuntimeException());
        Map<ModelResourceLocation, ModelBlockDefinition> definitions = new HashMap<>();
        definitions.put(new ModelResourceLocation("minecraft:fence", "multipart"), mock(ModelBlockDefinition.class));
        Map<ModelBlockDefinition, IModel> multipartModels = new HashMap<>();
        multipartModels.put(mock(ModelBlockDefinition.class), mock(IModel.class));
        Mixins.set(loader, "stateModels", stateModels);
        Mixins.set(loader, "loadingExceptions", errors);
        Mixins.set(loader, "multipartDefinitions", definitions);
        Mixins.set(loader, "multipartModels", multipartModels);
        Mockito.doReturn(2).when((BakeStateReleasable) loader).coarctatio$releaseBakeryState();

        Mixins.call(loader, "coarctatio$releaseBakeState", null, Mixins.ci());
        // Mods read these during ModelBakeEvent, which has already fired by now
        assertTrue(stateModels.isEmpty());
        assertTrue(errors.isEmpty());
        assertTrue(definitions.isEmpty());
        assertTrue(multipartModels.isEmpty());
    }

    @Test
    void aModelThatFailedToLoadGetsThePlainMissingModel() {
        IModel plain = mock(IModel.class);
        ShadowStubs.on(null, "getMissingModel", args -> plain);

        CallbackInfoReturnable<IModel> cir = Mixins.cir();
        Mixins.call(ModelLoaderRegistryMissingMixin.class, "coarctatio$plainMissingModel",
                new ResourceLocation("minecraft:broken"), new RuntimeException("no such model"), cir);
        assertTrue(cir.isCancelled());
        assertSame(plain, cir.getReturnValue());

        // The shadow's own body is a placeholder for the real static on the target class
        assertThrows(AssertionError.class, ModelLoaderRegistryMissingMixin::getMissingModel);
        assertNotNull(Mixins.instance(ModelLoaderRegistryMissingMixin.class));
    }

    @Test
    void theRegistryIsBuiltWithThePacksModelsAlreadyReadAndNothingIsKeptAfter() throws Exception {
        ResourceLocation teacup = new ResourceLocation("minecraft", "models/item/teacup");
        FileResourcePackMixin pack = Mixins.instance(FileResourcePackMixin.class);
        Mixins.set(pack, "coarctatio$entries", Set.of("assets/minecraft/models/item/teacup.json"));
        FallbackResourceManager domain = Mc.mock(FallbackResourceManager.class);
        when(((FallbackResourceManagerAccessor) domain).coarctatio$packs()).thenReturn(new ArrayList<>(Arrays.asList((IResourcePack) pack)));
        Map<String, FallbackResourceManager> domains = new LinkedHashMap<>();
        domains.put("minecraft", domain);
        SimpleReloadableResourceManager manager = Mc.mock(SimpleReloadableResourceManager.class);
        when(((SimpleReloadableResourceManagerAccessor) manager).coarctatio$domainManagers()).thenReturn(domains);
        // The model file reads as JSON; the armature beside it does not exist
        when(manager.getResource(any())).thenAnswer(invocation -> {
            ResourceLocation location = invocation.getArgument(0);
            if (!location.getPath().startsWith("models/")) {
                throw new FileNotFoundException(location.toString());
            }
            IResource resource = mock(IResource.class);
            when(resource.getInputStream()).thenReturn(new ByteArrayInputStream("{\"parent\":\"item/generated\"}".getBytes(StandardCharsets.UTF_8)));
            return resource;
        });
        ModelLoaderPrefetchMixin loader = Mixins.instance(ModelLoaderPrefetchMixin.class, manager, Mc.mock(TextureMap.class), Mc.mock(BlockModelShapes.class));

        @SuppressWarnings("unchecked")
        IRegistry<ModelResourceLocation, IBakedModel> registry = mock(IRegistry.class);
        ModelBlock[] seen = new ModelBlock[1];
        Operation<IRegistry<ModelResourceLocation, IBakedModel>> original = args -> {
            // The loader's passes find the model already read through the bakery's own loadModel
            seen[0] = ModelPrefetch.takeModel(teacup);
            return registry;
        };
        assertSame(registry, Mixins.call(loader, "coarctatio$prefetchModels", original));
        assertNotNull(seen[0]);
        assertEquals("minecraft:models/item/teacup", seen[0].name);
        // What nothing asked for is dropped once the registry is built
        assertNull(ModelPrefetch.takeAnimation(new ResourceLocation("minecraft", "armatures/item/teacup.json")));

        // And dropped the same way when building it fails
        Operation<IRegistry<ModelResourceLocation, IBakedModel>> failing = args -> {
            assertNotNull(ModelPrefetch.takeAnimation(new ResourceLocation("minecraft", "armatures/item/teacup.json")));
            throw new IllegalStateException("bake failed");
        };
        assertThrows(IllegalStateException.class, () -> Mixins.call(loader, "coarctatio$prefetchModels", failing));
        assertNull(ModelPrefetch.takeModel(teacup));
    }

    @Test
    void theBakeryAndTheArmatureLookupAreAnsweredFromThePrefetch() {
        ResourceLocation teacup = new ResourceLocation("minecraft", "models/item/teacup");
        ResourceLocation armature = new ResourceLocation("minecraft", "armatures/item/teacup.json");
        ModelBakeryPrefetchMixin bakery = Mixins.instance(ModelBakeryPrefetchMixin.class);

        // Nothing prefetched: both take the usual path
        CallbackInfoReturnable<ModelBlock> modelMiss = Mixins.cir();
        Mixins.call(bakery, "coarctatio$prefetchedModel", teacup, modelMiss);
        assertFalse(modelMiss.isCancelled());
        CallbackInfoReturnable<ModelBlockAnimation> animationMiss = Mixins.cir();
        Mixins.call(ModelBlockAnimationPrefetchMixin.class, "coarctatio$prefetchedAnimation", mock(IResourceManager.class), armature, animationMiss);
        assertFalse(animationMiss.isCancelled());

        ModelBlock model = ModelBlock.deserialize("{}");
        ModelBlockAnimation animation = mock(ModelBlockAnimation.class);
        Mixins.<Map<ResourceLocation, ModelBlock>>get(ModelPrefetch.class, "MODEL_BLOCKS").put(teacup, model);
        Mixins.<Map<ResourceLocation, ModelBlockAnimation>>get(ModelPrefetch.class, "ANIMATIONS").put(armature, animation);
        CallbackInfoReturnable<ModelBlock> modelHit = Mixins.cir();
        Mixins.call(bakery, "coarctatio$prefetchedModel", teacup, modelHit);
        assertTrue(modelHit.isCancelled());
        assertSame(model, modelHit.getReturnValue());
        CallbackInfoReturnable<ModelBlockAnimation> animationHit = Mixins.cir();
        Mixins.call(ModelBlockAnimationPrefetchMixin.class, "coarctatio$prefetchedAnimation", mock(IResourceManager.class), armature, animationHit);
        assertTrue(animationHit.isCancelled());
        assertSame(animation, animationHit.getReturnValue());
        assertNotNull(Mixins.instance(ModelBlockAnimationPrefetchMixin.class));
    }
}
