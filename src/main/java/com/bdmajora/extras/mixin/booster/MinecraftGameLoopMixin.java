package com.bdmajora.extras.mixin.booster;

import com.bdmajora.extras.client.booster.StreamingUploader;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes Minecraft.
@Mixin(Minecraft.class)
public class MinecraftGameLoopMixin {
    // The loop first runs once loading gave the window's context back to the client thread.
    @Inject(method = "runGameLoop", at = @At("HEAD"))
    private void impetus$beginStreaming(CallbackInfo ci) {
        StreamingUploader.beginGameLoop();
    }
}
