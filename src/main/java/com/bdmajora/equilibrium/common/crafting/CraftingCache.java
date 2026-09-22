package com.bdmajora.equilibrium.common.crafting;

import net.minecraft.item.ItemStack;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.world.World;

import java.util.LinkedHashMap;
import java.util.Map;

// Remembers which recipe (or that none) matched a grid shape, after Universal Tweaks' crafting cache: vanilla walks every registered recipe on every slot change and on every crafting-table tick of every automation block, and a large pack registers tens of thousands. A hit is re-verified with matches() so a recipe that reads more than item and meta can still say no, and the whole cache is dropped whenever the registry's size changes so a reload starts clean
public final class CraftingCache {
    private static final int CAPACITY = 4096;
    // Sentinel for "scanned everything, nothing matched"; null in the map would be indistinguishable from absent
    private static final IRecipe NO_MATCH = new NoRecipe();

    private static final Map<CraftMatrixKey, IRecipe> CACHE = new LinkedHashMap<CraftMatrixKey, IRecipe>(256, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<CraftMatrixKey, IRecipe> eldest) {
            return this.size() > CAPACITY;
        }
    };
    private static int registrySize = -1;
    private static IRecipe lastMatch;

    private CraftingCache() {
    }

    public static synchronized IRecipe findMatchingRecipe(InventoryCrafting matrix, World world) {
        int size = CraftingManager.REGISTRY.getKeys().size();
        if (size != registrySize) {
            CACHE.clear();
            lastMatch = null;
            registrySize = size;
        }
        // The same grid is queried repeatedly while a player arranges it, and automation re-asks with the same contents every tick
        IRecipe previous = lastMatch;
        if (previous != null && previous.matches(matrix, world)) {
            return previous;
        }
        CraftMatrixKey key = CraftMatrixKey.of(matrix);
        if (key == null) {
            return scan(matrix, world);
        }
        IRecipe cached = CACHE.get(key);
        if (cached == NO_MATCH) {
            return null;
        }
        if (cached != null && cached.matches(matrix, world)) {
            lastMatch = cached;
            return cached;
        }
        IRecipe found = scan(matrix, world);
        CACHE.put(key, found == null ? NO_MATCH : found);
        return found;
    }

    // Vanilla's loop, kept separate so the NBT path and a cache miss share it
    private static IRecipe scan(InventoryCrafting matrix, World world) {
        for (IRecipe recipe : CraftingManager.REGISTRY) {
            if (recipe.matches(matrix, world)) {
                lastMatch = recipe;
                return recipe;
            }
        }
        return null;
    }

    // Never registered and never returned; only its identity is used
    private static final class NoRecipe extends net.minecraftforge.registries.IForgeRegistryEntry.Impl<IRecipe> implements IRecipe {
        @Override
        public boolean matches(InventoryCrafting inv, World world) {
            return false;
        }

        @Override
        public ItemStack getCraftingResult(InventoryCrafting inv) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean canFit(int width, int height) {
            return false;
        }

        @Override
        public ItemStack getRecipeOutput() {
            return ItemStack.EMPTY;
        }
    }
}
