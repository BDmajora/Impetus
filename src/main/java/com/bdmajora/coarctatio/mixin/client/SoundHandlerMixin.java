package com.bdmajora.coarctatio.mixin.client;

import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.audio.SoundRegistry;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Collections;
import java.util.Set;

// After loading every sounds.json the sound handler walks the whole registry twice to log debug lines about missing subtitles and sound events, a translation lookup per sound; both walks are given nothing to walk (UniversalTweaks' audio debug removal, by Darkhax)
@Mixin(SoundHandler.class)
public abstract class SoundHandlerMixin {
    @Redirect(method = "onResourceManagerReload", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/audio/SoundRegistry;getKeys()Ljava/util/Set;"))
    private Set<ResourceLocation> coarctatio$skipDebugWalks(SoundRegistry registry) {
        return Collections.emptySet();
    }
}
