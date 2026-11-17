package com.bdmajora.extras.mixin.bakedentities;

import com.bdmajora.extras.client.bakedentities.BakedEntities;
import net.minecraft.block.BlockEnderChest;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumBlockRenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Mixin that changes BlockEnderChest.
@Mixin(BlockEnderChest.class)
public abstract class BlockEnderChestRenderTypeMixin {
    // Runs at the start of getRenderType and can cancel it.
    @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
    private void impetus$bakedRenderType(IBlockState state, CallbackInfoReturnable<EnumBlockRenderType> cir) {
        if (BakedEntities.isBaked(state.getBlock())) {
            cir.setReturnValue(EnumBlockRenderType.MODEL);
        }
    }
}
