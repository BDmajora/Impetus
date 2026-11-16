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

// Flat button with a label and an action.
public class FlatButtonWidget extends AbstractWidget implements Focusable {
    // Bounds of the button.
    protected final Dim2i dim;
    // Action run on a click.
    private final Runnable action;

    // Colors of the button.
    private @NotNull Style style = Style.defaults();

    // Selection highlight state of the button.
    private boolean selected;
    // Input state of the button.
    private boolean enabled = true;
    // Visibility state of the button.
    private boolean visible = true;
    // Left alignment state of the label.
    private boolean leftAligned;
    // Focus state of the button.
    private boolean focused;

    // Text of the button.
    private TextComponent label;

    // Creates a button and rejects a null bound or label or action.
    public FlatButtonWidget(Dim2i dim, TextComponent label, Runnable action) {
        this.dim = Objects.requireNonNull(dim, "dim");
        this.label = Objects.requireNonNull(label, "label");
        this.action = Objects.requireNonNull(action, "action");
    }

    // Returns the indent of a left aligned label.
    protected int getLeftAlignedTextOffset(DrawContext drawContext) {
        return 10;
    }

    // Returns whether the cursor is over the button.
    protected boolean isHovered(int mouseX, int mouseY) {
        return this.dim.containsCursor(mouseX, mouseY);
    }

    // Draws the background and the label and an outline when focused.
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

    // Stores the colors and rejects null.
    public void setStyle(@NotNull Style style) {
        Objects.requireNonNull(style, "style");

        this.style = style;
    }

    // Stores whether the button is selected.
    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    // Stores whether the label starts at the left.
    public void setLeftAligned(boolean leftAligned) {
        this.leftAligned = leftAligned;
    }

    // Runs the action on a left click inside an enabled and visible button.
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

    // Runs the action on Enter or Space while the button holds focus.
    @Override
    public boolean keyTyped(InteractionContext context, char typedChar, int keyCode) {
        if (this.focused && this.canFocus() && Keys.isPress(typedChar, keyCode)) {
            doAction(context);

            return true;
        }

        return false;
    }

    // Runs the action and plays the click sound.
    private void doAction(InteractionContext context) {
        this.action.run();
        context.playClickSound();
    }

    // Stores whether the button reacts to input.
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    // Stores whether the button draws and reacts.
    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    // Stores the label and rejects null.
    public void setLabel(TextComponent text) {
        this.label = Objects.requireNonNull(text, "text");
    }

    // Returns the label.
    public TextComponent getLabel() {
        return this.label;
    }

    // Returns whether the cursor is over a visible button.
    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return this.visible && this.dim.containsCursor(mouseX, mouseY);
    }

    // Returns whether the button is visible and enabled.
    @Override
    public boolean canFocus() {
        return this.enabled && this.visible;
    }

    // Returns whether the button holds focus.
    @Override
    public boolean isFocused() {
        return this.focused;
    }

    // Stores whether the button holds focus.
    @Override
    public void setFocused(boolean focused) {
        this.focused = focused;
    }

    // Returns the bounds of the button.
    @Override
    public Dim2i getDimensions() {
        return this.dim;
    }

    // Colors of a button.
    public static class Style {
        // Background colors by state.
        public int bgHovered, bgDefault, bgDisabled;
        // Text colors by state.
        public int textDefault, textSelected, textDisabled;
        // Color of the accent lines.
        public int accentColor;

        // Creates the standard colors.
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
