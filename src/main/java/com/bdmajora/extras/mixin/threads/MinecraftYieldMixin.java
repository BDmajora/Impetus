package com.bdmajora.extras.mixin.threads;

import com.bdmajora.extras.client.ThreadTuning;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// StutterFix's "remove yield": the render loop offers its core to the scheduler once per frame, which on a busy machine hands it to a worker and delays the next frame
@Mixin(Minecraft.class)
public abstract class MinecraftYieldMixin {
    @Redirect(method = "runGameLoop", at = @At(value = "INVOKE", target = "Ljava/lang/Thread;yield()V"))
    private void impetus$optionalYield() {
        if (!ThreadTuning.removeRenderYield) {
            Thread.yield();
        }
    }
}
