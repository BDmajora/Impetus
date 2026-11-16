package com.bdmajora.coarctatio.state.predicate;

import com.google.common.base.Predicate;
import net.minecraft.block.state.IBlockState;

import java.util.Arrays;
import java.util.List;

// Predicate that combines other predicates with and or or.
public final class CompositePredicate implements Predicate<IBlockState> {
    // Combined predicates.
    private final Predicate<IBlockState>[] predicates;
    // Whether every predicate must match.
    private final boolean requireAll;

    // Hash computed once at construction.
    private final int hash;

    // Creates a predicate from its parts.
    private CompositePredicate(Predicate<IBlockState>[] predicates, boolean requireAll) {
        this.predicates = predicates;
        this.requireAll = requireAll;
        this.hash = 31 * Arrays.hashCode(predicates) + (requireAll ? 1 : 0);
    }

    // Returns a predicate that needs every member to match.
    @SuppressWarnings("unchecked")
    public static CompositePredicate all(List<Predicate<IBlockState>> predicates) {
        return new CompositePredicate(predicates.toArray(new Predicate[0]), true);
    }

    // Returns a predicate that needs one member to match.
    @SuppressWarnings("unchecked")
    public static CompositePredicate any(List<Predicate<IBlockState>> predicates) {
        return new CompositePredicate(predicates.toArray(new Predicate[0]), false);
    }

    // Returns whether the members agree with the combination rule.
    @Override
    public boolean apply(IBlockState state) {
        for (Predicate<IBlockState> predicate : this.predicates) {
            if (predicate.apply(state) != this.requireAll) {
                return !this.requireAll;
            }
        }

        return this.requireAll;
    }

    // Returns whether the object combines equal members the same way.
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CompositePredicate)) {
            return false;
        }

        CompositePredicate other = (CompositePredicate) o;
        return this.requireAll == other.requireAll && Arrays.equals(this.predicates, other.predicates);
    }

    // Returns the stored hash.
    @Override
    public int hashCode() {
        return this.hash;
    }
}
