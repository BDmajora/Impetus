package com.bdmajora.equilibrium.mixin.block.furnace_recipes;

import com.bdmajora.equilibrium.common.crafting.FurnaceRecipeIndex;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;

// getSmeltingResult and getSmeltingExperience each walk every smelting entry with compareItemStacks, and a furnace with an input asks once per tick (Chibi's optimizeFurnaceRecipeStore, done as an index over vanilla's maps rather than a replacement map so no registration order or wildcard semantics change); the lookup now only visits entries registered for the stack's item
@Mixin(FurnaceRecipes.class)
public abstract class FurnaceRecipesMixin {
    @Shadow
    @Final
    private Map<ItemStack, ItemStack> smeltingList;

    @Shadow
    @Final
    private Map<ItemStack, Float> experienceList;

    @Shadow
    protected abstract boolean compareItemStacks(ItemStack stack1, ItemStack stack2);

    private FurnaceRecipeIndex<ItemStack> equilibrium$inputs;
    private FurnaceRecipeIndex<Float> equilibrium$outputs;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void equilibrium$buildIndexes(CallbackInfo ci) {
        this.equilibrium$inputs = new FurnaceRecipeIndex<>(this.smeltingList);
        this.equilibrium$outputs = new FurnaceRecipeIndex<>(this.experienceList);
    }

    // The only registration path vanilla and Forge offer; direct map edits are caught by the size check
    @Inject(method = "addSmeltingRecipe", at = @At("RETURN"))
    private void equilibrium$invalidateOnAdd(ItemStack input, ItemStack stack, float experience, CallbackInfo ci) {
        // The constructor registers vanilla's entries before the indexes exist
        if (this.equilibrium$inputs != null) {
            this.equilibrium$inputs.invalidate();
            this.equilibrium$outputs.invalidate();
        }
    }

    @Inject(method = "getSmeltingResult", at = @At("HEAD"), cancellable = true)
    private void equilibrium$indexedResult(ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
        if (this.equilibrium$inputs == null || stack.isEmpty()) {
            return;
        }
        List<Map.Entry<ItemStack, ItemStack>> candidates = this.equilibrium$inputs.candidates(stack);
        for (int i = 0, n = candidates.size(); i < n; i++) {
            Map.Entry<ItemStack, ItemStack> entry = candidates.get(i);
            if (this.compareItemStacks(stack, entry.getKey())) {
                cir.setReturnValue(entry.getValue());
                return;
            }
        }
        cir.setReturnValue(ItemStack.EMPTY);
    }

    @Inject(method = "getSmeltingExperience", at = @At("HEAD"), cancellable = true)
    private void equilibrium$indexedExperience(ItemStack stack, CallbackInfoReturnable<Float> cir) {
        if (this.equilibrium$outputs == null || stack.isEmpty()) {
            return;
        }
        // Forge asks the item first and only falls back to the table on a negative answer; that order is kept
        float fromItem = stack.getItem().getSmeltingExperience(stack);
        if (fromItem != -1.0F) {
            cir.setReturnValue(fromItem);
            return;
        }
        List<Map.Entry<ItemStack, Float>> candidates = this.equilibrium$outputs.candidates(stack);
        for (int i = 0, n = candidates.size(); i < n; i++) {
            Map.Entry<ItemStack, Float> entry = candidates.get(i);
            if (this.compareItemStacks(stack, entry.getKey())) {
                cir.setReturnValue(entry.getValue());
                return;
            }
        }
        cir.setReturnValue(0.0F);
    }
}
