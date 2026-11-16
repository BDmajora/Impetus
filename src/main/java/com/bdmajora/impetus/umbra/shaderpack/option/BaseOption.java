package com.bdmajora.impetus.umbra.shaderpack.option;

import java.util.Optional;

// Base of a shader pack option.
public abstract class BaseOption {
    // Kind of declaration.
    private final OptionType type;
    // Name of the option.
    private final String name;
    // Comment of the option or null.
    private final String comment;

    // Creates an option and drops an empty comment.
    BaseOption(OptionType type, String name, String comment) {
        this.type = type;
        this.name = name;

        if (comment == null || comment.isEmpty()) {
            this.comment = null;
        } else {
            this.comment = comment;
        }
    }

    // Returns the kind of declaration.
    public OptionType getType() {
        return type;
    }

    // Returns the name.
    public String getName() {
        return name;
    }

    // Returns the comment or empty.
    public Optional<String> getComment() {
        return Optional.ofNullable(comment);
    }
}
