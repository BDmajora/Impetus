package com.bdmajora.coarctatio.mixin.core;

import net.minecraftforge.registries.ObjectHolderRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

// Mixin that changes ObjectHolderRegistry.
@Mixin(value = ObjectHolderRegistry.class, remap = false)
public abstract class ObjectHolderRegistryMixin {
    // Reference to the field object holders of the target.
    @Shadow
    private List<Object> objectHolders;

    // Runs before every return of findObjectHolders.
    @Inject(method = "findObjectHolders", at = @At("RETURN"))
    @SuppressWarnings("unchecked")
    private void coarctatio$trimHolders(CallbackInfo ci) {
        if (this.objectHolders instanceof ArrayList) {
            ((ArrayList<Object>) this.objectHolders).trimToSize();
        }
    }
}
