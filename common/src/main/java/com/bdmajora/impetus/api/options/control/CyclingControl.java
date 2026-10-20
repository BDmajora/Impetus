package com.bdmajora.impetus.api.options.control;

import com.bdmajora.impetus.api.options.structure.Option;
import com.bdmajora.impetus.engine.impl.gui.framework.DrawContext;
import com.bdmajora.impetus.engine.impl.gui.framework.InteractionContext;
import com.bdmajora.impetus.engine.impl.gui.framework.Keys;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.engine.impl.gui.options.TextProvider;
import com.bdmajora.impetus.engine.impl.util.Dim2i;

import java.util.Objects;

public class CyclingControl<T> implements Control<T> {
    private final Option<T> option;
    private final T[] allowedValues;
    private final TextComponent[] names;

    public CyclingControl(Option<T> option, Class<T> enumType) {
        this(option, enumType.getEnumConstants(), determineNames(enumType.getEnumConstants()));
    }

    public CyclingControl(Option<T> option, Class<T> enumType, TextComponent[] names) {
        this(option, enumType.getEnumConstants(), names);
    }

    public CyclingControl(Option<T> option, Class<T> enumType, T[] allowedValues) {
        this(option, allowedValues, determineNames(allowedValues));
    }

    public CyclingControl(Option<T> option, T[] allowedValues, TextComponent[] names) {
        this.option = option;
        if (allowedValues.length != names.length) {
            throw new IllegalArgumentException();
        }
        this.allowedValues = allowedValues;
        this.names = names;
    }

    // Localised names when the enum provides them, else the constant names
    private static TextComponent[] determineNames(Object[] universe) {
        TextComponent[] names = new TextComponent[universe.length];
        for (int i = 0; i < names.length; i++) {
            TextComponent name;
            Object value = universe[i];

            if (value instanceof TextProvider) {
                name = ((TextProvider) value).getLocalizedName();
            } else if (value instanceof Enum<?> e) {
                name = TextComponent.literal(e.name());
            } else {
                throw new IllegalArgumentException("Could not figure out name of object " + value);
            }

            names[i] = name;
        }
        return names;
    }

    // Display names per value
    public TextComponent[] getNames() {
        return this.names;
    }

    // Bound option
    @Override
    public Option<T> getOption() {
        return this.option;
    }

    // The cycler widget
    @Override
    public ControlElement<T> createElement(Dim2i dim) {
        return new CyclingControlElement<>(this.option, dim, this.allowedValues, this.names);
    }

    // Widest name
    @Override
    public int getMaxWidth() {
        return 70;
    }

    private static class CyclingControlElement<T> extends ControlElement<T> {
        private final T[] allowedValues;
        private final TextComponent[] names;

        public CyclingControlElement(Option<T> option, Dim2i dim, T[] allowedValues, TextComponent[] names) {
            super(option, dim);

            this.allowedValues = allowedValues;
            this.names = names;
        }

        // Index of the current value in the universe, or 0 when it is null or not allowed
        private int getCurrentIndex() {
            // Read once, so the getter runs once per call instead of once per allowed value
            T current = this.option.getValue();
            for (int i = 0; i < this.allowedValues.length; i++) {
                // Equality, not identity, so boxed values outside the Integer cache still match
                if (Objects.equals(this.allowedValues[i], current)) {
                    return i;
                }
            }

            return 0;
        }

        // Label and current name
        @Override
        public void render(DrawContext drawContext, int mouseX, int mouseY, float delta) {
            super.render(drawContext, mouseX, mouseY, delta);

            TextComponent name = this.names[getCurrentIndex()];
            boolean enabled = this.option.isAvailable();

            if(!enabled) {
                name = this.formatDisabledControlValue(name);
            }

            int strWidth = drawContext.getStringWidth(name);
            drawContext.drawString(name, this.dim.getLimitX() - strWidth - 6, this.dim.getCenterY() - 4, enabled ? 0xFFFFFFFF : this.getDisabledControlColor());
        }

        // Left cycles forward, right backward
        @Override
        public boolean mouseClicked(InteractionContext context, double mouseX, double mouseY, int button) {
            if (this.option.isAvailable() && button == 0 && this.dim.containsCursor(mouseX, mouseY)) {
                cycleControl(context.isSpecialKeyDown(InteractionContext.SpecialKey.SHIFT));
                context.playClickSound();

                return true;
            }

            return false;
        }

        // While focused: Enter, keypad Enter or Space steps like a click (Shift backwards), and the arrows step the way they point
        @Override
        public boolean keyTyped(InteractionContext context, char typedChar, int keyCode) {
            if (!this.isFocused() || !this.option.isAvailable()) {
                return false;
            }

            boolean reverse;
            if (Keys.isPress(typedChar, keyCode)) {
                reverse = context.isSpecialKeyDown(InteractionContext.SpecialKey.SHIFT);
            } else if (keyCode == Keys.LEFT || keyCode == Keys.RIGHT) {
                reverse = keyCode == Keys.LEFT;
            } else {
                return false;
            }

            cycleControl(reverse);
            context.playClickSound();

            return true;
        }

        // Steps with wrap-around
        public void cycleControl(boolean reverse) {
            int currentIndex = getCurrentIndex();
            if (reverse) {
                currentIndex = (currentIndex + this.allowedValues.length - 1) % this.allowedValues.length;
            } else {
                currentIndex = (currentIndex + 1) % this.allowedValues.length;
            }
            this.option.setValue(this.allowedValues[currentIndex]);
        }
    }
}
