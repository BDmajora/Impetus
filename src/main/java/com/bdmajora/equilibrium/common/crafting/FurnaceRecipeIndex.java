package com.bdmajora.equilibrium.common.crafting;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Index of smelting recipes by input item.
public final class FurnaceRecipeIndex<V> {
    // Recipes of the game.
    private final Map<ItemStack, V> source;
    // Recipes by input item.
    private final Map<Item, List<Map.Entry<ItemStack, V>>> byItem = new HashMap<>();
    // Size of the source when the index was built.
    private int builtForSize = -1;

    // Creates an index and rejects a null source.
    public FurnaceRecipeIndex(Map<ItemStack, V> source) {
        this.source = java.util.Objects.requireNonNull(source, "source");
    }

    // Returns the recipes whose input item matches the stack.
    public List<Map.Entry<ItemStack, V>> candidates(ItemStack stack) {
        if (this.builtForSize != this.source.size()) {
            this.rebuild();
        }
        List<Map.Entry<ItemStack, V>> entries = this.byItem.get(stack.getItem());
        return entries == null ? java.util.Collections.emptyList() : entries;
    }

    // Forces a rebuild on the next lookup.
    public void invalidate() {
        this.builtForSize = -1;
    }

    // Rebuilds the index from the source.
    private void rebuild() {
        this.byItem.clear();
        for (Map.Entry<ItemStack, V> entry : this.source.entrySet()) {
            ItemStack key = entry.getKey();
            if (key.isEmpty()) {
                continue;
            }
            this.byItem.computeIfAbsent(key.getItem(), item -> new ArrayList<>(2)).add(entry);
        }
        this.builtForSize = this.source.size();
    }
}
