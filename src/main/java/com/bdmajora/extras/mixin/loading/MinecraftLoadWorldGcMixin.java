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

// loadWorld forces a full garbage collection on every world load and unload, which on a large heap is a multi-second stop-the-world pause the JVM would otherwise never need (VanillaFix, later Chibi's removeExcessiveGCCalls)
@Mixin(Minecraft.class)
public abstract class MinecraftLoadWorldGcMixin {
    @WrapWithCondition(method = "loadWorld(Lnet/minecraft/client/multiplayer/WorldClient;Ljava/lang/String;)V", at = @At(value = "INVOKE", target = "Ljava/lang/System;gc()V"))
    private boolean impetus$skipForcedGc() {
        return !Extras.options().loading.skipWorldLoadGc;
    }

    // Leaving a world is also when the HUD cache's framebuffer can go
    @Inject(method = "loadWorld(Lnet/minecraft/client/multiplayer/WorldClient;Ljava/lang/String;)V", at = @At("HEAD"))
    private void impetus$releaseHudCache(WorldClient world, String message, CallbackInfo ci) {
        if (world == null) {
            HudCache.release();
        }
    }
}
