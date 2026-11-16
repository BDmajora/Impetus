package com.bdmajora.coarctatio.mixin.client.model.dynamic.compat;

import com.bdmajora.coarctatio.collections.ParentPartitionedCache;
import com.google.common.cache.Cache;
import net.minecraft.client.renderer.block.model.IBakedModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.time.Duration;

// Replaces the tool model cache of Tinkers Construct with one that is partitioned by parent model and reclaimed under memory pressure.
@Pseudo
@Mixin(targets = "slimeknights/tconstruct/library/client/model/BakedToolModel$ToolItemOverrideList", remap = false)
public abstract class TconToolModelCacheMixin {
    // Tool model cache of the override list.
    @Shadow
    private Cache<?, IBakedModel> bakedModelCache;

    // Getter of the parent field of a cache key or null when the field is missing.
    private static final MethodHandle coarctatio$PARENT = coarctatio$parentGetter();
    // Largest number of parent models with cached tools.
    private static final int coarctatio$MAX_PARENTS = 70;
    // Largest number of cached tools of one parent.
    private static final int coarctatio$MAX_TOOLS_PER_PARENT = 50;
    // Time after which a cached tool expires.
    private static final Duration coarctatio$EXPIRY = Duration.ofMinutes(3);

    // Installs the partitioned cache after construction.
    @Inject(method = "<init>", at = @At("RETURN"))
    private void coarctatio$useSoftCache(CallbackInfo ci) {
        this.bakedModelCache = new ParentPartitionedCache<IBakedModel, Object, IBakedModel>(
                TconToolModelCacheMixin::coarctatio$parentOf, coarctatio$MAX_PARENTS, coarctatio$MAX_TOOLS_PER_PARENT,
                coarctatio$EXPIRY);
    }

    // Returns the parent model of a cache key or null for a key without one.
    private static IBakedModel coarctatio$parentOf(Object key) {
        if (coarctatio$PARENT == null) {
            return null;
        }
        try {
            Object parent = coarctatio$PARENT.invoke(key);
            return parent instanceof IBakedModel model ? model : null;
        } catch (ClassCastException e) {
            return null;
        } catch (Throwable e) {
            throw new IllegalStateException("Could not read the parent of a tool model key", e);
        }
    }

    // Returns the getter of the parent field of a cache key or null when the class or field is missing.
    private static MethodHandle coarctatio$parentGetter() {
        try {
            Field parent = Class.forName("slimeknights.tconstruct.library.client.model.BakedToolModel$CacheKey").getDeclaredField("parent");
            parent.setAccessible(true);
            return MethodHandles.lookup().unreflectGetter(parent);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
