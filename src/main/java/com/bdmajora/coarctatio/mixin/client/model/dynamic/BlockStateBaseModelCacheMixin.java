package com.bdmajora.coarctatio.mixin.client.model.dynamic;

import com.bdmajora.coarctatio.client.model.dynamic.ModelHoldingState;
import net.minecraft.block.state.BlockStateBase;
import net.minecraft.client.renderer.block.model.IBakedModel;
import org.spongepowered.asm.mixin.Mixin;

import java.lang.ref.SoftReference;

// Mixin that changes BlockStateBase.
@Mixin(BlockStateBase.class)
public abstract class BlockStateBaseModelCacheMixin implements ModelHoldingState {
    // Softly held baked model of the state.
    private volatile SoftReference<IBakedModel> coarctatio$model;

    // Handles cached model.
    @Override
    public IBakedModel coarctatio$cachedModel() {
        SoftReference<IBakedModel> reference = this.coarctatio$model;
        return reference != null ? reference.get() : null;
    }

    // Handles cache model.
    @Override
    public void coarctatio$cacheModel(IBakedModel model) {
        this.coarctatio$model = model != null ? new SoftReference<>(model) : null;
    }
}
