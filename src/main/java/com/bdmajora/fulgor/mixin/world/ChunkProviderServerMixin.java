package com.bdmajora.fulgor.mixin.world;

import com.bdmajora.fulgor.api.LightingEngineProvider;
import net.minecraft.world.WorldServer;
import net.minecraft.world.gen.ChunkProviderServer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Mixin that changes ChunkProviderServer.
@Mixin(ChunkProviderServer.class)
public abstract class ChunkProviderServerMixin {
    // Reference to the field world of the target.
    @Shadow
    @Final
    public WorldServer world;

    // Runs at the start of saveChunks.
    @Inject(method = "saveChunks", at = @At("HEAD"))
    private void fulgor$flushBeforeSave(boolean all, CallbackInfoReturnable<Boolean> cir) {
        ((LightingEngineProvider) this.world).fulgor$getLightingEngine().processLightUpdates();
    }

    // Runs at the start of tick.
    @Inject(method = "tick", at = @At("HEAD"))
    private void fulgor$flushBeforeUnload(CallbackInfoReturnable<Boolean> cir) {
        ((LightingEngineProvider) this.world).fulgor$getLightingEngine().processLightUpdates();
    }
}
