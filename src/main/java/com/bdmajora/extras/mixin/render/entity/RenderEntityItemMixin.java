package com.bdmajora.extras.mixin.render.entity;

import com.bdmajora.extras.Extras;
import net.minecraft.client.renderer.entity.RenderEntityItem;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Mixin that changes RenderEntityItem.
@Mixin(RenderEntityItem.class)
public class RenderEntityItemMixin {
    // Runs at the start of getModelCount and can cancel it.
    @Inject(method = "getModelCount", at = @At("HEAD"), cancellable = true)
    private void impetus$modelCount(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if (!Extras.options().render.droppedItemsFancy) {
            cir.setReturnValue(1);
        }
    }
}
