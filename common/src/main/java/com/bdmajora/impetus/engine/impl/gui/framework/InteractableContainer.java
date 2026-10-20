package com.bdmajora.impetus.engine.impl.gui.framework;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

public interface InteractableContainer extends Interactable {
    Stream<? extends Interactable> interactableChildren();

    /**
     * the child that most recently claimed a mouseClicked. while set, drag events
     * are routed straight to it instead of being re-checked against the cursor position each time,
     * so a drag started on e.g. a slider isn't dropped the moment the cursor strays off its narrow
     * hitbox. Cleared on mouseReleased.
     */
    Interactable getCapturedChild();
    void setCapturedChild(Interactable child);

    private boolean runSingleChildAction(Predicate<Interactable> action) {
        // Defensive copy to handle mutation
        return interactableChildren().toList().stream().anyMatch(action);
    }

    // Any child
    default boolean isMouseOver(double mouseX, double mouseY) {
        return interactableChildren().anyMatch(i -> i.isMouseOver(mouseX, mouseY));
    }

    // Whether a child holds keyboard focus, itself or through one of its own children
    private static boolean holdsFocus(Interactable child) {
        return child instanceof Focusable focusable && focusable.isFocused()
                || child instanceof InteractableContainer container && container.getFocusedChild() != null;
    }

    // The child holding keyboard focus, itself or through a descendant, or null
    default Interactable getFocusedChild() {
        return interactableChildren().filter(InteractableContainer::holdsFocus).findFirst().orElse(null);
    }

    // The focused control however deep it sits, or null
    default Focusable getFocusedControl() {
        Interactable child = getFocusedChild();
        if (child instanceof Focusable focusable) {
            return focusable;
        }
        return child instanceof InteractableContainer container ? container.getFocusedControl() : null;
    }

    // Drops keyboard focus everywhere below this container
    default void clearFocus() {
        interactableChildren().forEach(child -> {
            if (child instanceof Focusable focusable && focusable.isFocused()) {
                focusable.setFocused(false);
            }
            if (child instanceof InteractableContainer container) {
                container.clearFocus();
            }
        });
    }

    // One Tab step: the next focusable control past the focused one, or the first (the last going backwards) when nothing here has focus; false once the step runs off the end, leaving nothing here focused
    default boolean moveFocus(boolean forward) {
        List<? extends Interactable> children = interactableChildren().toList();
        Interactable current = getFocusedChild();
        // A child container keeps the step while it has controls left in that direction
        if (current instanceof InteractableContainer container && container.moveFocus(forward)) {
            return true;
        }
        if (current instanceof Focusable focusable) {
            focusable.setFocused(false);
        }

        int step = forward ? 1 : -1;
        int index = current != null ? children.indexOf(current) + step : forward ? 0 : children.size() - 1;
        for (; index >= 0 && index < children.size(); index += step) {
            Interactable child = children.get(index);
            if (child instanceof Focusable focusable && focusable.canFocus()) {
                focusable.setFocused(true);
                return true;
            }
            if (child instanceof InteractableContainer container && container.moveFocus(forward)) {
                return true;
            }
        }
        return false;
    }

    // First child that handles it; a click drops focus everywhere here and the control that takes it is focused
    @Override
    default boolean mouseClicked(InteractionContext context, double mouseX, double mouseY, int button) {
        setCapturedChild(null);
        // Cleared before the click lands, so the control it lands on ends up the only one focused even when the click rebuilds the frame under it
        clearFocus();
        return runSingleChildAction(i -> {
            if (i.mouseClicked(context, mouseX, mouseY, button)) {
                setCapturedChild(i);
                if (i instanceof Focusable focusable && focusable.canFocus()) {
                    focusable.setFocused(true);
                }
                return true;
            }
            return false;
        });
    }

    // First child that handles it
    @Override
    default boolean mouseReleased(InteractionContext context, double mouseX, double mouseY, int button) {
        Interactable captured = getCapturedChild();
        setCapturedChild(null);
        if (captured != null) {
            captured.mouseReleased(context, mouseX, mouseY, button);
            return true;
        }
        return runSingleChildAction(i -> i.mouseReleased(context, mouseX, mouseY, button));
    }

    // First child that handles it
    @Override
    default boolean mouseDragged(InteractionContext context, double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        Interactable captured = getCapturedChild();
        if (captured != null) {
            return captured.mouseDragged(context, mouseX, mouseY, button, deltaX, deltaY);
        }
        return runSingleChildAction(i -> i.isMouseOver(mouseX, mouseY) && i.mouseDragged(context, mouseX, mouseY, button, deltaX, deltaY));
    }

    // First child that handles it
    @Override
    default boolean mouseScrolled(InteractionContext context, double mouseX, double mouseY, double deltaX, double deltaY) {
        return runSingleChildAction(i -> i.isMouseOver(mouseX, mouseY) && i.mouseScrolled(context, mouseX, mouseY, deltaX, deltaY));
    }

    // The focused child first, so a focused button never takes Space from the search bar, then the others in order
    @Override
    default boolean keyTyped(InteractionContext context, char typedChar, int keyCode) {
        Interactable focused = getFocusedChild();
        if (focused != null && focused.keyTyped(context, typedChar, keyCode)) {
            return true;
        }
        return runSingleChildAction(i -> i != focused && i.keyTyped(context, typedChar, keyCode));
    }

    // A screen's key event: keyTyped, then a Tab no control took moves focus (Shift+Tab backwards), wrapping round at either end
    default boolean dispatchKeyTyped(InteractionContext context, char typedChar, int keyCode) {
        if (keyTyped(context, typedChar, keyCode)) {
            return true;
        }
        if (!Keys.isTab(typedChar, keyCode)) {
            return false;
        }
        boolean forward = !context.isSpecialKeyDown(InteractionContext.SpecialKey.SHIFT);
        // A step off the end leaves nothing focused, so the second starts over from the other end; both fail only when nothing can take focus
        return moveFocus(forward) || moveFocus(forward);
    }
}
