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

// Patches Minecraft's frame loop: CPU render-ahead throttling and inactivity FPS caps (Cleanroom creates the window through GLFW, so the LWJGL2 fullscreen-create retry this once carried has nothing left to catch)
@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Unique
    private final RenderAheadManager impetus$renderAheadManager = new RenderAheadManager();

    // Opens the frame for the render-ahead limiter. runGameLoop, not runTick: Sodium hooks MinecraftClient.runTick(boolean) because in modern versions that IS the per-frame render, but in 1.12.2 runTick() is the 20 Hz game tick and runGameLoop() the frame, so a fence per tick never bounded anything (a fence three ticks old is 150 ms behind the GPU)
    @Inject(method = "runGameLoop", at = @At("HEAD"))
    private void preRender(CallbackInfo ci) {
        impetus$renderAheadManager.startFrame(ImpetusVintage.options().advanced.cpuRenderAheadLimit);
    }

    // Closes the frame, which is where the limiter may block
    @Inject(method = "runGameLoop", at = @At("RETURN"))
    private void postRender(CallbackInfo ci) {
        impetus$renderAheadManager.endFrame();
    }

    // Caps framerate hard when minimized, or (in AFK mode) merely unfocused; mirrors Sodium's "Inactivity FPS Limit"
    @ModifyReturnValue(method = "getLimitFramerate", at = @At("RETURN"))
    private int impetus$applyInactivityFpsLimit(int limit) {
        ImpetusGameOptions.InactivityFpsLimit mode = ImpetusRuntimeOptions.inactivityFpsLimit;

        if (mode == ImpetusGameOptions.InactivityFpsLimit.NO_LIMIT) {
            return limit;
        }

        if (GameWindow.isMinimized()) {
            // Minimized window: both AFK and MINIMIZED modes clamp hard.
            return Math.min(limit, 10);
        }

        if (mode == ImpetusGameOptions.InactivityFpsLimit.AFK && !GameWindow.isFocused()) {
            // Unfocused window in AFK mode.
            return Math.min(limit, 30);
        }

        return limit;
    }
}
