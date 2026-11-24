package com.bdmajora.extras.mixin.misc;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.ExtrasConfig;
import com.bdmajora.extras.compat.BetterNetherSaves;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes MinecraftServer.
@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {
    // Changes a constant in tick.
    @ModifyConstant(method = "tick", constant = @Constant(intValue = 900))
    private int impetus$autosaveInterval(int vanillaInterval) {
        int configured = Extras.options().extra.autosaveInterval;

        if (configured == ExtrasConfig.ExtraSettings.AUTOSAVE_MIN_TICKS) {
            return Integer.MAX_VALUE;
        }

        return configured;
    }

    // The final save queues BetterNether's city data off thread; it has to be on disk before the server is gone.
    @Inject(method = "stopServer", at = @At("RETURN"))
    private void impetus$flushBetterNether(CallbackInfo ci) {
        BetterNetherSaves.flush();
    }
}
