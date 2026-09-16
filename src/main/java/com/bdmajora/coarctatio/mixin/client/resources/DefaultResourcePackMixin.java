package com.bdmajora.coarctatio.mixin.client.resources;

import com.bdmajora.coarctatio.client.resources.ResourceLookupCaches;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import net.minecraft.client.resources.DefaultResourcePack;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// The built-in pack answers resourceExists by opening a classpath stream (and not closing it) then consulting the asset index; the answer per location is remembered for the reload generation, since the classpath cannot change underneath a running game. Synchronised since the texture scan and on-demand model loading probe from worker threads
@Mixin(DefaultResourcePack.class)
public abstract class DefaultResourcePackMixin {
    private final Object2BooleanOpenHashMap<ResourceLocation> coarctatio$known = new Object2BooleanOpenHashMap<>();
    private int coarctatio$knownGeneration = -1;

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

    @Inject(method = "resourceExists", at = @At("RETURN"))
    private void coarctatio$remember(ResourceLocation location, CallbackInfoReturnable<Boolean> cir) {
        synchronized (this.coarctatio$known) {
            this.coarctatio$known.put(location, cir.getReturnValueZ());
        }
    }
}
