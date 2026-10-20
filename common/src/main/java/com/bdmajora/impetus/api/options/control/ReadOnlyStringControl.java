package com.bdmajora.impetus.api.options.control;

import com.bdmajora.impetus.api.options.structure.Option;
import com.bdmajora.impetus.engine.impl.gui.framework.DrawContext;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.engine.impl.util.Dim2i;

import java.util.Objects;

public class ReadOnlyStringControl implements Control<String> {
    private final Option<String> option;

    // Refuses a missing option here, so the option it is made in can stand a placeholder in its row
    public ReadOnlyStringControl(Option<String> option) {
        this.option = Objects.requireNonNull(option, "Option must not be null");
    }

    // Bound option
    @Override
    public Option<String> getOption() {
        return this.option;
    }

    // The label widget
    @Override
    public ControlElement<String> createElement(Dim2i dim) {
        return new Element(this.option, dim);
    }

    // Text width
    @Override
    public int getMaxWidth() {
        return 90;
    }

    private static class Element extends ControlElement<String> {
        // Gap between the value and the row's right edge
        private static final int VALUE_RIGHT_INSET = 6;

        // The value cut to the room beside the name
        private final FittedText text = new FittedText();

        Element(Option<String> option, Dim2i dim) {
            super(option, dim);
        }

        // Nothing to press or change, so Tab never stops here
        @Override
        public boolean canFocus() {
            return false;
        }

        // Just the value, right-aligned and cut to the room the name leaves, and nothing for a missing value
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
            // Fitted again only when the value, its state or the room changes
            long generation = this.textGeneration();
            if (!this.text.isFittedFor(generation, value, 0, !enabled, room)) {
                TextComponent label = value != null ? TextComponent.literal(value) : null;
                this.text.fit(drawContext, generation, value, 0, !enabled, room, label == null || enabled ? label : this.formatDisabledControlValue(label));
            }

            TextComponent label = this.text.text();
            if (label != null) {
                drawContext.drawString(label, right - this.text.width(), this.dim.getCenterY() - 4, enabled ? 0xFFFFFFFF : this.getDisabledControlColor());
            }
        }
    }
}
