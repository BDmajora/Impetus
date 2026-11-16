package com.bdmajora.impetus.mixin.core;

import com.bdmajora.impetus.impl.platform.WindowModes;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;

// Mixin that changes Minecraft.
@Mixin(Minecraft.class)
public class FullscreenToggleMixin {
    // Handles toggle fullscreen.
    @WrapMethod(method = "toggleFullscreen")
    private void impetus$toggleFullscreen(Operation<Void> original) {
        WindowModes.toggle((Minecraft) (Object) this, original::call);
    }
}
