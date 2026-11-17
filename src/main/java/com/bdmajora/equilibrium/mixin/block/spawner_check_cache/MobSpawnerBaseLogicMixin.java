package com.bdmajora.equilibrium.mixin.block.spawner_check_cache;

import net.minecraft.tileentity.MobSpawnerBaseLogic;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Mixin that changes MobSpawnerBaseLogic.
@Mixin(MobSpawnerBaseLogic.class)
public abstract class MobSpawnerBaseLogicMixin {
    // Ticks between player checks.
    private static final int CHECK_INTERVAL = 10;

    // Cached result of the player check.
    private boolean equilibrium$cachedActive;
    // Player count at the last check.
    private int equilibrium$cachedPlayerCount = -1;
    // World time of the next check.
    private long equilibrium$recheckAt;

    // Runs at the start of isActivated and can cancel it.
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

    // Runs before every return of isActivated.
    @Inject(method = "isActivated", at = @At("RETURN"))
    private void equilibrium$remember(CallbackInfoReturnable<Boolean> cir) {
        World world = ((MobSpawnerBaseLogic) (Object) this).getSpawnerWorld();
        if (world == null) {
            return;
        }
        this.equilibrium$cachedActive = cir.getReturnValue();
        this.equilibrium$cachedPlayerCount = world.playerEntities.size();
        // Staggers spawners by position.
        long staggered = ((MobSpawnerBaseLogic) (Object) this).getSpawnerPosition().toLong() & 1L;
        this.equilibrium$recheckAt = world.getTotalWorldTime() + CHECK_INTERVAL + staggered;
    }
}
