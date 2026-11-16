package com.bdmajora.extras.mixin.loading;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.client.hud.HudCache;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes Minecraft.
@Mixin(Minecraft.class)
public abstract class MinecraftLoadWorldGcMixin {
    // Wraps the call of System.gc in loadWorld.
    @WrapWithCondition(method = "loadWorld(Lnet/minecraft/client/multiplayer/WorldClient;Ljava/lang/String;)V", at = @At(value = "INVOKE", target = "Ljava/lang/System;gc()V", remap = false))
    private boolean impetus$skipForcedGc() {
        return !Extras.options().loading.skipWorldLoadGc;
    }

    // Runs at the start of loadWorld.
    @Inject(method = "loadWorld(Lnet/minecraft/client/multiplayer/WorldClient;Ljava/lang/String;)V", at = @At("HEAD"))
    private void impetus$releaseHudCache(WorldClient world, String message, CallbackInfo ci) {
        if (world == null) {
            HudCache.release();
        }
    }
}
