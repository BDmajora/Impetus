package com.bdmajora.impetus.engine.impl.gui.framework;

// Element that reacts to the mouse and keyboard.
public interface Interactable {
    // Returns whether the cursor is over the element.
    boolean isMouseOver(double mouseX, double mouseY);

    // Handles a click and returns whether it was used.
    default boolean mouseClicked(InteractionContext context, double mouseX, double mouseY, int button) {
        return false;
    }

    // Handles a release and returns whether it was used.
    default boolean mouseReleased(InteractionContext context, double mouseX, double mouseY, int button) {
        return false;
    }

    // Handles a drag and returns whether it was used.
    default boolean mouseDragged(InteractionContext context, double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return false;
    }

    // Handles a scroll and returns whether it was used.
    default boolean mouseScrolled(InteractionContext context, double mouseX, double mouseY, double deltaX, double deltaY) {
        return false;
    }

    // Handles a key and returns whether it was used.
    default boolean keyTyped(InteractionContext context, char typedChar, int keyCode) {
        return false;
    }
}
