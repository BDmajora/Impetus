package com.bdmajora.impetus.impl.gui.overlay;

import com.bdmajora.impetus.ImpetusVintage;
import com.bdmajora.impetus.engine.impl.gui.theme.DefaultColors;
import com.bdmajora.impetus.engine.impl.notification.ImpetusNotification;
import com.bdmajora.impetus.engine.impl.notification.ImpetusNotifications;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;

import java.util.List;

// Draws the visible notifications as toasts in the top right corner.
public final class ImpetusToastRenderer {
    // Width of a toast in scaled pixels.
    private static final int WIDTH = 250;
    // Inner padding of a toast.
    private static final int PADDING = 6;
    // Space between toasts.
    private static final int GAP = 4;

    // Prevents instantiation
    private ImpetusToastRenderer() {
    }

    // Draws every visible notification when toasts are on.
    public static void render(Minecraft client, ScaledResolution resolution) {
        if (!ImpetusVintage.options().notifications.showToasts) {
            return;
        }

        List<ImpetusNotification> notifications = ImpetusNotifications.getVisible();
        if (notifications.isEmpty()) {
            return;
        }

        FontRenderer font = client.fontRenderer;
        int x = resolution.getScaledWidth() - WIDTH - 8;
        int y = 8;

        for (ImpetusNotification notification : notifications) {
            int height = (notification.lines().size() + 1) * (font.FONT_HEIGHT + 2) + (PADDING * 2);
            int accent = accentColor(notification.level());

            Gui.drawRect(x, y, x + WIDTH, y + height, 0xD0000000);
            Gui.drawRect(x, y, x + 2, y + height, accent);

            int textWidth = WIDTH - (PADDING * 2) - 4;
            font.drawString(trim(font, notification.title(), textWidth), x + PADDING + 2, y + PADDING, 0xFFFFFFFF, true);

            int lineY = y + PADDING + font.FONT_HEIGHT + 4;
            for (String line : notification.lines()) {
                font.drawString(trim(font, line, textWidth), x + PADDING + 2, lineY, 0xFFD0D0D0, true);
                lineY += font.FONT_HEIGHT + 2;
            }

            y += height + GAP;
        }
    }

    // Returns the accent color of a level.
    private static int accentColor(ImpetusNotification.Level level) {
        return switch (level) {
            case INFO -> DefaultColors.ELEMENT_ACTIVATED;
            case WARNING -> 0xFFFFB86C;
            case ERROR -> 0xFFFF5555;
        };
    }

    // Returns a text cut to a width with an ellipsis.
    private static String trim(FontRenderer font, String text, int width) {
        return font.getStringWidth(text) <= width ? text : font.trimStringToWidth(text, width - font.getStringWidth("...")) + "...";
    }
}
