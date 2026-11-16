package com.bdmajora.extras.mixin.misc;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.ExtrasConfig;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

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
}
