package com.bdmajora.extras.mixin.misc;

import com.bdmajora.extras.client.TimeWeatherOverride;
import net.minecraft.world.WorldServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes WorldServer.
@Mixin(WorldServer.class)
public class WorldServerMixin {
    // Runs at the start of tick.
    @Inject(method = "tick", at = @At("HEAD"))
    private void impetus$applyOverrides(CallbackInfo ci) {
        TimeWeatherOverride.apply((WorldServer) (Object) this);
    }
}
