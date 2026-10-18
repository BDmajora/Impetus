package com.bdmajora.extras.client.zoom;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.ExtrasConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

// LogicalGeekBoy's Logical Zoom: holding C narrows the view to 0.23 of its width, turns the cinematic camera on for the duration and hides the first-person hand
@SideOnly(Side.CLIENT)
public final class LogicalZoom {
    // In-game only, so opening a screen ends the zoom the way modern Minecraft releases every key when one opens
    private static final KeyBinding ZOOM = new KeyBinding("impetus.key.zoom", KeyConflictContext.IN_GAME, Keyboard.KEY_C, "impetus.key.category");

    // Latched once per frame by update, so every projection and the hand within one frame agree
    private static boolean zoomed;
    // Set while the cinematic camera is on because of the zoom; the player's own setting waits in smoothCameraBefore
    private static boolean borrowedSmoothCamera;
    private static boolean smoothCameraBefore;

    private LogicalZoom() {
    }

    // Called once from the mod's init phase; the controls screen only lists bindings registered by then
    public static void register() {
        ClientRegistry.registerKeyBinding(ZOOM);
    }

    // From the head of updateCameraAndRender, before the mouse turns the camera, so the first zoomed frame already turns smoothly
    public static void update() {
        boolean zooming = Extras.options().zoom.enabled && ZOOM.isKeyDown();
        if (zooming == zoomed) {
            return;
        }
        zoomed = zooming;

        GameSettings settings = Minecraft.getMinecraft().gameSettings;
        if (zooming && Extras.options().zoom.smoothCamera) {
            smoothCameraBefore = settings.smoothCamera;
            settings.smoothCamera = true;
            borrowedSmoothCamera = true;
        } else if (!zooming && borrowedSmoothCamera) {
            settings.smoothCamera = smoothCameraBefore;
            borrowedSmoothCamera = false;
        }
    }

    public static boolean isZoomed() {
        return zoomed;
    }

    public static boolean hidesHand() {
        return zoomed && Extras.options().zoom.hideHand;
    }

    // Logical Zoom scales the projection's x and y by 1 / level; shrinking tan(fov / 2) by level builds exactly that matrix, and every gluPerspective in the frame (sky, clouds, world, shader capture) follows
    public static float zoomFov(float fov) {
        if (!zoomed) {
            return fov;
        }
        double level = Extras.options().zoom.level / (double) ExtrasConfig.ZoomSettings.LEVEL_SCALE;
        return (float) Math.toDegrees(2.0D * Math.atan(level * Math.tan(Math.toRadians(fov) / 2.0D)));
    }
}
