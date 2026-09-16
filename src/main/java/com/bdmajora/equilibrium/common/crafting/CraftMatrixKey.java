package com.bdmajora.equilibrium.common.crafting;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.Arrays;

// The shape of a crafting grid reduced to (item, meta) per slot; stacks with NBT never become keys since a recipe may read the tag, and the count is dropped since vanilla recipes only look at it through getRemainingItems, which is not cached
public final class CraftMatrixKey {
    private final Item[] items;
    private final int[] metas;
    private final int width;
    private final int hash;

    private CraftMatrixKey(Item[] items, int[] metas, int width) {
        this.items = items;
        this.metas = metas;
        this.width = width;
        this.hash = 31 * (31 * Arrays.hashCode(items) + Arrays.hashCode(metas)) + width;
    }

    // Null when any slot carries NBT, which tells the caller not to cache this grid
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

    @Override
    public int hashCode() {
        return this.hash;
    }
}
