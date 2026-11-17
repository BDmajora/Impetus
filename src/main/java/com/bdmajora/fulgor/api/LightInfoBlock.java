package com.bdmajora.fulgor.api;

// Contract of the light info block.
public interface LightInfoBlock {
    // Returns whether the block overrides the position aware getLightValue.
    boolean fulgor$hasPositionAwareLightValue();

    // Returns whether the block overrides the position aware getLightOpacity.
    boolean fulgor$hasPositionAwareOpacity();
}
