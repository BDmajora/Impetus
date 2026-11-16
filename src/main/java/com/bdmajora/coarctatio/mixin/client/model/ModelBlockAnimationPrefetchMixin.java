package com.bdmajora.coarctatio.mixin.client.model;

import com.bdmajora.coarctatio.client.model.ModelPrefetch;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.animation.ModelBlockAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Mixin that changes ModelBlockAnimation.
@Mixin(value = ModelBlockAnimation.class, remap = false)
public abstract class ModelBlockAnimationPrefetchMixin {
    // Runs at the start of loadVanillaAnimation and can cancel it.
    @Inject(method = "loadVanillaAnimation(Lnet/minecraft/client/resources/IResourceManager;Lnet/minecraft/util/ResourceLocation;)Lnet/minecraftforge/client/model/animation/ModelBlockAnimation;",
            at = @At("HEAD"), cancellable = true)
    private static void coarctatio$prefetchedAnimation(IResourceManager manager, ResourceLocation armature, CallbackInfoReturnable<ModelBlockAnimation> cir) {
        ModelBlockAnimation prefetched = ModelPrefetch.takeAnimation(armature);
        if (prefetched != null) {
            cir.setReturnValue(prefetched);
        }
    }
}
