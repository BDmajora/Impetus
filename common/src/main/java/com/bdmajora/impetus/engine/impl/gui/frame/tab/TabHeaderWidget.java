package com.bdmajora.impetus.engine.impl.gui.frame.tab;

import com.bdmajora.impetus.engine.impl.gui.framework.DrawContext;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.engine.impl.gui.widgets.FlatButtonWidget;
import com.bdmajora.impetus.engine.impl.util.Dim2i;

import java.util.Objects;

// Sidebar header for a mod's group of pages, icon and name on one line, and a click folds or unfolds the pages beneath it; the fold marker on the right is plain "+" / "-" since the default font has no triangle glyphs
public class TabHeaderWidget extends FlatButtonWidget {
    private static final String FALLBACK_TEXTURE = "textures/misc/unknown_pack.png";
    private static final int ICON_SIZE = 20;
    private static final int ICON_PADDING = 5;
    private static final int MARKER_PADDING = 6;
    private static final int HOVER_COLOR = 0x30FFFFFF;

    // Row height used by the sidebar when laying this widget out
    public static final int HEIGHT = 30;
    // Room the sidebar reserves on the right of the name for the fold marker
    public static final int MARKER_WIDTH = 14;

    private final String modId;
    private final boolean collapsed;

    public TabHeaderWidget(Dim2i dim, String modId, boolean collapsed, Runnable onToggle) {
        super(dim, TextComponent.literal(""), onToggle);
        this.modId = modId;
        this.collapsed = collapsed;
    }

    // Icon, name, fold marker and a faint hover wash; no button background so the header reads as a heading rather than a row
    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float delta) {
        if (this.isHovered(mouseX, mouseY)) {
            drawContext.fill(this.dim.x(), this.dim.y(), this.dim.getLimitX(), this.dim.getLimitY(), HOVER_COLOR);
        }

        String icon = Objects.requireNonNullElse(drawContext.getModLogoPath(this.modId), FALLBACK_TEXTURE);
        int iconY = this.dim.getCenterY() - (ICON_SIZE / 2);
        drawContext.blitWholeImage(icon, this.dim.x() + ICON_PADDING, iconY, ICON_SIZE, ICON_SIZE);

        // Text starts past the icon and both paddings so every heading lines up whether the mod supplied a logo or fell back
        int textX = this.dim.x() + ICON_PADDING + ICON_SIZE + ICON_PADDING;
        var name = drawContext.getFriendlyModName(this.modId);
        int accentColor = drawContext.getModAccentColor(this.modId);
        int textY = this.dim.getCenterY() - (drawContext.lineHeight() / 2);

        // Vertically centred against the row rather than the icon, so the name stays put if ICON_SIZE changes
        drawContext.drawString(name, textX, textY, accentColor);

        String marker = this.collapsed ? "+" : "-";
        int markerX = this.dim.getLimitX() - MARKER_PADDING - drawContext.getStringWidth(marker);
        drawContext.drawString(marker, markerX, textY, accentColor);
    }
}
