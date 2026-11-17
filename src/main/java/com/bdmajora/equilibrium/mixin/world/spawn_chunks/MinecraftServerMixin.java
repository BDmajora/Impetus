package com.bdmajora.equilibrium.mixin.world.spawn_chunks;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes MinecraftServer.
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    // Reference to the field worlds of the target.
    @Shadow
    public WorldServer[] worlds;

    // Reference to the method set user message of the target.
    @Shadow
    protected abstract void setUserMessage(String message);

    // Reference to the method clear current task of the target.
    @Shadow
    protected abstract void clearCurrentTask();

    // Runs at the start of initialWorldChunkLoad and can cancel it.
    @Inject(method = "initialWorldChunkLoad", at = @At("HEAD"), cancellable = true)
    private void equilibrium$loadOnlySpawnChunk(CallbackInfo ci) {
        this.setUserMessage("menu.generatingTerrain");
        WorldServer overworld = this.worlds[0];
        BlockPos spawn = overworld.getSpawnPoint();
        overworld.getChunkProvider().provideChunk(spawn.getX() >> 4, spawn.getZ() >> 4);
        this.clearCurrentTask();
        ci.cancel();
    }
}
