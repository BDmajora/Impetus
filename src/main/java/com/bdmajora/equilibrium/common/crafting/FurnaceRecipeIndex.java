package com.bdmajora.equilibrium.common.crafting;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Per-item view over one of FurnaceRecipes' maps, rebuilt lazily when the map's size changes so mods editing getSmeltingList() directly are still honoured; entries are kept in the map's iteration order so the first-match-wins semantics of vanilla's scan are preserved for items with several overlapping entries
public final class FurnaceRecipeIndex<V> {
    private final Map<ItemStack, V> source;
    private final Map<Item, List<Map.Entry<ItemStack, V>>> byItem = new HashMap<>();
    private int builtForSize = -1;

    public FurnaceRecipeIndex(Map<ItemStack, V> source) {
        this.source = source;
    }

    // The candidate entries for a stack's item, or an empty list; the caller still runs vanilla's compareItemStacks so the wildcard rule is exactly vanilla's
    public List<Map.Entry<ItemStack, V>> candidates(ItemStack stack) {
        if (this.builtForSize != this.source.size()) {
            this.rebuild();
        }
        List<Map.Entry<ItemStack, V>> entries = this.byItem.get(stack.getItem());
        return entries == null ? java.util.Collections.emptyList() : entries;
    }

    // Any put or remove goes through here so a value swap on an existing key, which leaves the size unchanged, still invalidates
    public void invalidate() {
        this.builtForSize = -1;
    }

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
