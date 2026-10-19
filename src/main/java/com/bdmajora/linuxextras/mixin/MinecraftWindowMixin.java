package com.bdmajora.linuxextras.mixin;

import com.bdmajora.linuxextras.LinuxExtras;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftWindowMixin {
    @Inject(method = "init", at = @At("HEAD"))
    private void linuxextras$prepareWindow(CallbackInfo ci) {
        LinuxExtras.prepareWindow();
    }
}
