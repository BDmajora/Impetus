package com.bdmajora.impetus.engine.impl.gui.framework;

import com.bdmajora.impetus.engine.impl.gui.theme.DefaultColors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// Drawing surface for GUI elements.
public interface DrawContext extends FontMetricsProvider {
    // Fills a rectangle with a color.
    void fill(int x1, int y1, int x2, int y2, int color);

    // Draws a string without a shadow and returns the end position.
    default int drawString(String str, int x, int y, int color) {
        return drawString(TextComponent.literal(str), x, y, color);
    }

    // Draws a component without a shadow and returns the end position.
    default int drawString(TextComponent str, int x, int y, int color) {
        return drawString(str, x, y, color, false);
    }

    // Draws a component with an optional shadow and returns the end position.
    int drawString(TextComponent str, int x, int y, int color, boolean shadow);

    // Returns a number that changes whenever text may translate or measure differently.
    default long textVersion() {
        return 0L;
    }

    // Draws a whole image into a rectangle.
    void blitWholeImage(@NotNull String icon, int x, int y, int width, int height);

    // Saves the current transform.
    void pushMatrix();

    // Moves the origin of later drawing.
    void translate(double x, double y, double z);

    // Restores the last saved transform.
    void popMatrix();

    // Clips later drawing to a rectangle.
    void enableScissor(int x1, int y1, int x2, int y2);

    // Removes the last clip.
    void disableScissor();

    // Returns the number of saved transforms or zero when not counted.
    default int matrixDepth() {
        return 0;
    }

    // Returns the number of active clips or zero when not counted.
    default int scissorDepth() {
        return 0;
    }

    // Pops transforms and clips beyond the given depths.
    default void unwindTo(int matrixDepth, int scissorDepth) {
        for (int i = this.matrixDepth() - matrixDepth; i > 0; i--) {
            this.popMatrix();
        }
        for (int i = this.scissorDepth() - scissorDepth; i > 0; i--) {
            this.disableScissor();
        }
    }

    // Draws a one pixel outline with four fills.
    default void drawBorder(int x1, int y1, int x2, int y2, int color) {
        fill(x1, y1, x2, y1 + 1, color);
        fill(x1, y2 - 1, x2, y2, color);
        fill(x1, y1, x1 + 1, y2, color);
        fill(x2 - 1, y1, x2, y2, color);
    }

    // Returns the logo path of a mod or null.
    default @Nullable String getModLogoPath(String modId) {
        return null;
    }

    // Returns the display name of a mod.
    default TextComponent getFriendlyModName(String modId) {
        return TextComponent.literal(modId);
    }

    // Returns the accent color of a mod.
    default int getModAccentColor(String modId) {
        return DefaultColors.getModAccentColor(modId);
    }
}
