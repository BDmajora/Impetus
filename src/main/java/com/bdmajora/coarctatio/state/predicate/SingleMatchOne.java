package com.bdmajora.coarctatio.state.predicate;

import com.google.common.base.Predicate;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;

import java.util.Objects;

// Predicate that needs one property to hold one value.
public final class SingleMatchOne implements Predicate<IBlockState> {
    // Property to test.
    public final IProperty<?> property;
    // Required value.
    public final Object value;

    // Hash computed once at construction.
    private final int hash;

    // Creates a predicate and rejects a null property.
    public SingleMatchOne(IProperty<?> property, Object value) {
        this.property = java.util.Objects.requireNonNull(property, "property");
        this.value = value;
        this.hash = 31 * property.hashCode() + Objects.hashCode(value);
    }

    // Returns whether the property holds the required value.
    @Override
    public boolean apply(IBlockState state) {
        if (state == null) {
            return false;
        }

        Object actual = state.getValue(this.property);
        return actual == this.value || actual.equals(this.value);
    }

    // Returns whether the object tests the same property and value.
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SingleMatchOne)) {
            return false;
        }

        SingleMatchOne other = (SingleMatchOne) o;
        return (this.property == other.property || this.property.equals(other.property))
                && Objects.equals(this.value, other.value);
    }

    // Returns the stored hash.
    @Override
    public int hashCode() {
        return this.hash;
    }
}
