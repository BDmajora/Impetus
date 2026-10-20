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
        this(Objects.requireNonNull(option, "Option must not be null"), enumConstants(enumType), determineNames(enumConstants(enumType)));
    }

    public CyclingControl(Option<T> option, Class<T> enumType, TextComponent[] names) {
        this(Objects.requireNonNull(option, "Option must not be null"), enumConstants(enumType), names);
    }

    public CyclingControl(Option<T> option, Class<T> enumType, T[] allowedValues) {
        this(Objects.requireNonNull(option, "Option must not be null"), allowedValues, determineNames(Objects.requireNonNull(allowedValues, "Values must not be null")));
    }

    // Checks everything a row will need once here, so a bad cycler fails where it is made and the option it is made in can stand a placeholder in its row
    public CyclingControl(Option<T> option, T[] allowedValues, TextComponent[] names) {
        this.option = Objects.requireNonNull(option, "Option must not be null");
        Objects.requireNonNull(allowedValues, "Values must not be null");
        Objects.requireNonNull(names, "Names must not be null");
        if (allowedValues.length == 0) {
            throw new IllegalArgumentException("A cycler needs at least one value");
        }
        if (allowedValues.length != names.length) {
            throw new IllegalArgumentException("Each value needs one name, but there are " + allowedValues.length + " values and " + names.length + " names");
        }
        // Copied with every name's style sets, so arrays and sets the caller changes afterwards cannot reach the rows
        this.allowedValues = allowedValues.clone();
        this.names = new TextComponent[names.length];
        for (int i = 0; i < names.length; i++) {
            if (names[i] == null) {
                throw new IllegalArgumentException("Value " + this.allowedValues[i] + " has no name");
            }
            this.names[i] = FittedText.copied(names[i]);
        }
    }

    // The enum's constants, refusing a class that is not an enum by name rather than failing on its missing constants
    private static <T> T[] enumConstants(Class<T> enumType) {
        T[] constants = Objects.requireNonNull(enumType, "Enum type must not be null").getEnumConstants();
        if (constants == null) {
            throw new IllegalArgumentException(enumType.getName() + " is not an enum");
        }
        return constants;
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

    // Display names per value, as a copy so the cycler's own cannot be changed
    public TextComponent[] getNames() {
        return this.names.clone();
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

    // The values array's own element class, since the class leaves its type argument open
    @Override
    public Class<?> getValueType() {
        return this.allowedValues.getClass().getComponentType();
    }

    private static class CyclingControlElement<T> extends ControlElement<T> {
        // Gap between the value's name and the row's right edge
        private static final int VALUE_RIGHT_INSET = 6;

        private final T[] allowedValues;
        private final TextComponent[] names;

        // The current value's name cut to the room beside the option's name
        private final FittedText text = new FittedText();
        // Last value read and its index, or minus one before the first read, so the same value read again is found without a scan
        private T lastValue;
        private int lastIndex = -1;

        public CyclingControlElement(Option<T> option, Dim2i dim, T[] allowedValues, TextComponent[] names) {
            super(option, dim);

            this.allowedValues = allowedValues;
            this.names = names;
        }

        // Index of the current value in the universe, or 0 when it is null or not allowed
        private int getCurrentIndex() {
            // Read once, so the getter runs once per call instead of once per allowed value
            T current = this.option.getValue();
            if (this.lastIndex >= 0 && current == this.lastValue) {
                return this.lastIndex;
            }
            int index = 0;
            for (int i = 0; i < this.allowedValues.length; i++) {
                // Equality, not identity, so boxed values outside the Integer cache still match
                if (Objects.equals(this.allowedValues[i], current)) {
                    index = i;
                    break;
                }
            }
            this.lastValue = current;
            this.lastIndex = index;
            return index;
        }

        // Label and current name, right-aligned and cut to the room the option's name leaves
        @Override
        public void render(DrawContext drawContext, int mouseX, int mouseY, float delta) {
            super.render(drawContext, mouseX, mouseY, delta);
            if (!this.hasArea() || !this.hasRoomForText()) {
                return;
            }

            int index = this.getCurrentIndex();
            boolean enabled = this.isDrawnAvailable();
            int right = this.dim.getLimitX() - VALUE_RIGHT_INSET;
            int room = right - this.getNameLimitX();
            // Fitted again only when the value, its state or the room changes, the names being fixed when the cycler was made
            long generation = this.textGeneration();
            if (!this.text.isFittedFor(generation, null, index, !enabled, room)) {
                TextComponent name = this.names[index];
                this.text.fit(drawContext, generation, null, index, !enabled, room, enabled ? name : this.formatDisabledControlValue(name));
            }

            TextComponent shown = this.text.text();
            if (shown != null) {
                drawContext.drawString(shown, right - this.text.width(), this.dim.getCenterY() - 4, enabled ? 0xFFFFFFFF : this.getDisabledControlColor());
            }
        }

        // Left cycles forward, right backward
        @Override
        public boolean mouseClicked(InteractionContext context, double mouseX, double mouseY, int button) {
            if (button == 0 && this.dim.containsCursor(mouseX, mouseY) && this.isOptionAvailable()) {
                cycleControl(context.isSpecialKeyDown(InteractionContext.SpecialKey.SHIFT));
                context.playClickSound();

                return true;
            }

            return false;
        }

        // While focused: Enter, keypad Enter or Space steps like a click (Shift backwards), and the arrows step the way they point
        @Override
        public boolean keyTyped(InteractionContext context, char typedChar, int keyCode) {
            if (!this.isFocused() || !this.isOptionAvailable()) {
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
