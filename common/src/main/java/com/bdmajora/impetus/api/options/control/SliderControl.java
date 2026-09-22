package com.bdmajora.impetus.api.options.control;

import com.bdmajora.impetus.engine.api.util.ColorARGB;
import com.bdmajora.impetus.api.options.structure.Option;
import com.bdmajora.impetus.engine.impl.gui.framework.DrawContext;
import com.bdmajora.impetus.engine.impl.gui.framework.InteractionContext;
import com.bdmajora.impetus.engine.impl.util.Dim2i;

public class SliderControl implements Control<Integer> {
    private final Option<Integer> option;

    private final int min, max, interval;

    private final ControlValueFormatter mode;

    // Argument validation with a message
    private static void assertTrue(boolean condition, String msg) {
        if (!condition) {
            throw new IllegalArgumentException(msg);
        }
    }

    public SliderControl(Option<Integer> option, int min, int max, int interval, ControlValueFormatter mode) {
        assertTrue(max > min, "The maximum value must be greater than the minimum value");
        assertTrue(interval > 0, "The slider interval must be greater than zero");
        assertTrue(((max - min) % interval) == 0, "The maximum value must be divisable by the interval");
        assertTrue(mode != null, "The slider mode must not be null");

        this.option = option;
        this.min = min;
        this.max = max;
        this.interval = interval;
        this.mode = mode;
    }

    // The slider widget
    @Override
    public ControlElement<Integer> createElement(Dim2i dim) {
        return new Button(this.option, dim, this.min, this.max, this.interval, this.mode);
    }

    // Bound option
    @Override
    public Option<Integer> getOption() {
        return this.option;
    }

    // Widest the slider needs to be
    @Override
    public int getMaxWidth() {
        return 130;
    }

    private static class Button extends ControlElement<Integer> {
        private static final int THUMB_WIDTH = 2, TRACK_HEIGHT = 1;

        private final Dim2i sliderBounds;
        private final ControlValueFormatter formatter;

        private final int min;
        private final int max;
        private final int range;
        private final int interval;

        private double thumbPosition;

        private boolean sliderHeld;

        public Button(Option<Integer> option, Dim2i dim, int min, int max, int interval, ControlValueFormatter formatter) {
            super(option, dim);

            this.min = min;
            this.max = max;
            this.range = max - min;
            this.interval = interval;
            this.thumbPosition = this.getThumbPositionForValue(option.getValue());
            this.formatter = formatter;

            this.sliderBounds = new Dim2i(dim.getLimitX() - 96, dim.getCenterY() - 5, 90, 10);
            this.sliderHeld = false;
        }

        // Track, thumb and formatted value; a value-only label when not hovered
        @Override
        public void render(DrawContext drawContext, int mouseX, int mouseY, float delta) {
            super.render(drawContext, mouseX, mouseY, delta);

            if (this.option.isAvailable() && (this.isMouseOver(mouseX, mouseY))) {
                this.renderSlider(drawContext);
            } else {
                this.renderStandaloneValue(drawContext);
            }
        }

        // Just the formatted value, when the slider is idle
        private void renderStandaloneValue(DrawContext drawContext) {
            int sliderX = this.sliderBounds.x();
            int sliderY = this.sliderBounds.y();
            int sliderWidth = this.sliderBounds.width();
            int sliderHeight = this.sliderBounds.height();

            var label = this.formatter.format(this.option.getValue());
            boolean enabled = this.option.isAvailable();

            if (!enabled) {
                label = this.formatDisabledControlValue(label);
            }

            int labelWidth = drawContext.getStringWidth(label);

            drawContext.drawString(label, sliderX + sliderWidth - labelWidth, sliderY + (sliderHeight / 2) - 4, enabled ? 0xFFFFFFFF : this.getDisabledControlColor());
        }

        // Track, thumb and value while hovered or dragging
        private void renderSlider(DrawContext drawContext) {
            int sliderX = this.sliderBounds.x();
            int sliderY = this.sliderBounds.y();
            int sliderWidth = this.sliderBounds.width();
            int sliderHeight = this.sliderBounds.height();

            this.thumbPosition = this.getThumbPositionForValue(this.option.getValue());

            double thumbOffset = org.joml.Math.clamp(0, sliderWidth, (double) (this.getIntValue() - this.min) / this.range * sliderWidth);

            int thumbX = (int) (sliderX + thumbOffset - THUMB_WIDTH);
            int trackY = (int) (sliderY + (sliderHeight / 2f) - ((double) TRACK_HEIGHT / 2));

            int accentColor = this.getAccentColor(drawContext);
            drawContext.fill(thumbX, sliderY, thumbX + (THUMB_WIDTH * 2), sliderY + sliderHeight, accentColor);
            drawContext.fill(sliderX, trackY, sliderX + sliderWidth, trackY + TRACK_HEIGHT, ColorARGB.withAlpha(accentColor, 0xB8));

            var label = this.formatter.format(this.getIntValue());

            int labelWidth = drawContext.getStringWidth(label);

            drawContext.drawString(label, sliderX - labelWidth - 6, sliderY + (sliderHeight / 2) - 4, 0xFFFFFFFF);
        }

        // Current value
        public int getIntValue() {
            return this.min + (this.interval * (int) Math.round(this.getSnappedThumbPosition() / this.interval));
        }

        // Thumb position for the current value, snapped to the interval
        public double getSnappedThumbPosition() {
            return this.thumbPosition / (1.0D / this.range);
        }

        // 0..1 along the track
        public double getThumbPositionForValue(int value) {
            return (value - this.min) * (1.0D / this.range);
        }

        // Jumps to the click and starts a drag
        @Override
        public boolean mouseClicked(InteractionContext context, double mouseX, double mouseY, int button) {
            this.sliderHeld = false;

            if (this.option.isAvailable() && button == 0 && this.dim.containsCursor(mouseX, mouseY)) {
                if (this.sliderBounds.containsCursor((int) mouseX, (int) mouseY)) {
                    this.setValueFromMouse(mouseX);
                    this.sliderHeld = true;
                }

                return true;
            }

            return false;
        }

        // Track position to value
        private void setValueFromMouse(double d) {
            this.setValue((d - (double) this.sliderBounds.x()) / (double) (this.sliderBounds.width() - 1));
        }

        // Snaps to the interval and clamps
        public void setValue(double d) {
            this.thumbPosition = org.joml.Math.clamp(0.0D, 1.0D, d);

            int value = this.getIntValue();

            if (!this.option.getValue().equals(value)) {
                this.option.setValue(value);
            }
        }

        // Follows the cursor while dragging
        @Override
        public boolean mouseDragged(InteractionContext context, double mouseX, double mouseY, int button, double deltaX, double deltaY) {
            if (this.option.isAvailable() && button == 0 && this.sliderBounds.containsCursor(mouseX, mouseY)) {
                if (this.sliderHeld) {
                    this.setValueFromMouse(mouseX);
                }

                return true;
            }

            return false;
        }
    }

}
