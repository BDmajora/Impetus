package com.bdmajora.coarctatio.mixin.client.model;

import com.bdmajora.coarctatio.state.BakeStateReleasable;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.ShadowStubs;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.client.renderer.block.model.ModelBlockDefinition;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.IModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

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
}
