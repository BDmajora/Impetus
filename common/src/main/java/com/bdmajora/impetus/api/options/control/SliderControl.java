package com.bdmajora.impetus.api.options.control;

import com.bdmajora.impetus.engine.api.util.ColorARGB;
import com.bdmajora.impetus.api.options.structure.Option;
import com.bdmajora.impetus.engine.impl.gui.framework.DrawContext;
import com.bdmajora.impetus.engine.impl.gui.framework.InteractionContext;
import com.bdmajora.impetus.engine.impl.gui.framework.Keys;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.engine.impl.util.Dim2i;

import java.util.Objects;

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

    // Checks the option first and then the range, so a bad slider fails where it is made and the option it is made in can stand a placeholder in its row
    public SliderControl(Option<Integer> option, int min, int max, int interval, ControlValueFormatter mode) {
        Objects.requireNonNull(option, "Option must not be null");
        assertTrue(max > min, "The maximum value must be greater than the minimum value");
        assertTrue(interval > 0, "The slider interval must be greater than zero");
        // Worked out in a long, as the span of the full int range wraps an int
        assertTrue((((long) max - min) % interval) == 0, "The maximum value must be divisable by the interval");
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
        // Widest and tallest the slider is drawn
        private static final int SLIDER_WIDTH = 90;
        private static final int SLIDER_HEIGHT = 10;
        // Gap between the slider and either edge of the row, and between the value and the slider
        private static final int SLIDER_INSET = 6;

        private final Dim2i sliderBounds;
        // Whether the slider lies wholly inside the row, which a row too narrow or too short for it does not hold
        private final boolean sliderFits;
        private final ControlValueFormatter formatter;

        private final int min;
        // Kept in a long, as the span of the full int range wraps an int
        private final long range;
        private final int interval;

        private boolean sliderHeld;

        // The value beside the idle slider and beside the shown one, each cut to the room the name leaves
        private final FittedText idleText = new FittedText();
        private final FittedText shownText = new FittedText();

        public Button(Option<Integer> option, Dim2i dim, int min, int max, int interval, ControlValueFormatter formatter) {
            super(option, dim);

            this.min = min;
            this.range = (long) max - min;
            this.interval = interval;
            this.formatter = formatter;

            // Narrowed to keep the insets on both sides in a row narrower than the slider
            int sliderWidth = (int) Math.clamp(dim.width() - 2L * SLIDER_INSET, 0, SLIDER_WIDTH);
            this.sliderBounds = new Dim2i(dim.getLimitX() - SLIDER_INSET - sliderWidth, dim.getCenterY() - SLIDER_HEIGHT / 2, sliderWidth, SLIDER_HEIGHT);
            this.sliderFits = sliderWidth > 0 && dim.canFitDimension(this.sliderBounds);
            this.sliderHeld = false;
        }

        // Track, thumb and formatted value; a value-only label when neither hovered, held nor focused, or when the slider does not fit the row
        @Override
        public void render(DrawContext drawContext, int mouseX, int mouseY, float delta) {
            super.render(drawContext, mouseX, mouseY, delta);
            if (!this.hasArea()) {
                return;
            }

            boolean enabled = this.isDrawnAvailable();
            int value = this.currentValue();
            if (enabled && this.sliderFits && (this.sliderHeld || this.isFocused() || this.isMouseOver(mouseX, mouseY))) {
                this.renderSlider(drawContext, value);
            } else {
                this.renderStandaloneValue(drawContext, value, enabled);
            }
        }

        // Just the formatted value, when the slider is idle
        private void renderStandaloneValue(DrawContext drawContext, int value, boolean enabled) {
            if (!this.hasRoomForText()) {
                return;
            }
            int right = this.dim.getLimitX() - SLIDER_INSET;
            int room = right - this.getNameLimitX();
            // Formatted and fitted again only when the value, its state or the room changes
            long generation = this.textGeneration();
            if (!this.idleText.isFittedFor(generation, null, value, !enabled, room)) {
                TextComponent label = this.formatter.format(value);
                this.idleText.fit(drawContext, generation, null, value, !enabled, room, label == null || enabled ? label : this.formatDisabledControlValue(label));
            }

            TextComponent label = this.idleText.text();
            if (label != null) {
                drawContext.drawString(label, right - this.idleText.width(), this.dim.getCenterY() - 4, enabled ? 0xFFFFFFFF : this.getDisabledControlColor());
            }
        }

        // Track, thumb and value while hovered, held or focused
        private void renderSlider(DrawContext drawContext, int value) {
            int sliderX = this.sliderBounds.x();
            int sliderY = this.sliderBounds.y();
            int sliderWidth = this.sliderBounds.width();
            int sliderHeight = this.sliderBounds.height();

            // The value's place along the track, held to the ends for a value outside the range
            double thumbOffset = Math.clamp(((double) value - this.min) / this.range * sliderWidth, 0, sliderWidth);

            int thumbX = (int) (sliderX + thumbOffset - THUMB_WIDTH);
            int trackY = (int) (sliderY + (sliderHeight / 2f) - ((double) TRACK_HEIGHT / 2));

            int accentColor = this.getAccentColor(drawContext);
            drawContext.fill(thumbX, sliderY, thumbX + (THUMB_WIDTH * 2), sliderY + sliderHeight, accentColor);
            drawContext.fill(sliderX, trackY, sliderX + sliderWidth, trackY + TRACK_HEIGHT, ColorARGB.withAlpha(accentColor, 0xB8));
            if (!this.hasRoomForText()) {
                return;
            }

            int right = sliderX - SLIDER_INSET;
            int room = right - this.getNameLimitX();
            // Formatted and fitted again only when the value or the room changes
            long generation = this.textGeneration();
            if (!this.shownText.isFittedFor(generation, null, value, false, room)) {
                this.shownText.fit(drawContext, generation, null, value, false, room, this.formatter.format(value));
            }

            TextComponent label = this.shownText.text();
            if (label != null) {
                drawContext.drawString(label, right - this.shownText.width(), sliderY + (sliderHeight / 2) - 4, 0xFFFFFFFF);
            }
        }

        // The option's value, or the minimum when it has none, as a cycler shows a missing value as its first
        private int currentValue() {
            Integer value = this.option.getValue();
            return value != null ? value : this.min;
        }

        // Place along the track from 0 to 1 of a value, past either end for a value outside the range
        private double positionOf(double value) {
            return (value - this.min) / this.range;
        }

        // Value nearest a place along the track from 0 to 1, snapped to the interval
        private int valueAt(double position) {
            // Counted in longs, as steps times the interval can pass an int over the full int range
            long steps = Math.round(position * this.range / this.interval);
            return (int) (this.min + steps * this.interval);
        }

        // Jumps to the click and starts a drag
        @Override
        public boolean mouseClicked(InteractionContext context, double mouseX, double mouseY, int button) {
            this.sliderHeld = false;

            if (button == 0 && this.dim.containsCursor(mouseX, mouseY) && this.isOptionAvailable()) {
                if (this.sliderFits && this.sliderBounds.containsCursor(mouseX, mouseY)) {
                    this.setValueFromMouse(mouseX);
                    this.sliderHeld = true;
                }

                return true;
            }

            return false;
        }

        // Track position to value, the start for a track too narrow to have a span
        private void setValueFromMouse(double d) {
            int span = this.sliderBounds.width() - 1;
            this.setValue(span > 0 ? (d - this.sliderBounds.x()) / span : 0);
        }

        // Snaps to the interval and clamps, writing only a value that differs
        public void setValue(double d) {
            // A place that is not a number rounds to the start
            int value = this.valueAt(Math.clamp(d, 0.0, 1.0));

            Integer current = this.option.getValue();
            if (current == null || current != value) {
                this.option.setValue(value);
            }
        }

        // The arrows move a focused slider one interval the way they point, stopping at either end
        @Override
        public boolean keyTyped(InteractionContext context, char typedChar, int keyCode) {
            if (!this.isFocused() || (keyCode != Keys.LEFT && keyCode != Keys.RIGHT) || !this.isOptionAvailable()) {
                return false;
            }

            int step = keyCode == Keys.LEFT ? -this.interval : this.interval;
            // Added in a long, so a value at either end of the int range cannot wrap round to the other
            this.setValue(this.positionOf((long) this.currentValue() + step));

            return true;
        }

        // Ends a drag on the left button's release, so the slider stops showing as held
        @Override
        public boolean mouseReleased(InteractionContext context, double mouseX, double mouseY, int button) {
            if (button == 0 && this.sliderHeld) {
                this.sliderHeld = false;
                return true;
            }

            return false;
        }

        // Follows the cursor while dragging
        @Override
        public boolean mouseDragged(InteractionContext context, double mouseX, double mouseY, int button, double deltaX, double deltaY) {
            if (button == 0 && this.sliderHeld && this.isOptionAvailable()) {
                this.setValueFromMouse(mouseX);
                return true;
            }

            return false;
        }
    }

}
