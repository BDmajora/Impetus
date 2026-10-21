package com.bdmajora.impetus.api.options.control;

import com.bdmajora.impetus.api.options.structure.Option;
import com.bdmajora.impetus.engine.impl.gui.framework.DrawContext;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.engine.impl.util.Dim2i;

import java.util.Objects;

// Control that shows an option's string value as text that cannot be edited
public class ReadOnlyStringControl implements Control<String> {
    private final Option<String> option;

    // Stores the option and rejects a null option
    public ReadOnlyStringControl(Option<String> option) {
        this.option = Objects.requireNonNull(option, "Option must not be null");
    }

    // Returns the option this control shows
    @Override
    public Option<String> getOption() {
        return this.option;
    }

    // Creates the element that draws the value
    @Override
    public ControlElement<String> createElement(Dim2i dim) {
        return new Element(this.option, dim);
    }

    // Returns the maximum width of the value column in pixels
    @Override
    public int getMaxWidth() {
        return 90;
    }

    // Row that draws the value beside the option name
    private static class Element extends ControlElement<String> {
        // Gap between the value and the row's right edge
        private static final int VALUE_RIGHT_INSET = 6;

        // Value cut to the room beside the name
        private final FittedText text = new FittedText();

        // Creates the row for the option at the given bounds
        Element(Option<String> option, Dim2i dim) {
            super(option, dim);
        }

        // Keeps the row out of the focus order
        @Override
        public boolean canFocus() {
            return false;
        }

        // Keeps the row from ever taking focus
        @Override
        public void setFocused(boolean focused) {
            super.setFocused(false);
        }

        // Draws the value on one line aligned right and cut to the room left by the name and draws nothing for a null or blank value
        @Override
        public void render(DrawContext drawContext, int mouseX, int mouseY, float delta) {
            super.render(drawContext, mouseX, mouseY, delta);
            if (!this.hasArea() || !this.hasRoomForText()) {
                return;
            }

            String value = this.option.getValue();
            boolean enabled = this.isDrawnAvailable();
            int right = this.dim.getLimitX() - VALUE_RIGHT_INSET;
            int room = right - this.getNameLimitX();
            // Fits the value again when the value or state or room or text generation changes
            long generation = this.textGeneration();
            if (!this.text.isFittedFor(generation, value, 0, !enabled, room)) {
                String line = oneLine(value);
                TextComponent label = line != null ? TextComponent.literal(line) : null;
                this.text.fit(drawContext, generation, value, 0, !enabled, room, label == null || enabled ? label : this.formatDisabledControlValue(label));
            }

            TextComponent label = this.text.text();
            if (label != null) {
                drawContext.drawString(label, right - this.text.width(), this.dim.getCenterY() - 4, enabled ? 0xFFFFFFFF : this.getDisabledControlColor());
            }
        }

        // Returns the value trimmed of blank and control characters with each inner run of control characters replaced by one space or null when nothing is left
        private static String oneLine(String value) {
            if (value == null) {
                return null;
            }
            int start = 0;
            int end = value.length();
            while (start < end && isBlank(value.charAt(start))) {
                start++;
            }
            while (end > start && isBlank(value.charAt(end - 1))) {
                end--;
            }
            if (start == end) {
                return null;
            }
            // Creates the builder only when a control character is found
            StringBuilder line = null;
            int copied = start;
            for (int i = start; i < end; i++) {
                if (isControl(value.charAt(i))) {
                    if (line == null) {
                        line = new StringBuilder(end - start);
                    }
                    line.append(value, copied, i).append(' ');
                    // Skips the rest of the control character run
                    while (isControl(value.charAt(i + 1))) {
                        i++;
                    }
                    copied = i + 1;
                }
            }
            return line != null ? line.append(value, copied, end).toString() : value.substring(start, end);
        }

        // Returns true for whitespace and control characters
        private static boolean isBlank(char c) {
            return Character.isWhitespace(c) || isControl(c);
        }

        // Returns true for control characters and the line and paragraph separators
        private static boolean isControl(char c) {
            return Character.isISOControl(c) || c == '\u2028' || c == '\u2029';
        }
    }
}