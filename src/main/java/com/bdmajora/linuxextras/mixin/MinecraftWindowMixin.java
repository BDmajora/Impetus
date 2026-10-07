package com.bdmajora.linuxextras.mixin;

import com.bdmajora.impetus.impl.platform.GameWindow;
import com.bdmajora.linuxextras.LinuxExtras;
import com.bdmajora.linuxextras.wayland.WaylandWindow;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFWVidMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.nio.ByteBuffer;

// Linux Extras' window hooks; Cleanroom's Display itself cannot be mixed into (LWJGLTransformer builds it at load, so Mixin finds no class file for it), so its Wayland mistakes are put right at vanilla's calls into it
@Mixin(Minecraft.class)
public class MinecraftWindowMixin {
    // The Wayland switch goes back whether or not the window came up; the window fixes only follow a window
    @WrapMethod(method = "createDisplay")
    private void linuxextras$createDisplay(Operation<Void> original) {
        try {
            original.call();
        } finally {
            LinuxExtras.onWindowCreated();
        }
        WaylandWindow.onWindowCreated(GameWindow.handle());
    }

    @WrapOperation(method = "toggleFullscreen", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/Display;setFullscreen(Z)V", remap = false), require = 0)
    private void linuxextras$fullscreenOnPrimary(boolean fullscreen, Operation<Void> original) {
        original.call(fullscreen);
        GLFWVidMode moved = fullscreen ? WaylandWindow.moveToFullscreenMonitor(GameWindow.handle()) : null;
        if (moved != null) {
            // Cleanroom sized the game for the output it picked, and Minecraft ignores window resizes while fullscreen
            ((Minecraft) (Object) this).resize(moved.width(), moved.height());
        }
    }

    @WrapOperation(method = "setWindowIcon", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/Display;setIcon([Ljava/nio/ByteBuffer;)I", remap = false), require = 0)
    private int linuxextras$waylandIcon(ByteBuffer[] icons, Operation<Integer> original) {
        return WaylandWindow.takeIcon(icons) ? 0 : original.call((Object) icons);
    }
}
