package com.bdmajora.extras.client;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.ExtrasConfig.RenderSettings;
import com.bdmajora.impetus.ImpetusVintage;
import net.minecraft.client.Minecraft;

// Size and height of the cloud layer.
public final class CloudPassState {
    // Size of one cloud cell in the base game.
    public static final float VANILLA_CELL_SIZE = 12.0F;

    // Prevents instantiation
    private CloudPassState() {
    }

    // Returns the size of one cloud cell.
    public static float cellSize() {
        int scale = Math.max(RenderSettings.CLOUD_SCALE_MIN, Extras.options().render.cloudScale);
        return VANILLA_CELL_SIZE * scale / RenderSettings.CLOUD_SCALE_VANILLA;
    }

    // Returns the cloud height of the quality setting or the fallback outside a world.
    public static float cloudHeight(float fallback) {
        if (Minecraft.getMinecraft().world == null) {
            return fallback;
        }
        return ImpetusVintage.options().quality.cloudHeight;
    }
}
