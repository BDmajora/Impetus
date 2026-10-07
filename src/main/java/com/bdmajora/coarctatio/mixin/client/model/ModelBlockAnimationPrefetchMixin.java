package com.bdmajora.coarctatio.mixin.client.model;

import com.bdmajora.coarctatio.client.model.ModelPrefetch;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.animation.ModelBlockAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// The armature lookup VanillaLoader makes beside every model, which nearly always misses every pack; the prefetch already asked on a worker. remap = false, Forge class
@Mixin(value = ModelBlockAnimation.class, remap = false)
public abstract class ModelBlockAnimationPrefetchMixin {
    @Inject(method = "loadVanillaAnimation(Lnet/minecraft/client/resources/IResourceManager;Lnet/minecraft/util/ResourceLocation;)Lnet/minecraftforge/client/model/animation/ModelBlockAnimation;",
            at = @At("HEAD"), cancellable = true)
    private static void coarctatio$prefetchedAnimation(IResourceManager manager, ResourceLocation armature, CallbackInfoReturnable<ModelBlockAnimation> cir) {
        ModelBlockAnimation prefetched = ModelPrefetch.takeAnimation(armature);
        if (prefetched != null) {
            cir.setReturnValue(prefetched);
        }
    }
}
