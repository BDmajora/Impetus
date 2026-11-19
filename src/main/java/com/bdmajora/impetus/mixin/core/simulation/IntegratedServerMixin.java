package com.bdmajora.impetus.mixin.core.simulation;

import com.bdmajora.impetus.impl.world.SimulationDistance;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.integrated.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(IntegratedServer.class)
public abstract class IntegratedServerMixin {
    // Keep enough chunks loaded when simulation extends past the client's view.
    @ModifyExpressionValue(method = "tick", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/settings/GameSettings;renderDistanceChunks:I"))
    private int impetus$serverChunkDistance(int renderDistance) {
        return Math.max(renderDistance, SimulationDistance.getLocalDistance());
    }
}
