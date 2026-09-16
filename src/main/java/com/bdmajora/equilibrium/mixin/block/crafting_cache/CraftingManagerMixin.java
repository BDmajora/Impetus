package com.bdmajora.equilibrium.mixin.block.crafting_cache;

import com.bdmajora.equilibrium.common.crafting.CraftingCache;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Routes both registry walks through the cache; getRemainingItems repeats the same scan findMatchingRecipe just did, so a craft costs two full walks in vanilla
@Mixin(CraftingManager.class)
public abstract class CraftingManagerMixin {
    @Inject(method = "findMatchingRecipe", at = @At("HEAD"), cancellable = true)
    private static void equilibrium$cachedMatch(InventoryCrafting matrix, World world, CallbackInfoReturnable<IRecipe> cir) {
        cir.setReturnValue(CraftingCache.findMatchingRecipe(matrix, world));
    }

    @Inject(method = "getRemainingItems", at = @At("HEAD"), cancellable = true)
    private static void equilibrium$cachedRemaining(InventoryCrafting matrix, World world, CallbackInfoReturnable<NonNullList<ItemStack>> cir) {
        IRecipe recipe = CraftingCache.findMatchingRecipe(matrix, world);
        if (recipe != null) {
            cir.setReturnValue(recipe.getRemainingItems(matrix));
            return;
        }
        NonNullList<ItemStack> remaining = NonNullList.withSize(matrix.getSizeInventory(), ItemStack.EMPTY);
        for (int slot = 0; slot < remaining.size(); slot++) {
            remaining.set(slot, matrix.getStackInSlot(slot));
        }
        cir.setReturnValue(remaining);
    }
}
