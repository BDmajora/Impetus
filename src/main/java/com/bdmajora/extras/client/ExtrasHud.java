package com.bdmajora.extras.client;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.ExtrasConfig;
import com.bdmajora.extras.client.budget.BottleneckDetector;
import com.bdmajora.extras.client.budget.RenderBudget;
import com.bdmajora.extras.client.budget.RenderBudgetController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Overlay with the frame rate and coordinates and budget details.
@Mod.EventBusSubscriber(Side.CLIENT)
@SideOnly(Side.CLIENT)
public final class ExtrasHud {
    // Color of the text.
    private static final int TEXT_COLOR = 0xFFFFFF;
    // Color of the text background.
    private static final int BACKGROUND_COLOR = 0x90505050;
    // Distance from the screen edge.
    private static final int MARGIN = 2;

    // Prevents instantiation
    private ExtrasHud() {
    }

    // Draws the overlay lines in the chosen corner.
    @SubscribeEvent
    public static void onRenderOverlay(RenderGameOverlayEvent.Text event) {
        Minecraft minecraft = Minecraft.getMinecraft();

        if (minecraft.gameSettings.showDebugInfo || minecraft.gameSettings.hideGUI) {
            return;
        }

        ExtrasConfig options = Extras.options();
        List<String> lines = collectLines(minecraft, options);

        if (lines.isEmpty()) {
            return;
        }

        FontRenderer font = minecraft.fontRenderer;
        ScaledResolution resolution = event.getResolution();
        ExtrasConfig.OverlayCorner corner = options.extra.overlayCorner;

        int lineHeight = font.FONT_HEIGHT + 2;
        int y = corner.isBottom()
                ? resolution.getScaledHeight() - font.FONT_HEIGHT - MARGIN
                : MARGIN;

        for (String line : lines) {
            int x = corner.isRight()
                    ? resolution.getScaledWidth() - font.getStringWidth(line) - MARGIN
                    : MARGIN;

            drawLine(font, line, x, y, options.extra.textContrast);

            y += corner.isBottom() ? -lineHeight : lineHeight;
        }
    }

    // Returns the lines to draw.
    private static List<String> collectLines(Minecraft minecraft, ExtrasConfig options) {
        List<String> lines = new ArrayList<>();
        ExtrasConfig.ExtraSettings settings = options.extra;

        if (settings.showFps) {
            String text = I18n.format("impetus.options.extras.overlay.fps", Minecraft.getDebugFPS());

            if (settings.showFpsExtended) {
                text = text + " " + I18n.format("impetus.options.extras.overlay.fps_extended",
                        FrameCounter.getAverageFps(),
                        FrameCounter.getOnePercentLowFps(),
                        FrameCounter.getPointOnePercentLowFps());
            }

            lines.add(text);
        }

        if (settings.showCoords
                && (settings.ignoreReducedDebugInfo || !minecraft.gameSettings.reducedDebugInfo)) {
            EntityPlayer player = minecraft.player;
            if (player != null) {
                lines.add(I18n.format("impetus.options.extras.overlay.coordinates",
                        String.format("%.2f", player.posX),
                        String.format("%.2f", player.posY),
                        String.format("%.2f", player.posZ)));
            }
        }

        if (!options.render.lightUpdates) {
            lines.add(I18n.format("impetus.options.extras.overlay.light_updates"));
        }

        if (options.renderBudget.overlay) {
            if (options.renderBudget.enabled) {
                addBudgetLines(lines, options.renderBudget);
            } else {
                lines.add(I18n.format("impetus.options.extras.overlay.budget.off"));
            }
        }

        return lines;
    }

    // Adds the lines of the render budget.
    private static void addBudgetLines(List<String> lines, ExtrasConfig.RenderBudgetSettings settings) {
        RenderBudget budget = RenderBudgetController.budget();

        lines.add(I18n.format("impetus.options.extras.overlay.budget",
                settings.profile.localizedName(),
                String.format(Locale.ROOT, "%.1f", budget.emaFrameMillis),
                String.format(Locale.ROOT, "%.1f", budget.targetFrameMillis),
                Math.round(budget.framePressure * 100.0),
                budget.adaptiveActive ? I18n.format("impetus.options.extras.overlay.budget.adaptive") : "",
                bottleneckLabel(budget.bottleneck)));

        lines.add(I18n.format("impetus.options.extras.overlay.budget.limits",
                Math.round(budget.particleScale * 100.0),
                distanceLimit(budget.limitsEntities(), budget.entityCullDistance, budget.armed),
                distanceLimit(budget.limitsBlockEntities(), budget.blockEntityCullDistance, budget.armed)));

        lines.add(I18n.format("impetus.options.extras.overlay.budget.counters",
                RenderBudgetController.entitiesSkipped(), RenderBudgetController.entitiesProtected(),
                RenderBudgetController.particlesSkipped(), RenderBudgetController.particlesProtected(),
                RenderBudgetController.blockEntitiesSkipped(), RenderBudgetController.blockEntitiesProtected(),
                RenderBudgetController.itemFramesSkipped(), RenderBudgetController.itemFramesProtected()));
    }

    // Returns the text of the bottleneck state.
    private static String bottleneckLabel(BottleneckDetector.State state) {
        return switch (state) {
            case CPU_BOUND -> I18n.format("impetus.options.extras.overlay.budget.cpu_bound");
            case GPU_BOUND -> I18n.format("impetus.options.extras.overlay.budget.gpu_bound");
            case MIXED -> I18n.format("impetus.options.extras.overlay.budget.mixed");
            default -> "";
        };
    }

    // Returns the text of a distance limit.
    private static String distanceLimit(boolean limited, int distance, boolean armed) {
        if (!limited) {
            return I18n.format("options.off");
        }
        return I18n.format(armed
                ? "impetus.options.extras.overlay.budget.distance_armed"
                : "impetus.options.extras.overlay.budget.distance_idle", distance);
    }

    // Draws one line with the chosen contrast.
    private static void drawLine(FontRenderer font, String text, int x, int y,
                                 ExtrasConfig.TextContrast contrast) {
        switch (contrast) {
            case BACKGROUND -> {
                int width = font.getStringWidth(text);
                Gui.drawRect(x - 1, y - 1, x + width + 1, y + font.FONT_HEIGHT + 1, BACKGROUND_COLOR);
                font.drawString(text, x, y, TEXT_COLOR);
            }
            case SHADOW -> font.drawStringWithShadow(text, x, y, TEXT_COLOR);
            default -> font.drawString(text, x, y, TEXT_COLOR);
        }
    }
}
