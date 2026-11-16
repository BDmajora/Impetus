package com.bdmajora.equilibrium.mixin.world.entity_cleanup;

import com.bdmajora.equilibrium.common.world.UnloadedEntityRemover;
import net.minecraft.world.WorldServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes WorldServer.
@Mixin(WorldServer.class)
public abstract class WorldServerMixin {
    // Reference to the field update entity tick of the target.
    @Shadow
    private int updateEntityTick;

    // Runs at the start of updateEntities.
    @Inject(method = "updateEntities", at = @At("HEAD"))
    private void equilibrium$removeOnIdleTicks(CallbackInfo ci) {
        WorldServer self = (WorldServer) (Object) this;
        if (self.playerEntities.isEmpty() && this.updateEntityTick >= 300) {
            ((UnloadedEntityRemover) self).equilibrium$removeUnloaded();
        }
    }
}
