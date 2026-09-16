package com.bdmajora.fulgor.api;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;

// Implemented by blocks whose light opacity depends on the face light enters through (a partial cube open on one side); the async engine asks this before falling back to the scalar opacity
public interface FaceLightOcclusion {
    // 0-15 opacity for light entering through the given face
    int getDirectionalLightOpacity(IBlockState state, EnumFacing direction);
}
