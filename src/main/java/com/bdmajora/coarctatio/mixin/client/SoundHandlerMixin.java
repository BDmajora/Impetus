package com.bdmajora.coarctatio.mixin.client;

import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.audio.SoundRegistry;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Collections;
import java.util.Set;

// Mixin that changes SoundHandler.
@Mixin(SoundHandler.class)
public abstract class SoundHandlerMixin {
    // Replaces the call of SoundRegistry.getKeys in onResourceManagerReload.
    @Redirect(method = "onResourceManagerReload", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/audio/SoundRegistry;getKeys()Ljava/util/Set;"))
    private Set<ResourceLocation> coarctatio$skipDebugWalks(SoundRegistry registry) {
        return Collections.emptySet();
    }
}
