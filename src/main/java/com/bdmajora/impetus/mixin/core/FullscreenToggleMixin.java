package com.bdmajora.impetus.mixin.core;

import com.bdmajora.impetus.impl.platform.WindowModes;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;

// Routes vanilla's fullscreen toggle (F11, the video settings button, Impetus's own mode switch) through Impetus's window modes
@Mixin(Minecraft.class)
public class FullscreenToggleMixin {
    @WrapMethod(method = "toggleFullscreen")
    private void impetus$toggleFullscreen(Operation<Void> original) {
        WindowModes.toggle((Minecraft) (Object) this, original::call);
    }
}
