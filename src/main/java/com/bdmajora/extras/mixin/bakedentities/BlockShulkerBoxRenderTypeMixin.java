package com.bdmajora.extras.mixin.bakedentities;

import com.bdmajora.extras.client.bakedentities.BakedEntities;
import net.minecraft.block.BlockShulkerBox;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumBlockRenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Reports MODEL instead of ENTITYBLOCK_ANIMATED while baked, which is what lets the chunk mesher and the block damage overlay pick the block up
@Mixin(BlockShulkerBox.class)
public abstract class BlockShulkerBoxRenderTypeMixin {
    @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
    private void impetus(IBlockState state, CallbackInfoReturnable<EnumBlockRenderType> cir) {
        if (BakedEntities.isBaked(state.getBlock())) {
            cir.setReturnValue(EnumBlockRenderType.MODEL);
        }
    }
}
