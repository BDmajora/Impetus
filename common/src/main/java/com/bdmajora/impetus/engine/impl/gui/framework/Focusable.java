package com.bdmajora.impetus.engine.impl.gui.framework;

import com.bdmajora.impetus.engine.impl.util.Dim2i;

// Control that can hold keyboard focus.
public interface Focusable extends Interactable {
    // Returns whether Tab can stop at the control.
    default boolean canFocus() {
        return true;
    }

    // Returns whether the control holds focus.
    boolean isFocused();

    // Stores whether the control holds focus.
    void setFocused(boolean focused);

    // Returns the bounds of the control.
    Dim2i getDimensions();
}
