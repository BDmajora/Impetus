package com.bdmajora.coarctatio.client.model.dynamic;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;

// Implemented on BlockModelShapes by mixin; the state-to-location half of what vanilla's bakedModelStore used to answer
public interface LocationAwareBlockModelShapes {
    ModelResourceLocation coarctatio$locationForState(IBlockState state);
}
