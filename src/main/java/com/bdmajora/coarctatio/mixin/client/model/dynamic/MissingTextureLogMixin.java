package com.bdmajora.coarctatio.mixin.client.model.dynamic;

import net.minecraftforge.fml.client.FMLClientHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// The texture scan registers every png it finds, so the missing-texture report would list files that are not sprites at all. remap = false, Forge class
@Mixin(value = FMLClientHandler.class, remap = false)
public abstract class MissingTextureLogMixin {
    @Inject(method = "logMissingTextureErrors", at = @At("HEAD"), cancellable = true)
    private void coarctatio$silenceScanNoise(CallbackInfo ci) {
        ci.cancel();
    }
}
