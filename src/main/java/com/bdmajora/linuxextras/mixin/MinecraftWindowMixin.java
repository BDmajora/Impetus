package com.bdmajora.linuxextras.mixin;

import com.bdmajora.linuxextras.LinuxExtras;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes Minecraft.
@Mixin(Minecraft.class)
public class MinecraftWindowMixin {
    // Runs at the start of init.
    @Inject(method = "init", at = @At("HEAD"))
    private void linuxextras$prepareWindow(CallbackInfo ci) {
        LinuxExtras.prepareWindow();
    }
}
