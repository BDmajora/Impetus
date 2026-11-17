package com.bdmajora.impetus.impl.gui.overlay;

import com.bdmajora.impetus.ImpetusVintage;
import com.bdmajora.impetus.impl.world.WorldSaveTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;

// Fades a saving line into the bottom right corner of the HUD while the integrated server writes the world to disk
public final class AutosaveIndicator {
    // Lang key of the line
    static final String KEY = "impetus.autosave_indicator.saving";
    // Share of the remaining distance to the target the fade covers each client tick
    private static final float FADE = 0.2F;
    // Client ticks the line keeps rising after a save starts.
    static final int MIN_TICKS = 10;
    // Alpha at or below which nothing is drawn.
    static final int MIN_ALPHA = 8;
    // Gap between the end of the line and the right edge in scaled pixels
    private static final int RIGHT = 10;
    // Distance from the top of the line to the bottom edge in scaled pixels
    private static final int BOTTOM = 15;

    // Save count the client saw on its last tick
    private static int seen = WorldSaveTracker.started();
    // Client ticks left that the line keeps rising
    private static int hold;
    // Fade level after the last tick from 0 hidden to 1 shown.
    private static float value;
    // Fade level after the tick before the last.
    private static float last;

    // Prevents instantiation
    private AutosaveIndicator() {
    }

    // Moves the fade one tick toward shown during a save and toward hidden otherwise.
    public static void tick(boolean integratedServer) {
        int count = WorldSaveTracker.started();
        if (!integratedServer) {
            seen = count;
            hold = 0;
            value = 0F;
            last = 0F;
            return;
        }
        if (count != seen) {
            seen = count;
            hold = MIN_TICKS;
        }
        boolean show = hold > 0 || WorldSaveTracker.saving();
        if (hold > 0) {
            hold--;
        }
        last = value;
        value += ((show ? 1F : 0F) - value) * FADE;
    }

    // Returns the alpha of the line between the last two ticks from 0 to 255.
    static int alpha(float partialTicks) {
        float level = last + (value - last) * partialTicks;
        return (int) (255F * Math.min(Math.max(level, 0F), 1F));
    }

    // Draws the line when the option is on and the fade is high enough to see
    public static void render(Minecraft client, ScaledResolution resolution, float partialTicks) {
        int alpha = alpha(partialTicks);
        if (alpha <= MIN_ALPHA || !ImpetusVintage.options().notifications.autosaveIndicator) {
            return;
        }

        FontRenderer font = client.fontRenderer;
        String text = I18n.format(KEY);
        int x = resolution.getScaledWidth() - font.getStringWidth(text) - RIGHT;
        int y = resolution.getScaledHeight() - BOTTOM;
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        font.drawString(text, x, y, 0xFFFFFF | alpha << 24, true);
        GlStateManager.disableBlend();
    }
}
