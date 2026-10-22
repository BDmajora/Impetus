package com.bdmajora.impetus.api.options.control;

import com.bdmajora.impetus.engine.api.util.ColorARGB;
import com.bdmajora.impetus.api.options.structure.Option;
import com.bdmajora.impetus.engine.impl.gui.framework.DrawContext;
import com.bdmajora.impetus.engine.impl.gui.framework.InteractionContext;
import com.bdmajora.impetus.engine.impl.gui.framework.Keys;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.engine.impl.util.Dim2i;

import java.util.Objects;

// Control that sets an integer option with a slider stepped by a fixed interval
public class SliderControl implements Control<Integer> {
    private final Option<Integer> option;

    private final int min, max, interval;

    private final ControlValueFormatter mode;

    // Span from the minimum to the maximum as a long and the index of the last stop
    private final long range;
    private final long lastStop;

    // Creates the slider and rejects null arguments and an invalid range or interval
    public SliderControl(Option<Integer> option, int min, int max, int interval, ControlValueFormatter mode) {
        Objects.requireNonNull(option, "Option must not be null");
        Objects.requireNonNull(mode, "Formatter must not be null");
        // Rejects a maximum not above the minimum or an interval below one or a range that is not a whole number of intervals
        if (max <= min) {
            throw new IllegalArgumentException("Slider maximum " + max + " must be greater than its minimum " + min);
        }
        if (interval <= 0) {
            throw new IllegalArgumentException("Slider interval " + interval + " must be greater than zero");
        }
        long range = (long) max - min;
        if (range % interval != 0) {
            throw new IllegalArgumentException("Slider range " + min + " to " + max + " is not a whole number of intervals of " + interval);
        }

        this.option = option;
        this.min = min;
        this.max = max;
        this.interval = interval;
        this.mode = mode;
        // Stores the range and the index of the last stop
        this.range = range;
        this.lastStop = range / interval;
    }

    // Creates the slider element at the given bounds
    @Override
    public ControlElement<Integer> createElement(Dim2i dim) {
        return new Button(this, dim);
    }

    // Returns the option this control edits
    @Override
    public Option<Integer> getOption() {
        return this.option;
    }

    // Returns the maximum width of the control in pixels
    @Override
    public int getMaxWidth() {
        return 130;
    }

    // Row that draws the slider or the formatted value and handles clicks and drags and arrow keys
    private static final class Button extends ControlElement<Integer> {
        // Half width of the thumb and height of the track
        private static final int THUMB_WIDTH = 2, TRACK_HEIGHT = 1;
        // Largest width and the height of the slider
        private static final int SLIDER_WIDTH = 90;
        private static final int SLIDER_HEIGHT = 10;
        // Gap between the slider and the row edges and between the value and the slider
        private static final int SLIDER_INSET = 6;

        // Control that holds the range and stops and formatter
        private final SliderControl control;
        // Width of the track or zero when the slider does not fit inside a drawable row
        private final int sliderWidth;

        // Whether a drag is in progress
        private boolean sliderHeld;

        // Value text beside the idle slider and the shown slider created on first use
        private FittedText idleText;
        private FittedText shownText;

        // Creates the row and works out the track width
        Button(SliderControl control, Dim2i dim) {
            super(control.option, dim);

            this.control = control;
            // Narrows the track to keep the inset on both sides of a narrow row
            int width = (int) Math.clamp(dim.width() - 2L * SLIDER_INSET, 0, SLIDER_WIDTH);
            // Uses the track width only on a drawable row at least as tall as the slider
            this.sliderWidth = this.hasArea() && dim.height() >= SLIDER_HEIGHT ? width : 0;
        }

        // Returns the left edge of the track
        private int sliderX() {
            return this.dim.getLimitX() - SLIDER_INSET - this.sliderWidth;
        }

        // Returns the top of the slider centred on the row
        private int sliderY() {
            return this.dim.getCenterY() - SLIDER_HEIGHT / 2;
        }

        // Draws the slider while hovered or held or focused and the value alone otherwise
        @Override
        public void render(DrawContext drawContext, int mouseX, int mouseY, float delta) {
            super.render(drawContext, mouseX, mouseY, delta);

            // Shows the slider only on an available row with a track
            boolean enabled = this.isDrawnAvailable();
            if (enabled && this.sliderWidth > 0 && (this.sliderHeld || this.isHovered())) {
                this.renderSlider(drawContext, this.currentValue());
            } else if (this.hasRoomForText()) {
                // Draws the value alone when a line of text fits
                this.renderStandaloneValue(drawContext, this.currentValue(), enabled);
            }
        }

        // Draws the formatted value aligned right while the slider is idle
        private void renderStandaloneValue(DrawContext drawContext, int value, boolean enabled) {
            FittedText text = this.idleText;
            if (text == null) {
                text = this.idleText = new FittedText();
            }
            int right = this.dim.getLimitX() - SLIDER_INSET;
            int room = right - this.getNameLimitX();
            // Formats and fits the value again when the value or state or room or text generation changes
            long generation = this.textGeneration();
            if (!text.isFittedFor(generation, null, value, !enabled, room)) {
                TextComponent label = this.control.mode.format(value);
                text.fit(drawContext, generation, null, value, !enabled, room, label == null || enabled ? label : this.formatDisabledControlValue(label));
            }

            TextComponent label = text.text();
            if (label != null) {
                drawContext.drawString(label, right - text.width(), this.dim.getCenterY() - 4, enabled ? 0xFFFFFFFF : this.getDisabledControlColor());
            }
        }

