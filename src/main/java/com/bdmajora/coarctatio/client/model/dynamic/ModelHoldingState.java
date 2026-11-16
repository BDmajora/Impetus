package com.bdmajora.coarctatio.client.model.dynamic;

import net.minecraft.client.renderer.block.model.IBakedModel;

// Block state that keeps its baked model.
public interface ModelHoldingState {
    // Returns the kept model or null.
    IBakedModel coarctatio$cachedModel();

    // Keeps the model.
    void coarctatio$cacheModel(IBakedModel model);
}
