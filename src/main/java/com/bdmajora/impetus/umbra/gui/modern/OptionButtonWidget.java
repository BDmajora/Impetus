package com.bdmajora.impetus.umbra.gui.modern;

import com.bdmajora.impetus.engine.impl.gui.framework.DrawContext;
import com.bdmajora.impetus.engine.impl.gui.framework.InteractionContext;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.engine.impl.gui.widgets.AbstractWidget;
import com.bdmajora.impetus.engine.impl.util.Dim2i;

import java.util.List;

// Button of a shader pack option with a label and a value.
public class OptionButtonWidget extends AbstractWidget {
    // Background color of an idle button.
    private static final int BG_DEFAULT = 0x40101010;
    // Background color of a hovered button.
    private static final int BG_HOVERED = 0x90101010;
    // Background color of a disabled button.
    private static final int BG_DISABLED = 0x20101010;
    // Border color of an idle button.
    private static final int BORDER_DEFAULT = 0xFF404040;
    // Border color of a hovered button.
    private static final int BORDER_HOVERED = 0xFF9A9A9A;
    // Text color of an enabled button.
    private static final int TEXT_DEFAULT = 0xFFE0E0E0;
    // Text color of a disabled button.
    private static final int TEXT_DISABLED = 0xFF707070;

    // Bounds of the button.
    private final Dim2i dim;
    // Label of the button.
    private final TextComponent label;
    // Value text or null.
    private final String value;
    // Value as a component or null.
    private final TextComponent valueText;
    // Color of the value text.
    private final int valueColor;
    // Centered state of the label.
    private final boolean centered;
    // Input state of the button.
    private final boolean enabled;
    // Action of a left click or null.
    private final Runnable onLeft;
    // Action of a right click or null.
    private final Runnable onRight;
    // Tooltip lines.
    private final List<String> tooltip;

    // Creates a button.
    public OptionButtonWidget(Dim2i dim, TextComponent label, String value, int valueColor, boolean centered,
                              boolean enabled, Runnable onLeft, Runnable onRight, List<String> tooltip) {
        this.dim = dim;
        this.label = label;
        this.value = value;
        this.valueText = value == null ? null : TextComponent.literal(value);
        this.valueColor = valueColor;
        this.centered = centered;
        this.enabled = enabled;
        this.onLeft = onLeft;
        this.onRight = onRight;
        this.tooltip = tooltip;
    }

    // Draws the background and border and the label and value.
    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float delta) {
        boolean hovered = this.enabled && this.dim.containsCursor(mouseX, mouseY);

        drawContext.fill(this.dim.x(), this.dim.y(), this.dim.getLimitX(), this.dim.getLimitY(),
                this.enabled ? (hovered ? BG_HOVERED : BG_DEFAULT) : BG_DISABLED);
        drawContext.drawBorder(this.dim.x(), this.dim.y(), this.dim.getLimitX(), this.dim.getLimitY(),
                hovered ? BORDER_HOVERED : BORDER_DEFAULT);

        int textColor = this.enabled ? TEXT_DEFAULT : TEXT_DISABLED;
        int textY = this.dim.getCenterY() - 4;

        if (this.centered || this.value == null) {
            int width = drawContext.getStringWidth(this.label);
            drawContext.drawString(this.label, this.dim.getCenterX() - width / 2, textY, textColor);
        } else {
            drawContext.drawString(this.label, this.dim.x() + 8, textY, textColor);
            int valueWidth = drawContext.getStringWidth(this.valueText);
            drawContext.drawString(this.valueText, this.dim.getLimitX() - 8 - valueWidth, textY,
                    this.enabled ? this.valueColor : TEXT_DISABLED);
        }
    }

    // Runs the action of a left or right click inside an enabled button.
    @Override
    public boolean mouseClicked(InteractionContext context, double mouseX, double mouseY, int button) {
        if (!this.enabled || !this.dim.containsCursor(mouseX, mouseY)) {
            return false;
        }
        if (button == 0 && this.onLeft != null) {
            this.onLeft.run();
            context.playClickSound();
            return true;
        }
        if (button == 1 && this.onRight != null) {
            this.onRight.run();
            context.playClickSound();
            return true;
        }
        return false;
    }

    // Returns whether the cursor is over the button.
    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return this.dim.containsCursor(mouseX, mouseY);
    }

    // Returns whether the button takes input.
    public boolean isEnabled() {
        return this.enabled;
    }

    // Returns the tooltip lines.
    public List<String> getTooltip() {
        return this.tooltip;
    }

    // Returns the bounds of the button.
    public Dim2i getDim() {
        return this.dim;
    }
}
