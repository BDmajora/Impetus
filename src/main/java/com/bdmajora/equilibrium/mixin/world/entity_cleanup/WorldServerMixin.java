package com.bdmajora.equilibrium.mixin.world.entity_cleanup;

import com.bdmajora.equilibrium.common.world.UnloadedEntityRemover;
import net.minecraft.world.WorldServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Once a dimension has had no players for 300 ticks, updateEntities returns before World.updateEntities, and the entities and tile entities queued for removal by chunk unloads pile up in their lists until a player returns (FoamFix's fixWorldEntityCleanup); running the removal step on those ticks keeps the lists, and the chunks they pin, from leaking
@Mixin(WorldServer.class)
public abstract class WorldServerMixin {
    @Shadow
    private int updateEntityTick;

    @Inject(method = "updateEntities", at = @At("HEAD"))
    private void equilibrium$removeOnIdleTicks(CallbackInfo ci) {
        WorldServer self = (WorldServer) (Object) this;
        // Mirrors vanilla's early-out condition exactly: the counter is read before vanilla increments it, so this fires on the same ticks vanilla skips
        if (self.playerEntities.isEmpty() && this.updateEntityTick >= 300) {
            ((UnloadedEntityRemover) self).equilibrium$removeUnloaded();
        }
    }
}
