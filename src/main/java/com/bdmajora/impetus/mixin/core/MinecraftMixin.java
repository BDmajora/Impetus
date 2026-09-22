package com.bdmajora.impetus.mixin.core;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import com.bdmajora.impetus.engine.impl.ImpetusRuntimeOptions;
import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions;
import com.bdmajora.impetus.engine.impl.render.frame.RenderAheadManager;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.bdmajora.impetus.ImpetusVintage;

// Patches Minecraft's tick/display code: CPU render-ahead throttling, inactivity FPS caps, and a fullscreen-create fallback
@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Shadow
    private boolean fullscreen;

    @Shadow
    public int displayWidth;

    @Shadow
    public int displayHeight;

    @Shadow
    public GameSettings gameSettings;

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

        if (!Display.isVisible()) {
            // Minimized window: both AFK and MINIMIZED modes clamp hard.
            return Math.min(limit, 10);
        }

        if (mode == ImpetusGameOptions.InactivityFpsLimit.AFK && !Display.isActive()) {
            // Unfocused window in AFK mode.
            return Math.min(limit, 30);
        }

        return limit;
    }

    // Some drivers fail to create a fullscreen display; retry windowed instead of crashing to desktop
    @Redirect(method = "createDisplay", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/Display;create()V", remap = false))
    private void impetus$retryWindowedWhenFullscreenDisplayCreateFails() throws LWJGLException {
        try {
            Display.create();
            return;
        } catch (LWJGLException exception) {
            if (!this.fullscreen && (this.gameSettings == null || !this.gameSettings.fullScreen)) {
                throw exception;
            }

            ImpetusVintage.logger().warn("Fullscreen OpenGL display creation failed; retrying in windowed mode", exception);
            this.fullscreen = false;

            if (this.gameSettings != null) {
                this.gameSettings.fullScreen = false;
                this.gameSettings.saveOptions();
            }

            Display.setFullscreen(false);
            Display.setDisplayMode(new DisplayMode(Math.max(1, this.displayWidth), Math.max(1, this.displayHeight)));
            Display.create();
        }
    }
}
