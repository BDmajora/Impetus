package com.bdmajora.extras.client.bakedentities;

// Block entity that animates.
public interface AnimatedBlockEntity {
    // Returns whether the animation is at rest.
    boolean impetus$isSettled();

    // Returns whether the block entity needs its renderer.
    boolean impetus$needsRenderer();
}
