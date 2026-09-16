package com.bdmajora.coarctatio.mixin.client.model.dynamic.compat;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.CacheStats;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import net.minecraft.client.renderer.block.model.IBakedModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

// TConstruct caches every assembled tool model forever, keyed by parent model and modifiers, which with dynamic loading would also pin every parent; the cache becomes one soft, expiring cache per parent, weakly keyed so a parent the registry let go takes its tools with it. Applied only when TConstruct is present
@Pseudo
@Mixin(targets = "slimeknights/tconstruct/library/client/model/BakedToolModel$ToolItemOverrideList", remap = false)
public abstract class TconToolModelCacheMixin {
    @Shadow
    private Cache<?, IBakedModel> bakedModelCache;

    private static final MethodHandle coarctatio$PARENT = coarctatio$parentGetter();

    private final LoadingCache<IBakedModel, Cache<Object, IBakedModel>> coarctatio$byParent = CacheBuilder.newBuilder()
            .maximumSize(70)
            .expireAfterWrite(3, TimeUnit.MINUTES)
            .weakKeys()
            .softValues()
            .build(new CacheLoader<IBakedModel, Cache<Object, IBakedModel>>() {
                @Override
                public Cache<Object, IBakedModel> load(IBakedModel parent) {
                    return CacheBuilder.newBuilder()
                            .expireAfterWrite(3, TimeUnit.MINUTES)
                            .maximumSize(50)
                            .build();
                }
            });

    private IBakedModel coarctatio$modelFor(Object key, Callable<? extends IBakedModel> loader) throws ExecutionException {
        IBakedModel parent;
        try {
            parent = (IBakedModel) coarctatio$PARENT.invoke(key);
        } catch (Throwable e) {
            throw new ExecutionException(e);
        }
        return this.coarctatio$byParent.get(parent).get(key, loader);
    }

    private static MethodHandle coarctatio$parentGetter() {
        try {
            Field parent = Class.forName("slimeknights.tconstruct.library.client.model.BakedToolModel$CacheKey").getDeclaredField("parent");
            parent.setAccessible(true);
            return MethodHandles.lookup().unreflectGetter(parent);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    // TConstruct only ever calls get(key, loader) on its cache
    @Inject(method = "<init>", at = @At("RETURN"))
    private void coarctatio$useSoftCache(CallbackInfo ci) {
        this.bakedModelCache = new Cache<Object, IBakedModel>() {
            @Nullable
            @Override
            public IBakedModel getIfPresent(Object key) {
                throw new UnsupportedOperationException();
            }

            @Override
            public IBakedModel get(Object key, Callable<? extends IBakedModel> loader) throws ExecutionException {
                return coarctatio$modelFor(key, loader);
            }

            @Override
            public ImmutableMap<Object, IBakedModel> getAllPresent(Iterable<?> keys) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void put(Object key, IBakedModel value) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void putAll(Map<?, ? extends IBakedModel> map) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void invalidate(Object key) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void invalidateAll(Iterable<?> keys) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void invalidateAll() {
                coarctatio$byParent.invalidateAll();
            }

            @Override
            public long size() {
                return coarctatio$byParent.size();
            }

            @Override
            public CacheStats stats() {
                throw new UnsupportedOperationException();
            }

            @Override
            public ConcurrentMap<Object, IBakedModel> asMap() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void cleanUp() {
                coarctatio$byParent.cleanUp();
            }
        };
    }
}
