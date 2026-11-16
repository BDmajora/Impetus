package com.bdmajora.coarctatio.mixin.client.resources;

import com.bdmajora.coarctatio.client.resources.ResourceLookupCaches;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import net.minecraft.client.resources.DefaultResourcePack;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Mixin that changes DefaultResourcePack.
@Mixin(DefaultResourcePack.class)
public abstract class DefaultResourcePackMixin {
    // Known existence of each resource.
    private final Object2BooleanOpenHashMap<ResourceLocation> coarctatio$known = new Object2BooleanOpenHashMap<>();
    // Generation of the known map.
    private int coarctatio$knownGeneration = -1;

    // Runs at the start of resourceExists and can cancel it.
    @Inject(method = "resourceExists", at = @At("HEAD"), cancellable = true)
    private void coarctatio$answerFromCache(ResourceLocation location, CallbackInfoReturnable<Boolean> cir) {
        synchronized (this.coarctatio$known) {
            int generation = ResourceLookupCaches.generation();
            if (this.coarctatio$knownGeneration != generation) {
                this.coarctatio$known.clear();
                this.coarctatio$knownGeneration = generation;
            }
            if (this.coarctatio$known.containsKey(location)) {
                cir.setReturnValue(this.coarctatio$known.getBoolean(location));
            }
        }
    }

    // Runs before every return of resourceExists.
    @Inject(method = "resourceExists", at = @At("RETURN"))
    private void coarctatio$remember(ResourceLocation location, CallbackInfoReturnable<Boolean> cir) {
        synchronized (this.coarctatio$known) {
            this.coarctatio$known.put(location, cir.getReturnValueZ());
        }
    }
}
