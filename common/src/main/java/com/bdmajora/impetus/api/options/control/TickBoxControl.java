package com.bdmajora.impetus.api.options.control;

import com.bdmajora.impetus.api.options.structure.Option;
import com.bdmajora.impetus.engine.impl.gui.framework.DrawContext;
import com.bdmajora.impetus.engine.impl.gui.framework.InteractionContext;
import com.bdmajora.impetus.engine.impl.gui.framework.Keys;
import com.bdmajora.impetus.engine.impl.util.Dim2i;

public class TickBoxControl implements Control<Boolean> {
    private final Option<Boolean> option;

    public TickBoxControl(Option<Boolean> option) {
        this.option = option;
    }

    // The checkbox widget
    @Override
    public ControlElement<Boolean> createElement(Dim2i dim) {
        return new TickBoxControlElement(this.option, dim);
    }

    // Fixed box width
    @Override
    public int getMaxWidth() {
        return 30;
    }

    // Bound option
    @Override
    public Option<Boolean> getOption() {
        return this.option;
    }

    private static class TickBoxControlElement extends ControlElement<Boolean> {
        private final Dim2i button;

        public TickBoxControlElement(Option<Boolean> option, Dim2i dim) {
            super(option, dim);

            this.button = new Dim2i(dim.getLimitX() - 16, dim.getCenterY() - 5, 10, 10);
        }

        // Box with a tick when set
        @Override
        public void render(DrawContext drawContext, int mouseX, int mouseY, float delta) {
            super.render(drawContext, mouseX, mouseY, delta);

            final int x = this.button.x();
            final int y = this.button.y();
            final int w = x + this.button.width();
            final int h = y + this.button.height();

            final boolean enabled = this.option.isAvailable();
            final boolean ticked = this.option.getValue();

            final int color;

            if (enabled) {
                color = ticked ? this.getAccentColor(drawContext) : 0xFFFFFFFF;
            } else {
                color = this.getDisabledControlColor();
            }

            if (ticked) {
                drawContext.fill(x + 2, y + 2, w - 2, h - 2, color);
            }

            drawContext.drawBorder(x, y, w, h, color);
        }

        // Toggles on click
        @Override
        public boolean mouseClicked(InteractionContext context, double mouseX, double mouseY, int button) {
            if (this.option.isAvailable() && button == 0 && this.dim.containsCursor(mouseX, mouseY)) {
                toggleControl();
                context.playClickSound();

                return true;
            }

            return false;
        }

        // Enter, keypad Enter or Space toggles while focused, as a click does
        @Override
        public boolean keyTyped(InteractionContext context, char typedChar, int keyCode) {
            if (this.isFocused() && Keys.isPress(typedChar, keyCode) && this.option.isAvailable()) {
                toggleControl();
                context.playClickSound();

                return true;
            }

            return false;
        }

        // Flips the value
        public void toggleControl() {
            this.option.setValue(!this.option.getValue());
        }
    }
}
