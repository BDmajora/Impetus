package com.bdmajora.impetus.impl.extensions;

// Contract of the sprite extension.
public interface SpriteExtension {
    // Marks the sprite as drawn this frame.
    void impetus$markActive();
    // Returns whether the sprite should animate this tick.
    boolean impetus$shouldUpdate();
}
