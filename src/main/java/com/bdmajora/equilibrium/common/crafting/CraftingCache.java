package com.bdmajora.equilibrium.common.crafting;

import net.minecraft.item.ItemStack;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.world.World;

import java.util.LinkedHashMap;
import java.util.Map;

// Remembers which recipe matches a crafting grid.
public final class CraftingCache {
    // Largest number of remembered grids.
    private static final int CAPACITY = 4096;
    // Marker for a grid without a recipe.
    private static final IRecipe NO_MATCH = new NoRecipe();

    // Recipes by grid in order of use.
    private static final Map<CraftMatrixKey, IRecipe> CACHE = new LinkedHashMap<CraftMatrixKey, IRecipe>(256, 0.75F, true) {
        // Returns whether the oldest entry exceeds the capacity.
        @Override
        protected boolean removeEldestEntry(Map.Entry<CraftMatrixKey, IRecipe> eldest) {
            return this.size() > CAPACITY;
        }
    };
    // Size of the recipe registry when the cache was filled.
    private static int registrySize = -1;
    // Recipe that matched last.
    private static IRecipe lastMatch;

    // Prevents instantiation
    private CraftingCache() {
    }

    // Returns the recipe that matches the grid or null.
    public static synchronized IRecipe findMatchingRecipe(InventoryCrafting matrix, World world) {
        int size = CraftingManager.REGISTRY.getKeys().size();
        if (size != registrySize) {
            CACHE.clear();
            lastMatch = null;
            registrySize = size;
        }
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

    // Returns the first registered recipe that matches the grid.
    private static IRecipe scan(InventoryCrafting matrix, World world) {
        for (IRecipe recipe : CraftingManager.REGISTRY) {
            if (recipe.matches(matrix, world)) {
                lastMatch = recipe;
                return recipe;
            }
        }
        return null;
    }

    // Recipe that never matches.
    private static final class NoRecipe extends net.minecraftforge.registries.IForgeRegistryEntry.Impl<IRecipe> implements IRecipe {
        // Returns false.
        @Override
        public boolean matches(InventoryCrafting inv, World world) {
            return false;
        }

        // Returns the empty stack.
        @Override
        public ItemStack getCraftingResult(InventoryCrafting inv) {
            return ItemStack.EMPTY;
        }

        // Returns false.
        @Override
        public boolean canFit(int width, int height) {
            return false;
        }

        // Returns the empty stack.
        @Override
        public ItemStack getRecipeOutput() {
            return ItemStack.EMPTY;
        }
    }
}
