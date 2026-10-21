package com.bdmajora.impetus.api.options.control;

import com.bdmajora.impetus.api.options.structure.Option;
import com.bdmajora.impetus.engine.impl.gui.framework.DrawContext;
import com.bdmajora.impetus.engine.impl.gui.framework.InteractionContext;
import com.bdmajora.impetus.engine.impl.gui.framework.Keys;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.engine.impl.gui.options.TextProvider;
import com.bdmajora.impetus.engine.impl.util.Dim2i;

import java.util.Objects;

// Control that steps an option through a fixed list of values on a click or key press
public class CyclingControl<T> implements Control<T> {
    private static final String OPTION_NULL = "Option must not be null";
    private static final String ENUM_TYPE_NULL = "Enum type must not be null";
    private static final String VALUES_NULL = "Values must not be null";
    // Width of the value column at the right of the row
    private static final int MAX_WIDTH = 70;

    private final Option<T> option;
    private final T[] allowedValues;
    private final TextComponent[] names;
    // Type given for the values or the element class of the values array when none is given
    private final Class<?> valueType;

    // Cycles every constant of the enum named by the enum or by the constant names
    public CyclingControl(Option<T> option, Class<T> enumType) {
        Objects.requireNonNull(option, OPTION_NULL);
        T[] constants = enumConstants(enumType);
        this(option, constants, determineNames(constants), enumType, false);
    }

    // Cycles every constant of the enum with one name per constant in enum order
    public CyclingControl(Option<T> option, Class<T> enumType, TextComponent[] names) {
        Objects.requireNonNull(option, OPTION_NULL);
        this(option, enumConstants(enumType), names, enumType, false);
    }

    // Cycles the given values of the enum named the same way as every constant of the enum
    public CyclingControl(Option<T> option, Class<T> enumType, T[] allowedValues) {
        Objects.requireNonNull(option, OPTION_NULL);
        Objects.requireNonNull(enumType, ENUM_TYPE_NULL);
        T[] values = Objects.requireNonNull(allowedValues, VALUES_NULL).clone();
        // Rejects any value that is not an instance of the enum type
        for (T value : values) {
            if (!enumType.isInstance(value)) {
                throw new IllegalArgumentException("Value " + value + " is not a " + enumType.getName());
            }
        }
        this(option, values, determineNames(values), enumType, true);
    }

    // Cycles the given values with one name each in the same order and allows a null value
    public CyclingControl(Option<T> option, T[] allowedValues, TextComponent[] names) {
        Objects.requireNonNull(option, OPTION_NULL);
        T[] values = Objects.requireNonNull(allowedValues, VALUES_NULL).clone();
        this(option, values, names, values.getClass().getComponentType(), true);
    }

    // Stores the values and a copy of the names after checking that every value has exactly one name
    private CyclingControl(Option<T> option, T[] values, TextComponent[] names, Class<?> valueType, boolean checkRepeats) {
        Objects.requireNonNull(names, "Names must not be null");
        if (values.length == 0) {
            throw new IllegalArgumentException("A cycler needs at least one value");
        }
        if (values.length != names.length) {
            throw new IllegalArgumentException("Each value needs one name, but there are " + values.length + " values and " + names.length + " names");
        }
        // Copies every name with its styles
        TextComponent[] copies = new TextComponent[names.length];
        for (int i = 0; i < names.length; i++) {
            TextComponent name = names[i];
            if (name == null) {
                throw new IllegalArgumentException("Value " + values[i] + " has no name");
            }
            copies[i] = FittedText.copied(name);
            // Rejects a value listed more than once when repeats are checked
            if (checkRepeats) {
                for (int j = 0; j < i; j++) {
                    if (Objects.equals(values[j], values[i])) {
                        throw new IllegalArgumentException("Value " + values[i] + " is listed at both " + j + " and " + i);
                    }
                }
            }
        }
        this.option = option;
        this.allowedValues = values;
        this.names = copies;
        this.valueType = valueType;
    }

    // Returns the enum constants in a new array and rejects a class that is not an enum
    private static <T> T[] enumConstants(Class<T> enumType) {
        T[] constants = Objects.requireNonNull(enumType, ENUM_TYPE_NULL).getEnumConstants();
        if (constants == null) {
            throw new IllegalArgumentException(enumType.getName() + " is not an enum");
        }
        return constants;
    }

    // Returns the localised names the values provide or the constant names
    private static TextComponent[] determineNames(Object[] values) {
        TextComponent[] names = new TextComponent[values.length];
        for (int i = 0; i < names.length; i++) {
            Object value = values[i];
            if (value instanceof TextProvider provider) {
                names[i] = provider.getLocalizedName();
            } else if (value instanceof Enum<?> constant) {
                names[i] = TextComponent.literal(constant.name());
            } else {
                throw new IllegalArgumentException("Could not figure out name of object " + value);
            }
        }
        return names;
    }

