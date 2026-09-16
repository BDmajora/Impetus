package com.bdmajora.equilibrium.mixin.world.spawn_chunks;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Skips the 25x25 chunk spawn preload (Ksyxis' idea): vanilla generates 625 chunks before the world opens, and on 1.12.2 those chunks then stay loaded forever; only the chunk holding the spawn point is generated so the player has somewhere to stand, the rest load on demand and are kept like any spawn chunk once they do
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    @Shadow
    public WorldServer[] worlds;

    @Shadow
    protected abstract void setUserMessage(String message);

    @Shadow
    protected abstract void clearCurrentTask();

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
