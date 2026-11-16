package com.bdmajora.impetus.engine.impl.gl.util;

import java.util.EnumSet;
import java.util.Objects;

// Immutable set of enum flags with the combined bit mask.
public class EnumBitField<T extends Enum<T> & EnumBit> {
    // Flags in the field.
    private final EnumSet<T> set;
    // Bit mask of all flags.
    private final int bitfield;

    // Creates a field from a flag set and its mask.
    private EnumBitField(EnumSet<T> set, int bitfield) {
        this.set = set;
        this.bitfield = bitfield;
    }

    // Creates a field from flags and rejects an empty array or a null array or null flag.
    @SafeVarargs
    public static <T extends Enum<T> & EnumBit> EnumBitField<T> of(T... values) {
        if (values.length == 0) {
            throw new IllegalArgumentException("A bit field needs at least one flag");
        }

        EnumSet<T> set = EnumSet.noneOf(Objects.requireNonNull(values[0], "value").getDeclaringClass());
        int field = 0;

        for (T value : values) {
            if (set.add(Objects.requireNonNull(value, "value"))) {
                field |= value.getBits();
            }
        }

        return new EnumBitField<>(set, field);
    }

    // Returns the combined bit mask.
    public int getBitField() {
        return this.bitfield;
    }

    // Returns whether the field holds the flag.
    public boolean contains(T flag) {
        return this.set.contains(flag);
    }
}
