package com.bdmajora.impetus.api.options.control;

import com.bdmajora.impetus.api.options.structure.Option;
import com.bdmajora.impetus.engine.impl.gui.framework.DrawContext;
import com.bdmajora.impetus.engine.impl.gui.framework.InteractionContext;
import com.bdmajora.impetus.engine.impl.gui.framework.Keys;
import com.bdmajora.impetus.engine.impl.util.Dim2i;

import java.util.Objects;

// Control that toggles a boolean option with a tick box
public class TickBoxControl implements Control<Boolean> {
    // Width of the control column
    private static final int COLUMN_WIDTH = 30;

    private final Option<Boolean> option;

    // Stores the option and rejects a null option
    public TickBoxControl(Option<Boolean> option) {
        this.option = Objects.requireNonNull(option, "Option must not be null");
    }

    // Creates the tick box element at the given bounds
    @Override
    public ControlElement<Boolean> createElement(Dim2i dim) {
        return new TickBoxControlElement(this.option, dim);
    }

    // Returns the width of the control column
    @Override
    public int getMaxWidth() {
        return COLUMN_WIDTH;
    }

    // Returns the option this control edits
    @Override
    public Option<Boolean> getOption() {
        return this.option;
    }

    // Row that draws the tick box and toggles the option
    private static final class TickBoxControlElement extends ControlElement<Boolean> {
        // Side of the square box and its gap from the right edge of the row and the gap of the tick inside it
        private static final int BOX_SIZE = 10;
        private static final int BOX_RIGHT_INSET = 6;
        private static final int TICK_INSET = 2;
        // Outline colour of an empty box that can be edited
        private static final int EMPTY_COLOR = 0xFFFFFFFF;

        // Whether the row has an area large enough to hold the whole box
        private final boolean boxFits;

        // Creates the row and checks whether the box fits inside it
        TickBoxControlElement(Option<Boolean> option, Dim2i dim) {
            super(option, dim);

            // Requires room for the box and its inset in the width and the box side in the height
            this.boxFits = this.hasArea() && dim.width() >= BOX_RIGHT_INSET + BOX_SIZE && dim.height() >= BOX_SIZE;
        }

        // Draws the box with a tick when set and skips it on a row too small to hold it
        @Override
        public void render(DrawContext drawContext, int mouseX, int mouseY, float delta) {
            super.render(drawContext, mouseX, mouseY, delta);
            if (!this.boxFits) {
                return;
            }

            final int right = this.dim.getLimitX() - BOX_RIGHT_INSET;
            final int x = right - BOX_SIZE;
            final int y = this.dim.getCenterY() - BOX_SIZE / 2;
            final int bottom = y + BOX_SIZE;

            final boolean enabled = this.isDrawnAvailable();
            final boolean ticked = this.isTicked();

            final int color;

            if (enabled) {
                color = ticked ? this.getAccentColor(drawContext) : EMPTY_COLOR;
            } else {
                color = this.getDisabledControlColor();
            }

            if (ticked) {
                drawContext.fill(x + TICK_INSET, y + TICK_INSET, right - TICK_INSET, bottom - TICK_INSET, color);
            }

            drawContext.drawBorder(x, y, right, bottom, color);
        }

        // Toggles the value on a left click inside a row that has an area
        @Override
        public boolean mouseClicked(InteractionContext context, double mouseX, double mouseY, int button) {
            if (button == 0 && this.hasArea() && this.dim.containsCursor(mouseX, mouseY) && this.isOptionAvailable()) {
                this.toggleControl();
                context.playClickSound();

                return true;
            }

            return false;
        }

        // Toggles the value on Enter or Space while focused on a row that has an area
        @Override
        public boolean keyTyped(InteractionContext context, char typedChar, int keyCode) {
            if (this.isFocused() && this.hasArea() && Keys.isPress(typedChar, keyCode) && this.isOptionAvailable()) {
                this.toggleControl();
                context.playClickSound();

                return true;
            }

            return false;
        }

        // Flips the value
        private void toggleControl() {
            this.option.setValue(!this.isTicked());
        }

        // Returns true when the option holds true and false for a null value
        private boolean isTicked() {
            // Reads the value as a Boolean
            Boolean value = this.option.getValue();
            return value != null && value;
        }
    }
}