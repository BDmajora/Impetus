package com.bdmajora.equilibrium.mixin.world.chunk_unload_budget;

import net.minecraft.world.gen.ChunkProviderServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Mixin that changes ChunkProviderServer.
@Mixin(ChunkProviderServer.class)
public abstract class ChunkProviderServerMixin {
    // Time a tick may spend unloading chunks, a third of its 50 ms.
    @Unique
    private static final long EQUILIBRIUM_UNLOAD_NANOS = 15_000_000L;

    // Time the running unload pass ends.
    @Unique
    private long equilibrium$unloadDeadline;

    // Runs at the start of tick.
    @Inject(method = "tick", at = @At("HEAD"))
    private void equilibrium$beginUnloading(CallbackInfoReturnable<Boolean> cir) {
        this.equilibrium$unloadDeadline = System.nanoTime() + EQUILIBRIUM_UNLOAD_NANOS;
    }

    // Vanilla compares the number of unloaded chunks with this limit before each one. A limit of one once the time is up
    // still lets every tick unload something. What is left stays queued for the next tick.
    @ModifyConstant(method = "tick", constant = @Constant(intValue = 100), require = 0)
    private int equilibrium$unloadLimit(int vanilla) {
        return System.nanoTime() - this.equilibrium$unloadDeadline < 0L ? vanilla : 1;
    }
}
