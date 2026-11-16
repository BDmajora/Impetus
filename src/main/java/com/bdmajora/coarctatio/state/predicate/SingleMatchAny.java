package com.bdmajora.coarctatio.state.predicate;

import com.google.common.base.Predicate;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;

import java.util.Arrays;

// Predicate that needs one property to hold one of several values.
public final class SingleMatchAny implements Predicate<IBlockState> {
    // Property to test.
    public final IProperty<?> property;
    // Allowed values.
    public final Object[] values;

    // Hash computed once at construction.
    private final int hash;

    // Creates a predicate and rejects a null property or value array.
    public SingleMatchAny(IProperty<?> property, Object[] values) {
        this.property = java.util.Objects.requireNonNull(property, "property");
        this.values = java.util.Objects.requireNonNull(values, "values");
        this.hash = 31 * property.hashCode() + Arrays.hashCode(values);
    }

    // Returns whether the property holds an allowed value.
    @Override
    public boolean apply(IBlockState state) {
        if (state == null) {
            return false;
        }

        Object actual = state.getValue(this.property);

        for (Object value : this.values) {
            if (actual == value || actual.equals(value)) {
                return true;
            }
        }

        return false;
    }

    // Returns whether the object tests the same property and values.
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SingleMatchAny)) {
            return false;
        }

        SingleMatchAny other = (SingleMatchAny) o;
        return (this.property == other.property || this.property.equals(other.property))
                && Arrays.equals(this.values, other.values);
    }

    // Returns the stored hash.
    @Override
    public int hashCode() {
        return this.hash;
    }
}
