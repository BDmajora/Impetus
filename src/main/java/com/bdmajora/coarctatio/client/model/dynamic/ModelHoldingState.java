package com.bdmajora.coarctatio.client.model.dynamic;

import net.minecraft.client.renderer.block.model.IBakedModel;

// Implemented on BlockStateBase by mixin: one soft reference per state so the chunk builder's model lookup is a field read, not a registry probe
public interface ModelHoldingState {
    IBakedModel coarctatio$cachedModel();

    void coarctatio$cacheModel(IBakedModel model);
}
