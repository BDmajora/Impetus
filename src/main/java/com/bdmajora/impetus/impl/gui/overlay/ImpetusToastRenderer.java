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

public final class ImpetusToastRenderer {
    private static final int WIDTH = 250;
    private static final int PADDING = 6;
    private static final int GAP = 4;

    private ImpetusToastRenderer() {
    }

    // Draws the pending notification in the top-right corner
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

    // Bar colour per severity
    private static int accentColor(ImpetusNotification.Level level) {
        return switch (level) {
            case INFO -> DefaultColors.ELEMENT_ACTIVATED;
            case WARNING -> 0xFFFFB86C;
            case ERROR -> 0xFFFF5555;
        };
    }

    // Truncates with an ellipsis to fit the toast
    private static String trim(FontRenderer font, String text, int width) {
        return font.getStringWidth(text) <= width ? text : font.trimStringToWidth(text, width - font.getStringWidth("...")) + "...";
    }
}
