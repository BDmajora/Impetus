package com.bdmajora.coarctatio.state.predicate;

import com.google.common.base.Predicate;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;

import java.util.Arrays;
import java.util.List;

// Predicate that needs each property to hold one of several values.
public final class AllMatchAnyObject implements Predicate<IBlockState> {
    // Properties to test.
    private final IProperty<?>[] properties;
    // Allowed values of each property.
    private final Object[][] values;

    // Hash computed once at construction.
    private final int hash;

    // Creates a predicate from its parts.
    private AllMatchAnyObject(IProperty<?>[] properties, Object[][] values) {
        this.properties = properties;
        this.values = values;
        this.hash = 31 * Arrays.hashCode(properties) + Arrays.deepHashCode(values);
    }

    // Returns a combined predicate or null when a member is not a multi value match.
    public static AllMatchAnyObject tryFlatten(List<Predicate<IBlockState>> predicates) {
        int size = predicates.size();

        IProperty<?>[] properties = new IProperty<?>[size];
        Object[][] values = new Object[size][];

        for (int i = 0; i < size; i++) {
            Predicate<IBlockState> predicate = predicates.get(i);

            if (!(predicate instanceof SingleMatchAny)) {
                return null;
            }

            SingleMatchAny any = (SingleMatchAny) predicate;
            properties[i] = any.property;
            values[i] = any.values;
        }

        return new AllMatchAnyObject(properties, values);
    }

    // Returns whether every property holds an allowed value.
    @Override
    public boolean apply(IBlockState state) {
        if (state == null) {
            return false;
        }

        IProperty<?>[] properties = this.properties;
        Object[][] values = this.values;

        for (int i = 0; i < properties.length; i++) {
            Object actual = state.getValue(properties[i]);

            if (!contains(values[i], actual)) {
                return false;
            }
        }

        return true;
    }

    // Returns whether the value is one of the candidates.
    private static boolean contains(Object[] candidates, Object actual) {
        for (Object candidate : candidates) {
            if (actual == candidate || actual.equals(candidate)) {
                return true;
            }
        }

        return false;
    }

    // Returns whether the object tests the same properties and values.
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AllMatchAnyObject)) {
            return false;
        }

        AllMatchAnyObject other = (AllMatchAnyObject) o;
        return Arrays.equals(this.properties, other.properties) && Arrays.deepEquals(this.values, other.values);
    }

    // Returns the stored hash.
    @Override
    public int hashCode() {
        return this.hash;
    }
}
