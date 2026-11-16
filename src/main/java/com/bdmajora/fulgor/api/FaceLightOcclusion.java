package com.bdmajora.fulgor.api;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;

// Contract of the face light occlusion.
public interface FaceLightOcclusion {
    // Returns the directional light opacity.
    int getDirectionalLightOpacity(IBlockState state, EnumFacing direction);
}
