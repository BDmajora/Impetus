package com.bdmajora.coarctatio.mixin.client.model;

import com.bdmajora.coarctatio.client.model.ModelPrefetch;
import net.minecraft.client.renderer.block.model.ModelBakery;
import net.minecraft.client.renderer.block.model.ModelBlock;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Mixin that changes ModelBakery.
@Mixin(ModelBakery.class)
public abstract class ModelBakeryPrefetchMixin {
    // Runs at the start of loadModel and can cancel it.
    @Inject(method = "loadModel(Lnet/minecraft/util/ResourceLocation;)Lnet/minecraft/client/renderer/block/model/ModelBlock;",
            at = @At("HEAD"), cancellable = true)
    private void coarctatio$prefetchedModel(ResourceLocation location, CallbackInfoReturnable<ModelBlock> cir) {
        ModelBlock prefetched = ModelPrefetch.takeModel(location);
        if (prefetched != null) {
            cir.setReturnValue(prefetched);
        }
    }
}
