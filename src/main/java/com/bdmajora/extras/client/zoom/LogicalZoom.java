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

// Zoom that narrows the field of view while a key is held.
@SideOnly(Side.CLIENT)
public final class LogicalZoom {
    // Key that zooms.
    private static final KeyBinding ZOOM = new KeyBinding("impetus.key.zoom", KeyConflictContext.IN_GAME, Keyboard.KEY_C, "impetus.key.category");

    // Whether the zoom is active.
    private static boolean zoomed;
    // Whether the smooth camera was switched on by the zoom.
    private static boolean borrowedSmoothCamera;
    // Smooth camera setting before the zoom.
    private static boolean smoothCameraBefore;

    // Prevents instantiation
    private LogicalZoom() {
    }

    // Registers the key.
    public static void register() {
        ClientRegistry.registerKeyBinding(ZOOM);
    }

    // Applies the key state.
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

    // Returns whether the zoom is active.
    public static boolean isZoomed() {
        return zoomed;
    }

    // Returns whether the zoom hides the hand.
    public static boolean hidesHand() {
        return zoomed && Extras.options().zoom.hideHand;
    }

    // Returns the narrowed field of view.
    public static float zoomFov(float fov) {
        if (!zoomed) {
            return fov;
        }
        double level = Extras.options().zoom.level / (double) ExtrasConfig.ZoomSettings.LEVEL_SCALE;
        return (float) Math.toDegrees(2.0D * Math.atan(level * Math.tan(Math.toRadians(fov) / 2.0D)));
    }
}
