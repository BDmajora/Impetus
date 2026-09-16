package com.bdmajora.equilibrium.mixin.block.spawner_check_cache;

import net.minecraft.tileentity.MobSpawnerBaseLogic;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// A spawner asks every tick whether a player is within 16 blocks, which walks the whole player list per spawner (FoamFix's mobSpawnerCheckSpeed); the answer is held for ten ticks, jittered by one so a farm's spawners do not all re-check on the same tick, and dropped early if the player count changes so a fake player added for a fraction of a tick cannot pin a stale answer
@Mixin(MobSpawnerBaseLogic.class)
public abstract class MobSpawnerBaseLogicMixin {
    private static final int CHECK_INTERVAL = 10;

    private boolean equilibrium$cachedActive;
    private int equilibrium$cachedPlayerCount = -1;
    private long equilibrium$recheckAt;

    @Inject(method = "isActivated", at = @At("HEAD"), cancellable = true)
    private void equilibrium$answerFromCache(CallbackInfoReturnable<Boolean> cir) {
        World world = ((MobSpawnerBaseLogic) (Object) this).getSpawnerWorld();
        if (world == null) {
            return;
        }
        int players = world.playerEntities.size();
        long now = world.getTotalWorldTime();
        if (players == this.equilibrium$cachedPlayerCount && now < this.equilibrium$recheckAt) {
            cir.setReturnValue(this.equilibrium$cachedActive);
        }
    }

    @Inject(method = "isActivated", at = @At("RETURN"))
    private void equilibrium$remember(CallbackInfoReturnable<Boolean> cir) {
        World world = ((MobSpawnerBaseLogic) (Object) this).getSpawnerWorld();
        if (world == null) {
            return;
        }
        this.equilibrium$cachedActive = cir.getReturnValue();
        this.equilibrium$cachedPlayerCount = world.playerEntities.size();
        this.equilibrium$recheckAt = world.getTotalWorldTime() + CHECK_INTERVAL + (world.rand.nextInt() & 1);
    }
}
