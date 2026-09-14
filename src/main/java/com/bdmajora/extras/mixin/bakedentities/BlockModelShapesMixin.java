package com.bdmajora.extras.mixin.bakedentities;

import com.bdmajora.extras.client.bakedentities.BakedEntities;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BlockModelShapes;
import net.minecraft.client.renderer.block.model.IBakedModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Vanilla registers these blocks as built-in and never loads a model for them; the baked entity models are answered here, ahead of the state-to-model table that would return the missing model
@Mixin(BlockModelShapes.class)
public abstract class BlockModelShapesMixin {
    @Inject(method = "getModelForState", at = @At("HEAD"), cancellable = true)
    private void impetus$bakedEntityModel(IBlockState state, CallbackInfoReturnable<IBakedModel> cir) {
        IBakedModel model = BakedEntities.modelFor(state);
        if (model != null) {
            cir.setReturnValue(model);
        }
    }
}
