package com.bdmajora.extras.mixin.booster;

import com.bdmajora.extras.client.booster.StreamingUploader;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.WorldVertexBufferUploader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes WorldVertexBufferUploader.
@Mixin(WorldVertexBufferUploader.class)
public class WorldVertexBufferUploaderMixin {
    // Runs at the start of draw and can cancel it.
    @Inject(method = "draw", at = @At("HEAD"), cancellable = true)
    private void impetus$streamDraw(BufferBuilder builder, CallbackInfo ci) {
        if (StreamingUploader.draw(builder)) {
            ci.cancel();
        }
    }
}
