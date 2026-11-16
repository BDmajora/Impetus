package com.bdmajora.fulgor.api;

// Contract of the light info block.
public interface LightInfoBlock {
    // Returns whether position aware light value.
    boolean fulgor$hasPositionAwareLightValue();

    // Returns whether position aware opacity.
    boolean fulgor$hasPositionAwareOpacity();
}
