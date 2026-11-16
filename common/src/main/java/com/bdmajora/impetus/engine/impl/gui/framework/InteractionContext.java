package com.bdmajora.impetus.engine.impl.gui.framework;

// Input state available while handling an event.
public interface InteractionContext {
    // Plays the click sound.
    default void playClickSound() {

    }

    // Returns whether a modifier key is held.
    default boolean isSpecialKeyDown(SpecialKey key) {
        return false;
    }

    // Modifier keys.
    enum SpecialKey {
        SHIFT,
        CTRL,
        ALT
    }
}
