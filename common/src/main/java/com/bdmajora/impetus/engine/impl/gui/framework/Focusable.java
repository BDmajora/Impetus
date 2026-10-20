package com.bdmajora.impetus.engine.impl.gui.framework;

import com.bdmajora.impetus.engine.impl.util.Dim2i;

// A control that can hold keyboard focus; the flag lives on the control, so containers find the focus by asking their children and a rebuilt control handed the flag keeps it
public interface Focusable extends Interactable {
    // Whether Tab stops here now; a hidden, disabled or unavailable control is passed over
    default boolean canFocus() {
        return true;
    }

    boolean isFocused();

    void setFocused(boolean focused);

    // Bounds, so a scrolling frame can bring the focused control into view
    Dim2i getDimensions();
}
