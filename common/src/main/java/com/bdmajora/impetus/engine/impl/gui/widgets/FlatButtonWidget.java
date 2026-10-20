package com.bdmajora.impetus.engine.impl.gui.widgets;

import com.bdmajora.impetus.engine.impl.gui.framework.DrawContext;
import com.bdmajora.impetus.engine.impl.gui.framework.Focusable;
import com.bdmajora.impetus.engine.impl.gui.framework.InteractionContext;
import com.bdmajora.impetus.engine.impl.gui.framework.Keys;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.engine.impl.util.Dim2i;
import com.bdmajora.impetus.engine.impl.gui.theme.DefaultColors;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class FlatButtonWidget extends AbstractWidget implements Focusable {
    protected final Dim2i dim;
    private final Runnable action;

    private @NotNull Style style = Style.defaults();

    private boolean selected;
    private boolean enabled = true;
    private boolean visible = true;
    private boolean leftAligned;
    private boolean focused;

    private TextComponent label;

    public FlatButtonWidget(Dim2i dim, TextComponent label, Runnable action) {
        this.dim = dim;
        this.label = label;
        this.action = action;
    }

    // Indent for left-aligned labels
    protected int getLeftAlignedTextOffset(DrawContext drawContext) {
        return 10;
    }

    // Bounds test, only when enabled
    protected boolean isHovered(int mouseX, int mouseY) {
        return this.dim.containsCursor(mouseX, mouseY);
    }

    // Background by state, then the label centred or left-aligned; focus looks hovered and adds an accent outline
    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float delta) {
        if (!this.visible) {
            return;
        }

        boolean hovered = this.focused || this.isHovered(mouseX, mouseY);

        int backgroundColor = this.enabled ? (hovered ? this.style.bgHovered : this.style.bgDefault) : this.style.bgDisabled;
        int textColor = this.enabled ? (this.selected ? this.style.textSelected : this.style.textDefault) : this.style.textDisabled;

        int strWidth = drawContext.getStringWidth(this.label);

        drawContext.fill(this.dim.x(), this.dim.y(), this.dim.getLimitX(), this.dim.getLimitY(), backgroundColor);
        int textX;
        if (this.leftAligned) {
            textX = this.dim.x() + this.getLeftAlignedTextOffset(drawContext);
        } else {
            textX = this.dim.getCenterX() - (strWidth / 2);
        }
        drawContext.drawString(this.label, textX, this.dim.getCenterY() - 4, textColor);

        if (this.enabled && this.selected) {
            drawContext.fill(this.dim.x(), this.leftAligned ? this.dim.y() : (this.dim.getLimitY() - 1), this.leftAligned ? (this.dim.x() + 1) : this.dim.getLimitX(), this.dim.getLimitY(), this.style.accentColor);
        }

        if (this.focused) {
            drawContext.drawBorder(this.dim.x(), this.dim.y(), this.dim.getLimitX(), this.dim.getLimitY(), this.style.accentColor);
        }
    }

    // Replaces the colour set
    public void setStyle(@NotNull Style style) {
        Objects.requireNonNull(style);

        this.style = style;
    }

    // Highlighted state, for the active tab
    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    // Label alignment
    public void setLeftAligned(boolean leftAligned) {
        this.leftAligned = leftAligned;
    }

    // Runs the action on left click when enabled and hovered
    @Override
    public boolean mouseClicked(InteractionContext context, double mouseX, double mouseY, int button) {
        if (!this.enabled || !this.visible) {
            return false;
        }

        if (button == 0 && this.dim.containsCursor(mouseX, mouseY)) {
            doAction(context);

            return true;
        }

        return false;
    }

    // Enter, keypad Enter or Space presses the button while it has focus and is shown and enabled, as a click would need
    @Override
    public boolean keyTyped(InteractionContext context, char typedChar, int keyCode) {
        if (this.focused && this.canFocus() && Keys.isPress(typedChar, keyCode)) {
            doAction(context);

            return true;
        }

        return false;
    }

    // Plays the click and runs the action
    private void doAction(InteractionContext context) {
        this.action.run();
        context.playClickSound();
    }

    // Greys out and ignores clicks
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    // Hidden buttons draw nothing and ignore input
    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    // Replaces the label
    public void setLabel(TextComponent text) {
        this.label = text;
    }

    // Current label
    public TextComponent getLabel() {
        return this.label;
    }

    // Bounds test, only when visible
    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return this.dim.containsCursor(mouseX, mouseY);
    }

    // Tab skips a hidden or greyed-out button
    @Override
    public boolean canFocus() {
        return this.enabled && this.visible;
    }

    // Whether Enter and Space go here
    @Override
    public boolean isFocused() {
        return this.focused;
    }

    // Set by the container when Tab or a click moves focus
    @Override
    public void setFocused(boolean focused) {
        this.focused = focused;
    }

    // Bounds
    @Override
    public Dim2i getDimensions() {
        return this.dim;
    }

    public static class Style {
        public int bgHovered, bgDefault, bgDisabled;
        public int textDefault, textSelected, textDisabled;
        public int accentColor;

        // The standard flat colour set
        public static Style defaults() {
            var style = new Style();
            style.bgHovered = DefaultColors.BACKGROUND_HOVERED;
            style.bgDefault = DefaultColors.BACKGROUND_DEFAULT;
            style.bgDisabled = DefaultColors.BACKGROUND_DISABLED;
            style.textDefault = 0xFFFFFFFF;
            style.textSelected = 0xFFFFFFFF;
            style.textDisabled = 0x90FFFFFF;
            style.accentColor = DefaultColors.ELEMENT_ACTIVATED;

            return style;
        }
    }
}
