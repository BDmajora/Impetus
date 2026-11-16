package com.bdmajora.equilibrium.common.crafting;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.Arrays;

// Key made of the items and metadata of a crafting grid.
public final class CraftMatrixKey {
    // Item of each slot.
    private final Item[] items;
    // Metadata of each slot.
    private final int[] metas;
    // Width of the grid.
    private final int width;
    // Hash computed once at construction.
    private final int hash;

    // Creates a key from its parts.
    private CraftMatrixKey(Item[] items, int[] metas, int width) {
        this.items = items;
        this.metas = metas;
        this.width = width;
        this.hash = 31 * (31 * Arrays.hashCode(items) + Arrays.hashCode(metas)) + width;
    }

    // Returns the key of the grid or null when a stack carries tags.
    public static CraftMatrixKey of(InventoryCrafting matrix) {
        int size = matrix.getSizeInventory();
        Item[] items = new Item[size];
        int[] metas = new int[size];
        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = matrix.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.hasTagCompound()) {
                return null;
            }
            items[slot] = stack.getItem();
            metas[slot] = stack.getMetadata();
        }
        return new CraftMatrixKey(items, metas, matrix.getWidth());
    }

    // Returns whether the other key describes the same grid.
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CraftMatrixKey)) {
            return false;
        }
        CraftMatrixKey key = (CraftMatrixKey) other;
        return this.width == key.width && this.hash == key.hash && Arrays.equals(this.items, key.items) && Arrays.equals(this.metas, key.metas);
    }

    // Returns the stored hash.
    @Override
    public int hashCode() {
        return this.hash;
    }
}
