package com.bdmajora.equilibrium.common.advancements;

// Implemented on ItemStack: the count the stack had before the last container change, which is what lets a trigger tell "grew past 16" from "shrank to 16"
public interface PreviousStackSize {
    int equilibrium$previousCount();

    void equilibrium$setPreviousCount(int count);
}
