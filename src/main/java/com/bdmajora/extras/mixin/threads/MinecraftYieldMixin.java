package com.bdmajora.extras.mixin.threads;

import com.bdmajora.extras.client.ThreadTuning;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Mixin that changes Minecraft.
@Mixin(Minecraft.class)
public abstract class MinecraftYieldMixin {
    // Replaces the call of Thread.yield in runGameLoop.
    @Redirect(method = "runGameLoop", at = @At(value = "INVOKE", target = "Ljava/lang/Thread;yield()V", remap = false))
    private void impetus$optionalYield() {
        if (!ThreadTuning.removeRenderYield) {
            Thread.yield();
        }
    }
}
