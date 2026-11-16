package com.bdmajora.coarctatio.state.predicate;

import com.google.common.base.Predicate;
import net.minecraft.block.state.IBlockState;

// Predicate that inverts another predicate.
public final class NegatedPredicate implements Predicate<IBlockState> {
    // Predicate that is inverted.
    private final Predicate<IBlockState> delegate;

    // Creates an inverted predicate and rejects a null delegate.
    public NegatedPredicate(Predicate<IBlockState> delegate) {
        this.delegate = java.util.Objects.requireNonNull(delegate, "delegate");
    }

    // Returns whether the delegate does not match.
    @Override
    public boolean apply(IBlockState state) {
        return !this.delegate.apply(state);
    }

    // Returns whether the object inverts an equal predicate.
    @Override
    public boolean equals(Object o) {
        return o instanceof NegatedPredicate && this.delegate.equals(((NegatedPredicate) o).delegate);
    }

    // Returns the inverted hash of the delegate.
    @Override
    public int hashCode() {
        return ~this.delegate.hashCode();
    }
}
