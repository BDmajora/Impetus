package com.bdmajora.equilibrium.common.advancements;

// Stack that remembers its count before the last change.
public interface PreviousStackSize {
    // Returns the count before the last change.
    int equilibrium$previousCount();

    // Stores the count before the last change.
    void equilibrium$setPreviousCount(int count);
}
