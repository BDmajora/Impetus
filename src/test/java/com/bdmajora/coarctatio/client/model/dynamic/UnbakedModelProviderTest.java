package com.bdmajora.coarctatio.client.model.dynamic;

import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.ICustomModelLoader;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UnbakedModelProviderTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        Mc.bootstrap();
        Minecraft client = Mc.client();
        when(client.getResourceManager()).thenReturn(mock(IReloadableResourceManager.class));
        // The provider checks each loaded model against Forge's missing one, which needs an active loader
        net.minecraftforge.client.model.ModelLoader loader = Mc.mock(net.minecraftforge.client.model.ModelLoader.class);
        Mixins.stub(loader, "getMissingModel", invocation -> mock(IModel.class));
        Object vanillaLoader = com.bdmajora.testing.Statics.get(
                Class.forName("net.minecraftforge.client.model.ModelLoader$VanillaLoader"), "INSTANCE");
        Mixins.set(vanillaLoader, "loader", loader);
    }

    // A loader that answers for everything with the model the test hands it
    private static ICustomModelLoader loaderFor(IModel model, ResourceLocation... accepts) {
        ICustomModelLoader loader = mock(ICustomModelLoader.class);
        Set<ResourceLocation> accepted = new HashSet<>(List.of(accepts));
        when(loader.accepts(any())).thenAnswer(invocation -> accepted.isEmpty() || accepted.contains(invocation.getArgument(0)));
        try {
            when(loader.loadModel(any())).thenReturn(model);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        return loader;
    }

    private static IModel model() {
        IModel model = mock(IModel.class);
        when(model.getTextures()).thenReturn(Collections.emptyList());
        return model;
    }

    @Test
    void aModelIsLoadedOnceAndThenServedFromTheCache() {
        IModel model = model();
        AtomicInteger loads = new AtomicInteger();
        ICustomModelLoader loader = mock(ICustomModelLoader.class);
        when(loader.accepts(any())).thenReturn(true);
        try {
            when(loader.loadModel(any())).thenAnswer(invocation -> {
                loads.incrementAndGet();
                return model;
            });
        } catch (Exception e) {
            throw new AssertionError(e);
        }

        UnbakedModelProvider provider = new UnbakedModelProvider(new LinkedHashSet<>(List.of(loader)));
        ResourceLocation location = new ResourceLocation("minecraft:block/stone");
        assertSame(model, provider.getObject(location));
        assertSame(model, provider.getObject(location));
        assertEquals(1, loads.get());
        assertEquals(1, provider.cachedCount());

        // Invalidation sends the next lookup back to the loader, and clearing drops everything
        provider.invalidate(location);
        assertSame(model, provider.getObject(location));
        assertEquals(2, loads.get());
        provider.clearCache();
        assertEquals(0, provider.cachedCount());
    }

    @Test
    void aModelAModPutStaysPutAndIsListed() {
        IModel model = model();
        UnbakedModelProvider provider = new UnbakedModelProvider(new LinkedHashSet<>(List.of(loaderFor(model()))));
        ResourceLocation location = new ResourceLocation("examplemod:block/machine");

        provider.putObject(location, model);
        assertSame(model, provider.getObject(location));
        assertEquals(1, provider.permanentCount());
        assertEquals(Set.of(location), provider.getKeys());
        assertSame(model, provider.iterator().next());
    }

    @Test
    void anAliasIsFollowedToTheModelThatBacksIt() {
        IModel entity = model();
        ResourceLocation builtin = new ResourceLocation("builtin/entity");
        UnbakedModelProvider provider = new UnbakedModelProvider(new LinkedHashSet<>(List.of(loaderFor(model()))));
        provider.putObject(builtin, entity);
        // The alias the constructor installs, and one a caller adds
        assertSame(entity, provider.getObject(new ResourceLocation("block/builtin/entity")));
        provider.putAlias(new ResourceLocation("examplemod:alias"), builtin);
        assertSame(entity, provider.getObject(new ResourceLocation("examplemod:alias")));
    }

    @Test
    void aTextureCaptureCollectsWhatEveryLoadedModelUses() {
        IModel model = mock(IModel.class);
        when(model.getTextures()).thenReturn(List.of(new ResourceLocation("minecraft:blocks/stone")));
        UnbakedModelProvider provider = new UnbakedModelProvider(new LinkedHashSet<>(List.of(loaderFor(model))));

        Set<ResourceLocation> captured = Collections.synchronizedSet(new HashSet<>());
        UnbakedModelProvider.textureCapture = captured;
        try {
            provider.getObject(new ResourceLocation("minecraft:block/stone"));
        } finally {
            UnbakedModelProvider.textureCapture = null;
        }
        assertTrue(captured.contains(new ResourceLocation("minecraft:blocks/stone")));
    }

    @Test
    void twoLoadersAcceptingTheSameModelIsARefusal() {
        ResourceLocation location = new ResourceLocation("minecraft:block/stone");
        UnbakedModelProvider provider = new UnbakedModelProvider(
                new LinkedHashSet<>(List.of(loaderFor(model(), location), loaderFor(model(), location))));
        assertThrows(RuntimeException.class, () -> provider.getObject(location));
    }

    @Test
    void aLoaderThatFailsOrAnswersNothingIsReported() {
        ResourceLocation location = new ResourceLocation("minecraft:block/stone");

        ICustomModelLoader nullModel = mock(ICustomModelLoader.class);
        when(nullModel.accepts(any())).thenReturn(true);
        UnbakedModelProvider nulls = new UnbakedModelProvider(new LinkedHashSet<>(List.of(nullModel)));
        assertThrows(RuntimeException.class, () -> nulls.getObject(location));

        ICustomModelLoader broken = mock(ICustomModelLoader.class);
        when(broken.accepts(any())).thenThrow(new IllegalStateException("cannot tell"));
        UnbakedModelProvider failing = new UnbakedModelProvider(new LinkedHashSet<>(List.of(broken)));
        assertThrows(RuntimeException.class, () -> failing.getObject(location));

        // And a model whose parent chain cannot be resolved fails where the cause is visible
        IModel unresolvable = mock(IModel.class);
        when(unresolvable.getTextures()).thenThrow(new IllegalStateException("missing parent"));
        UnbakedModelProvider unresolved = new UnbakedModelProvider(new LinkedHashSet<>(List.of(loaderFor(unresolvable))));
        assertThrows(RuntimeException.class, () -> unresolved.getObject(location));
    }

    @Test
    void aVariantWithNoBlockstateEntryFallsBackToItsItemFile() {
        ModelResourceLocation variant = new ModelResourceLocation("examplemod:machine", "inventory");
        ResourceLocation itemFile = new ResourceLocation("examplemod", "item/machine");
        IModel itemModel = model();

        ICustomModelLoader loader = mock(ICustomModelLoader.class);
        when(loader.accepts(any())).thenReturn(true);
        try {
            when(loader.loadModel(any())).thenAnswer(invocation -> {
                // The loader is asked for the location under models/, which is where the file actually lives
                ResourceLocation asked = invocation.getArgument(0);
                if (asked.getPath().endsWith("item/machine")) {
                    return itemModel;
                }
                throw new IllegalStateException("no such variant");
            });
        } catch (Exception e) {
            throw new AssertionError(e);
        }

        UnbakedModelProvider provider = new UnbakedModelProvider(new LinkedHashSet<>(List.of(loader)));
        ModelLocations.addItemVariantFile(variant, itemFile);
        assertSame(itemModel, provider.getObject(variant));

        // With neither the variant nor an item file, the failure carries both causes
        ModelResourceLocation missing = new ModelResourceLocation("examplemod:nothing", "inventory");
        // getObject wraps whatever the load threw, and the failure inside carries both attempts
        RuntimeException thrown = assertThrows(RuntimeException.class, () -> provider.getObject(missing));
        assertInstanceOf(ModelLoaderRegistry.LoaderException.class, thrown.getCause());
        assertEquals(2, thrown.getCause().getSuppressed().length);
        // And the caller that wants a model regardless gets the missing one
        IModel missingModel = model();
        provider.putObject(UnbakedModelProvider.MISSING, missingModel);
        assertSame(missingModel, provider.getModelOrMissing(missing));
        assertSame(itemModel, provider.getModelOrMissing(variant));
    }
}