    // Returns copies of the display names in value order
    public TextComponent[] getNames() {
        TextComponent[] copies = new TextComponent[this.names.length];
        for (int i = 0; i < copies.length; i++) {
            copies[i] = FittedText.copied(this.names[i]);
        }
        return copies;
    }

    // Returns the option this control edits
    @Override
    public Option<T> getOption() {
        return this.option;
    }

    // Creates the cycler element sharing the values and names of the control
    @Override
    public ControlElement<T> createElement(Dim2i dim) {
        return new CyclingControlElement<>(this.option, dim, this.allowedValues, this.names);
    }

    // Returns the width of the value column
    @Override
    public int getMaxWidth() {
        return MAX_WIDTH;
    }

    // Returns the type given for the values or the element class of the values array
    @Override
    public Class<?> getValueType() {
        return this.valueType;
    }

    // Row that shows the name of the current value and steps through the values
    private static final class CyclingControlElement<T> extends ControlElement<T> {
        // Gap between the value's name and the row's right edge
        private static final int VALUE_RIGHT_INSET = 6;

        private final T[] allowedValues;
        private final TextComponent[] names;

        // Name of the current value cut to the room beside the option name
        private final FittedText text = new FittedText();
        // Last value read or written and its index or minus one before the first read
        private T lastValue;
        private int lastIndex = -1;
        // Whether the last value is one of the allowed values
        private boolean lastFound;

        // Stores the values and names shared with the control
        CyclingControlElement(Option<T> option, Dim2i dim, T[] allowedValues, TextComponent[] names) {
            super(option, dim);

            this.allowedValues = allowedValues;
            this.names = names;
        }

        // Returns the index of the current value or zero when it is not one of the values
        private int getCurrentIndex() {
            // Reads the option value once
            T current = this.option.getValue();
            int last = this.lastIndex;
            // Returns the cached index when the value is the same object or equal to the last match
            if (last >= 0 && (current == this.lastValue || Objects.equals(this.lastFound ? this.allowedValues[last] : this.lastValue, current))) {
                this.lastValue = current;
                return last;
            }
            int index = -1;
            for (int i = 0; i < this.allowedValues.length; i++) {
                // Matches values by equality
                if (Objects.equals(this.allowedValues[i], current)) {
                    index = i;
                    break;
                }
            }
            this.lastValue = current;
            this.lastFound = index >= 0;
            this.lastIndex = Math.max(index, 0);
            return this.lastIndex;
        }

        // Draws the row and the current value name aligned right and cut to the room left by the option name
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
            // Fits the name again when the value or state or room or text generation changes
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

        // Steps forward on a left click or backward with Shift held
        @Override
        public boolean mouseClicked(InteractionContext context, double mouseX, double mouseY, int button) {
            if (button == 0 && this.hasArea() && this.dim.containsCursor(mouseX, mouseY) && this.isOptionAvailable()) {
                this.cycleControl(context.isSpecialKeyDown(InteractionContext.SpecialKey.SHIFT));
                context.playClickSound();

                return true;
            }

            return false;
        }

        // Steps on Enter or Space with Shift for backward and on the arrow keys in their direction while focused
        @Override
        public boolean keyTyped(InteractionContext context, char typedChar, int keyCode) {
            if (!this.isFocused() || !this.hasArea()) {
                return false;
            }
            boolean press = Keys.isPress(typedChar, keyCode);
            // Checks availability only for keys the cycler handles
            if (!press && keyCode != Keys.LEFT && keyCode != Keys.RIGHT || !this.isOptionAvailable()) {
                return false;
            }

            this.cycleControl(press ? context.isSpecialKeyDown(InteractionContext.SpecialKey.SHIFT) : keyCode == Keys.LEFT);
            context.playClickSound();

            return true;
        }

        // Steps to the next or previous value and wraps around at either end
        private void cycleControl(boolean reverse) {
            int index = this.getCurrentIndex();
            int last = this.allowedValues.length - 1;
            int next = reverse ? (index == 0 ? last : index - 1) : (index == last ? 0 : index + 1);
            // Skips the write when the only value is already held
            if (next == index && this.lastFound) {
                return;
            }
            T value = this.allowedValues[next];
            this.option.setValue(value);
            // Caches the written value and its index
            this.lastValue = value;
            this.lastIndex = next;
            this.lastFound = true;
        }
    }
}