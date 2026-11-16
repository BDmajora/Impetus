package com.bdmajora.impetus.umbra.shaderpack.option;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

// Pack option with a list of allowed values.
public class StringOption extends BaseOption {
    // Default value.
    private final String defaultValue;
    // Allowed values.
    private final List<String> allowedValues;

    // Creates a string option from its type and name and comment and default value and allowed values.
    private StringOption(OptionType type, String name, String comment, String defaultValue, List<String> allowedValues) {
        super(type, name, comment);

        this.defaultValue = Objects.requireNonNull(defaultValue);
        this.allowedValues = allowedValues;
    }

    // Creates an option or returns null when it has no allowed values.
    public static StringOption create(OptionType type, String name, String comment, String defaultValue) {
        if (comment == null) {
            return null;
        }

        int openingBracket = comment.indexOf('[');

        if (openingBracket == -1) {
            return null;
        }

        int closingBracket = comment.indexOf(']', openingBracket);

        if (closingBracket == -1) {
            return null;
        }

        String[] allowedValues = comment.substring(openingBracket + 1, closingBracket).split(" ");
        comment = comment.substring(0, openingBracket) + comment.substring(closingBracket + 1);
        boolean allowedValuesContainsDefaultValue = false;

        for (String value : allowedValues) {
            if (defaultValue.equals(value)) {
                allowedValuesContainsDefaultValue = true;
                break;
            }
        }

        List<String> builder = new ArrayList<>();

        Collections.addAll(builder, allowedValues);

        if (!allowedValuesContainsDefaultValue) {
            builder.add(defaultValue);
        }

        return new StringOption(type, name, comment.trim(), defaultValue, Collections.unmodifiableList(builder));
    }

    // Returns the default value.
    public String getDefaultValue() {
        return defaultValue;
    }

    // Returns the allowed values.
    public List<String> getAllowedValues() {
        return allowedValues;
    }
}
