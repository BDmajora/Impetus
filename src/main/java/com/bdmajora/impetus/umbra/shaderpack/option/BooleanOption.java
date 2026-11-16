package com.bdmajora.impetus.umbra.shaderpack.option;

// On or off pack option.
public final class BooleanOption extends BaseOption {
    // Default value.
    private final boolean defaultValue;

    // Creates a boolean option from its type and name and comment and default value.
    public BooleanOption(OptionType type, String name, String comment, boolean defaultValue) {
        super(type, name, comment);

        this.defaultValue = defaultValue;
    }

    // Returns the default value.
    public boolean getDefaultValue() {
        return defaultValue;
    }

    // Returns the text of the option.
    @Override
    public String toString() {
        return "BooleanDefineOption{" +
                "name=" + getName() +
                ", comment=" + getComment() +
                ", defaultValue=" + defaultValue +
                '}';
    }
}
