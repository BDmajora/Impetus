package com.bdmajora.impetus.engine.impl.gui.frame.tab;

import com.bdmajora.impetus.engine.impl.gui.framework.DrawContext;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.engine.impl.gui.widgets.FlatButtonWidget;
import com.bdmajora.impetus.engine.impl.util.Dim2i;

import java.util.Objects;

// Sidebar header of a group of pages that folds the group on a click.
public class TabHeaderWidget extends FlatButtonWidget {
    // Icon used when a mod has no logo.
    private static final String FALLBACK_TEXTURE = "textures/misc/unknown_pack.png";
    // Side of the icon in pixels.
    private static final int ICON_SIZE = 20;
    // Space around the icon in pixels.
    private static final int ICON_PADDING = 5;
    // Space right of the fold marker in pixels.
    private static final int MARKER_PADDING = 6;
    // Color of the hover and focus wash.
    private static final int HOVER_COLOR = 0x30FFFFFF;

    // Height of the header row.
    public static final int HEIGHT = 30;
    // Width reserved for the fold marker.
    public static final int MARKER_WIDTH = 14;

    // Id of the group.
    private final String modId;
    // Fold state of the group.
    private final boolean collapsed;

    // Creates a header and rejects a null group id.
    public TabHeaderWidget(Dim2i dim, String modId, boolean collapsed, Runnable onToggle) {
        super(dim, TextComponent.literal(""), onToggle);
        this.modId = Objects.requireNonNull(modId, "modId");
        this.collapsed = collapsed;
    }

    // Draws the icon and name and fold marker and a wash when hovered or focused.
    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float delta) {
        if (this.isFocused() || this.isHovered(mouseX, mouseY)) {
            drawContext.fill(this.dim.x(), this.dim.y(), this.dim.getLimitX(), this.dim.getLimitY(), HOVER_COLOR);
        }

        String icon = Objects.requireNonNullElse(drawContext.getModLogoPath(this.modId), FALLBACK_TEXTURE);
        int iconY = this.dim.getCenterY() - (ICON_SIZE / 2);
        drawContext.blitWholeImage(icon, this.dim.x() + ICON_PADDING, iconY, ICON_SIZE, ICON_SIZE);

        // Starts the text after the icon and its padding
        int textX = this.dim.x() + ICON_PADDING + ICON_SIZE + ICON_PADDING;
        var name = drawContext.getFriendlyModName(this.modId);
        int accentColor = drawContext.getModAccentColor(this.modId);
        int textY = this.dim.getCenterY() - (drawContext.lineHeight() / 2);

        // Centers the text on the row
        drawContext.drawString(name, textX, textY, accentColor);

        String marker = this.collapsed ? "+" : "-";
        int markerX = this.dim.getLimitX() - MARKER_PADDING - drawContext.getStringWidth(marker);
        drawContext.drawString(marker, markerX, textY, accentColor);

        if (this.isFocused()) {
            drawContext.drawBorder(this.dim.x(), this.dim.y(), this.dim.getLimitX(), this.dim.getLimitY(), accentColor);
        }
    }
}
