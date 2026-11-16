package com.bdmajora.coarctatio.mixin.client;

import net.minecraft.client.util.SearchTree;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

// Mixin that changes SearchTree.
@Mixin(SearchTree.class)
public abstract class SearchTreeMixin<T> {
    // Field stale added to the target.
    @Unique
    private boolean coarctatio$stale = true;

    // Reference to the method recalculate of the target.
    @Shadow
    public abstract void recalculate();

    // Runs at the start of recalculate and can cancel it.
    @Inject(method = "recalculate", at = @At("HEAD"), cancellable = true)
    private void coarctatio$deferRecalculate(CallbackInfo ci) {
        if (this.coarctatio$stale) {
            ci.cancel();
        }
    }

    // Runs at the start of search.
    @Inject(method = "search", at = @At("HEAD"))
    private void coarctatio$buildOnDemand(String query, CallbackInfoReturnable<List<T>> cir) {
        if (this.coarctatio$stale) {
            this.coarctatio$stale = false;
            recalculate();
        }
    }

    // Runs at the start of add.
    @Inject(method = "add", at = @At("HEAD"))
    private void coarctatio$invalidate(T item, CallbackInfo ci) {
        this.coarctatio$stale = true;
    }
}
