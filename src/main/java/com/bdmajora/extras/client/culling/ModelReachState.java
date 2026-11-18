package com.bdmajora.extras.client.culling;

import net.minecraft.item.ItemStack;

// Equipment an entity wore when its model reach was last measured.
public final class ModelReachState {
    // Head item of the last measurement.
    ItemStack head = ItemStack.EMPTY;
    // Damage of the head item at the last measurement.
    int headDamage;
    // Main hand item of the last measurement.
    ItemStack mainHand = ItemStack.EMPTY;
    // Damage of the main hand item at the last measurement.
    int mainHandDamage;
    // Off hand item of the last measurement.
    ItemStack offHand = ItemStack.EMPTY;
    // Damage of the off hand item at the last measurement.
    int offHandDamage;
    // Model generation of the last measurement.
    int generation = -1;
    // Reach of the head item around the head pivot at full size.
    float headRadius;
    // Reach of the held items around the head pivot at full size.
    float handRadius;
}
