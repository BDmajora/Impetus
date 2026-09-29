package com.bdmajora.coarctatio.mixin.client.model.dynamic.compat;

import com.bdmajora.coarctatio.client.model.dynamic.DynamicModels;
import com.bdmajora.coarctatio.client.model.dynamic.ModelLocations;
import com.bdmajora.coarctatio.client.model.dynamic.UnbakedModelProvider;
import com.bdmajora.coarctatio.client.model.dynamic.compat.TconTextureExistence;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.google.common.cache.Cache;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.ICustomModelLoader;
import net.minecraftforge.client.model.IModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import slimeknights.tconstruct.library.client.model.BakedToolModel;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CompatMixinsTest {
    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
        Mc.client();
    }

    @AfterEach
    void forgetProvider() {
        Mixins.set(DynamicModels.class, "unbaked", null);
    }

    // A provider whose loader refuses everything, so only what a test puts in it resolves
    private static UnbakedModelProvider installUnbaked() throws Exception {
        ICustomModelLoader refusing = mock(ICustomModelLoader.class);
        when(refusing.accepts(any())).thenReturn(true);
        when(refusing.loadModel(any())).thenThrow(new IllegalStateException("no such model"));
        UnbakedModelProvider provider = new UnbakedModelProvider(new LinkedHashSet<>(List.of(refusing)));
        Mixins.set(DynamicModels.class, "unbaked", provider);
        return provider;
    }

    @Test
    void ctmProfilingIsSwitchedOff() {
        assertNotNull(Mixins.instance(CtmProfileUtilMixin.class));
        CtmProfileUtilMixin.start("quads");
        CtmProfileUtilMixin.endAndStart("more quads");
        CtmProfileUtilMixin.end();
    }

    @Test
    void debarkSeesALiveViewOverEveryKnownLocation() throws Exception {
        UnbakedModelProvider provider = installUnbaked();
        ModelResourceLocation log = new ModelResourceLocation("debark:log", "axis=y");
        IModel model = mock(IModel.class);
        provider.putObject(log, model);
        ModelLocations.ALL_KNOWN.add(log);
        try {
            DebarkEarlyViewMixin view = Mixins.instance(DebarkEarlyViewMixin.class);
            Mixins.call(view, "coarctatio$liveView", Mixins.ci());
            Map<ModelResourceLocation, IModel> sauce = Mixins.get(view, "secretSauce");
            assertSame(model, sauce.get(log));
            // Only known locations are in the view
            assertNull(sauce.get(new ModelResourceLocation("debark:log", "axis=q")));
        } finally {
            ModelLocations.ALL_KNOWN.remove(log);
        }
    }

    @Test
    void tconstructsExistenceProbesAreAnsweredOncePerReload() {
        assertNotNull(Mixins.instance(TconTextureCreatorMixin.class));
        TconTextureExistence.clear();
        String key = "tconstruct:items/pickaxe/iron";

        // The first probe runs; its answer is remembered as it returns
        CallbackInfoReturnable<Boolean> first = Mixins.cir();
        Mixins.call(TconTextureCreatorMixin.class, "coarctatio$answerFromCache", key, first);
        assertFalse(first.isCancelled());
        Mixins.call(TconTextureCreatorMixin.class, "coarctatio$remember", key, Mixins.cir(true));

        CallbackInfoReturnable<Boolean> second = Mixins.cir();
        Mixins.call(TconTextureCreatorMixin.class, "coarctatio$answerFromCache", key, second);
        assertTrue(second.isCancelled());
        assertTrue(second.getReturnValue());

        // A reload clears what was learned
        TconTextureExistence.clear();
        assertNull(TconTextureExistence.known(key));
    }

    @Test
    void tconstructsToolModelsAreCachedPerParent() throws Exception {
        TconToolModelCacheMixin overrides = Mixins.instance(TconToolModelCacheMixin.class);
        Mixins.call(overrides, "coarctatio$useSoftCache", Mixins.ci());
        Cache<Object, IBakedModel> cache = Mixins.get(overrides, "bakedModelCache");

        IBakedModel parent = mock(IBakedModel.class);
        IBakedModel tool = mock(IBakedModel.class);
        BakedToolModel.CacheKey key = new BakedToolModel.CacheKey(parent);
        assertSame(tool, cache.get(key, () -> tool));
        // The second request for the same key is answered without assembling again
        assertSame(tool, cache.get(key, () -> {
            throw new AssertionError("assembled twice");
        }));
        assertEquals(1, cache.size());
        cache.cleanUp();
        cache.invalidateAll();
        assertEquals(0, cache.size());

        // A key whose parent cannot be read fails the load rather than the caller
        assertThrows(ExecutionException.class, () -> cache.get("not a key", () -> tool));

        // TConstruct only ever calls get(key, loader), so the rest is refused
        assertThrows(UnsupportedOperationException.class, () -> cache.getIfPresent(key));
        assertThrows(UnsupportedOperationException.class, () -> cache.getAllPresent(List.of(key)));
        assertThrows(UnsupportedOperationException.class, () -> cache.put(key, tool));
        assertThrows(UnsupportedOperationException.class, () -> cache.putAll(Map.of(key, tool)));
        assertThrows(UnsupportedOperationException.class, () -> cache.invalidate(key));
        assertThrows(UnsupportedOperationException.class, () -> cache.invalidateAll(List.of(key)));
        assertThrows(UnsupportedOperationException.class, cache::stats);
        assertThrows(UnsupportedOperationException.class, cache::asMap);
    }

    @Test
    void ucwReadsAndWritesTheDynamicProvider() throws Exception {
        UnbakedModelProvider provider = installUnbaked();
        IModel missing = mock(IModel.class);
        provider.putObject(UnbakedModelProvider.MISSING, missing);

        UcwEarlyViewMixin view = Mixins.instance(UcwEarlyViewMixin.class);
        Mixins.call(view, "coarctatio$keepMissing", Mixins.ci());

        Map<Object, Object> stateModels = new HashMap<>();
        ResourceLocation block = new ResourceLocation("ucw:block/stone_1");
        IModel model = mock(IModel.class);
        // A write lands in the provider, not in the loader's map
        assertNull(Mixins.call(view, "coarctatio$putDynamic", stateModels, block, model));
        assertTrue(stateModels.isEmpty());
        assertSame(model, Mixins.call(view, "coarctatio$getDynamic", stateModels, block));
        // A model that cannot be loaded answers the missing one
        assertSame(missing, Mixins.call(view, "coarctatio$getDynamic", stateModels, new ResourceLocation("ucw:block/nothing")));
    }
}
