package com.bdmajora.impetus.mixin.core;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.Minecraft;
import com.bdmajora.impetus.engine.impl.ImpetusRuntimeOptions;
import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions;
import com.bdmajora.impetus.engine.impl.render.frame.RenderAheadManager;
import com.bdmajora.impetus.impl.platform.GameWindow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.bdmajora.impetus.ImpetusVintage;

// Mixin that changes Minecraft.
@Mixin(Minecraft.class)
public class MinecraftMixin {
    // Field render ahead manager added to the target.
    @Unique
    private final RenderAheadManager impetus$renderAheadManager = new RenderAheadManager();

    // Runs at the start of runGameLoop.
    @Inject(method = "runGameLoop", at = @At("HEAD"))
    private void preRender(CallbackInfo ci) {
        impetus$renderAheadManager.startFrame(ImpetusVintage.options().advanced.cpuRenderAheadLimit);
    }

    // Runs before every return of runGameLoop.
    @Inject(method = "runGameLoop", at = @At("RETURN"))
    private void postRender(CallbackInfo ci) {
        impetus$renderAheadManager.endFrame();
    }

    // Changes the return value of getLimitFramerate.
    @ModifyReturnValue(method = "getLimitFramerate", at = @At("RETURN"))
    private int impetus$applyInactivityFpsLimit(int limit) {
        ImpetusGameOptions.InactivityFpsLimit mode = ImpetusRuntimeOptions.inactivityFpsLimit;

        if (mode == ImpetusGameOptions.InactivityFpsLimit.NO_LIMIT) {
            return limit;
        }

        if (GameWindow.isMinimized()) {
            return impetus$capped(limit, 10);
        }

        if (mode == ImpetusGameOptions.InactivityFpsLimit.AFK && !GameWindow.isFocused()) {
            return impetus$capped(limit, 30);
        }

        return limit;
    }

    // Helper method capped added to the target.
    @Unique
    private static int impetus$capped(int limit, int cap) {
        return limit > 0 ? Math.min(limit, cap) : cap;
    }
}