        // Draws the track and thumb and the value beside the slider
        private void renderSlider(DrawContext drawContext, int value) {
            int sliderX = this.sliderX();
            int sliderY = this.sliderY();
            int sliderWidth = this.sliderWidth;

            // Converts the value clamped to the range into a whole pixel offset along the track
            long offset = Math.clamp((long) value - this.control.min, 0, this.control.range);
            int thumbOffset = (int) ((double) offset * sliderWidth / this.control.range);

            // Computes the thumb and track positions in whole pixels
            int thumbX = sliderX + thumbOffset - THUMB_WIDTH;
            int trackY = sliderY + (SLIDER_HEIGHT - TRACK_HEIGHT) / 2;

            int accentColor = this.getAccentColor(drawContext);
            drawContext.fill(thumbX, sliderY, thumbX + (THUMB_WIDTH * 2), sliderY + SLIDER_HEIGHT, accentColor);
            drawContext.fill(sliderX, trackY, sliderX + sliderWidth, trackY + TRACK_HEIGHT, ColorARGB.withAlpha(accentColor, 0xB8));
            if (!this.hasRoomForText()) {
                return;
            }

            FittedText text = this.shownText;
            if (text == null) {
                text = this.shownText = new FittedText();
            }
            int right = sliderX - SLIDER_INSET;
            int room = right - this.getNameLimitX();
            // Formats and fits the value again when the value or room or text generation changes
            long generation = this.textGeneration();
            if (!text.isFittedFor(generation, null, value, false, room)) {
                text.fit(drawContext, generation, null, value, false, room, this.control.mode.format(value));
            }

            TextComponent label = text.text();
            if (label != null) {
                drawContext.drawString(label, right - text.width(), this.dim.getCenterY() - 4, 0xFFFFFFFF);
            }
        }

        // Returns the option value or the minimum when it is null
        private int currentValue() {
            return this.valueOr(this.option.getValue());
        }

        // Returns the given value or the minimum when it is null
        private int valueOr(Integer value) {
            return value != null ? value : this.control.min;
        }

        // Returns the value of the given stop clamped to the first and last stops
        private int valueOfStop(long stop) {
            SliderControl control = this.control;
            return (int) (control.min + Math.clamp(stop, 0, control.lastStop) * control.interval);
        }

        // Writes the value unless the option already holds it
        private void write(Integer current, int value) {
            if (current == null || current != value) {
                this.option.setValue(value);
            }
        }

        // Moves the value to the clicked point on the track and starts a drag
        @Override
        public boolean mouseClicked(InteractionContext context, double mouseX, double mouseY, int button) {
            this.sliderHeld = false;

            if (button == 0 && this.hasArea() && this.dim.containsCursor(mouseX, mouseY) && this.isOptionAvailable()) {
                if (this.sliderWidth > 0) {
                    int sliderX = this.sliderX();
                    int sliderY = this.sliderY();
                    // Checks that the cursor is over the track columns and slider rows
                    if (mouseX >= sliderX && mouseX < sliderX + this.sliderWidth && mouseY >= sliderY && mouseY < sliderY + SLIDER_HEIGHT) {
                        this.setValueFromMouse(mouseX, sliderX);
                        this.sliderHeld = true;
                    }
                }

                return true;
            }

            return false;
        }

        // Sets the value to the stop nearest the cursor with the first track pixel as the minimum and the last as the maximum
        private void setValueFromMouse(double mouseX, int sliderX) {
            int span = this.sliderWidth - 1;
            double position = span > 0 ? (mouseX - sliderX) / span : 0;
            // Rounds the clamped position to the nearest stop
            long stop = Math.round(Math.clamp(position, 0.0, 1.0) * this.control.lastStop);
            this.write(this.option.getValue(), this.valueOfStop(stop));
        }

        // Moves a focused slider one stop left or right with the arrow keys and stops at either end
        @Override
        public boolean keyTyped(InteractionContext context, char typedChar, int keyCode) {
            if (!this.isFocused() || !this.hasArea() || (keyCode != Keys.LEFT && keyCode != Keys.RIGHT) || !this.isOptionAvailable()) {
                return false;
            }

            Integer current = this.option.getValue();
            int interval = this.control.interval;
            // Computes the offset from the minimum as a long
            long offset = (long) this.valueOr(current) - this.control.min;
            // Picks the next stop past the current value in the arrow direction
            long stop = keyCode == Keys.RIGHT ? Math.floorDiv(offset, interval) + 1 : Math.ceilDiv(offset, interval) - 1;
            this.write(current, this.valueOfStop(stop));

            return true;
        }

        // Ends a drag when the left button is released
        @Override
        public boolean mouseReleased(InteractionContext context, double mouseX, double mouseY, int button) {
            if (button == 0 && this.sliderHeld) {
                this.sliderHeld = false;
                return true;
            }

            return false;
        }

        // Moves the value with the cursor while dragging
        @Override
        public boolean mouseDragged(InteractionContext context, double mouseX, double mouseY, int button, double deltaX, double deltaY) {
            if (button == 0 && this.sliderHeld && this.isOptionAvailable()) {
                this.setValueFromMouse(mouseX, this.sliderX());
                return true;
            }

            return false;
        }
    }

}