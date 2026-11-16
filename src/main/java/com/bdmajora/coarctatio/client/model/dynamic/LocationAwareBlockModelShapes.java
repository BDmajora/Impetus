package com.bdmajora.coarctatio.client.model.dynamic;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;

// Model shape table that reports model locations.
public interface LocationAwareBlockModelShapes {
    // Returns the model location of the state.
    ModelResourceLocation coarctatio$locationForState(IBlockState state);
}
