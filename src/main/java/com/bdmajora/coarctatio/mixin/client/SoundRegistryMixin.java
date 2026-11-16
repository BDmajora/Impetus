package com.bdmajora.coarctatio.mixin.client;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.client.audio.SoundEventAccessor;
import net.minecraft.client.audio.SoundRegistry;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

// Mixin that changes SoundRegistry.
@Mixin(SoundRegistry.class)
public abstract class SoundRegistryMixin {
    @Shadow
    private Map<ResourceLocation, SoundEventAccessor> soundRegistry;

    // Runs before every return of createUnderlyingMap and can cancel it.
    @Inject(method = "createUnderlyingMap", at = @At("RETURN"), cancellable = true)
    private void coarctatio$compactSoundMap(CallbackInfoReturnable<Map<ResourceLocation, SoundEventAccessor>> cir) {
        Map<ResourceLocation, SoundEventAccessor> compact = new Object2ObjectOpenHashMap<>();

        this.soundRegistry = compact;
        cir.setReturnValue(compact);
    }
}
