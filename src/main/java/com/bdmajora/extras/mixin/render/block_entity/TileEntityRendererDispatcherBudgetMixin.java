package com.bdmajora.extras.mixin.render.block_entity;

import com.bdmajora.extras.client.budget.RenderBudgetController;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes TileEntityRendererDispatcher.
@Mixin(TileEntityRendererDispatcher.class)
public class TileEntityRendererDispatcherBudgetMixin {
    // Runs at the start of render and can cancel it.
    @Inject(
            method = "render(Lnet/minecraft/tileentity/TileEntity;FI)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void impetus$budgetBlockEntity(TileEntity blockEntity, float partialTicks, int destroyStage, CallbackInfo ci) {
        if (RenderBudgetController.shouldCullBlockEntity(blockEntity)) {
            ci.cancel();
        }
    }
}
