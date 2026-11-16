package com.bdmajora.coarctatio.state.predicate;

import com.google.common.base.Predicate;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;

import java.util.Arrays;
import java.util.List;

// Predicate that needs each property to hold one value.
public final class AllMatchOne implements Predicate<IBlockState> {
    // Properties to test.
    private final IProperty<?>[] properties;
    // Required value of each property.
    private final Object[] values;

    // Hash computed once at construction.
    private final int hash;

    // Creates a predicate from its parts.
    private AllMatchOne(IProperty<?>[] properties, Object[] values) {
        this.properties = properties;
        this.values = values;
        this.hash = 31 * Arrays.hashCode(properties) + Arrays.hashCode(values);
    }

    // Returns a combined predicate or null when a member is not a single value match.
    public static AllMatchOne tryFlatten(List<Predicate<IBlockState>> predicates) {
        int size = predicates.size();

        IProperty<?>[] properties = new IProperty<?>[size];
        Object[] values = new Object[size];

        for (int i = 0; i < size; i++) {
            Predicate<IBlockState> predicate = predicates.get(i);

            if (!(predicate instanceof SingleMatchOne)) {
                return null;
            }

            SingleMatchOne single = (SingleMatchOne) predicate;
            properties[i] = single.property;
            values[i] = single.value;
        }

        return new AllMatchOne(properties, values);
    }

    // Returns whether every property holds its required value.
    @Override
    public boolean apply(IBlockState state) {
        if (state == null) {
            return false;
        }

        IProperty<?>[] properties = this.properties;
        Object[] values = this.values;

        for (int i = 0; i < properties.length; i++) {
            Object actual = state.getValue(properties[i]);

            if (actual != values[i] && !actual.equals(values[i])) {
                return false;
            }
        }

        return true;
    }

    // Returns whether the object tests the same properties and values.
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AllMatchOne)) {
            return false;
        }

        AllMatchOne other = (AllMatchOne) o;
        return Arrays.equals(this.properties, other.properties) && Arrays.equals(this.values, other.values);
    }

    // Returns the stored hash.
    @Override
    public int hashCode() {
        return this.hash;
    }
}
