package com.bdmajora.extras.client.bakedentities;

// Implemented on the animating block entities (chests, ender chests, shulker boxes) by mixin; settled means the static mesh draws it and the renderer stays away
public interface AnimatedBlockEntity {
    boolean impetus$isSettled();

    // True while the renderer must still draw it: mid-animation, or for a few ticks after settling so the mesh rebuild has landed before it stops
    boolean impetus$needsRenderer();
}
