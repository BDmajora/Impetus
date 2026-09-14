package com.bdmajora.extras.mixin.bakedentities;

import com.bdmajora.extras.client.bakedentities.BakedEntities;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumBlockRenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Signs never declare a render type of their own and inherit BlockContainer's INVISIBLE, so the switch to MODEL has to sit here; isBaked keeps every other container on vanilla's answer
@Mixin(BlockContainer.class)
public abstract class BlockContainerRenderTypeMixin {
    @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
    private void impetus$bakedRenderType(IBlockState state, CallbackInfoReturnable<EnumBlockRenderType> cir) {
        if (BakedEntities.isBaked(state.getBlock())) {
            cir.setReturnValue(EnumBlockRenderType.MODEL);
        }
    }
}
