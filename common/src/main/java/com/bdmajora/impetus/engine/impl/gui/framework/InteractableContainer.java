package com.bdmajora.impetus.engine.impl.gui.framework;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

// Interactable that forwards events to children and manages keyboard focus.
public interface InteractableContainer extends Interactable {
    // Returns the children in tree order.
    Stream<? extends Interactable> interactableChildren();

    // Returns the child that captured the last click.
    Interactable getCapturedChild();
    // Stores the child that captured the last click.
    void setCapturedChild(Interactable child);

    // Returns whether a failed child was replaced and false to rethrow the failure.
    default boolean recoverChild(Interactable child, Throwable failure) {
        return false;
    }

    // Asks one child and answers the fallback when a failure was recovered.
    private boolean ask(Interactable child, Predicate<Interactable> question, boolean fallback) {
        try {
            return question.test(child);
        } catch (Throwable broken) {
            if (this.recoverChild(child, broken)) {
                return fallback;
            }
            throw broken;
        }
    }

    // Runs an action on a copy of the children until one handles it.
    private boolean runSingleChildAction(Predicate<Interactable> action) {
        List<? extends Interactable> children = interactableChildren().toList();
        for (int i = 0; i < children.size(); i++) {
            if (this.ask(children.get(i), action, true)) {
                return true;
            }
        }
        return false;
    }

    // Returns whether the cursor is over any child.
    default boolean isMouseOver(double mouseX, double mouseY) {
        Predicate<Interactable> over = child -> child.isMouseOver(mouseX, mouseY);
        return interactableChildren().anyMatch(child -> this.ask(child, over, false));
    }

    // Returns whether a child or one of its descendants holds focus.
    private static boolean holdsFocus(Interactable child) {
        return child instanceof Focusable focusable && focusable.isFocused()
                || child instanceof InteractableContainer container && container.getFocusedChild() != null;
    }

    // Returns the child that holds focus directly or through a descendant or null.
    default Interactable getFocusedChild() {
        return interactableChildren().filter(child -> this.ask(child, InteractableContainer::holdsFocus, false)).findFirst().orElse(null);
    }

    // Drops the focus of a child and its descendants and returns false.
    private static boolean dropFocus(Interactable child) {
        if (child instanceof Focusable focusable && focusable.isFocused()) {
            focusable.setFocused(false);
        }
        if (child instanceof InteractableContainer container) {
            container.clearFocus();
        }
        return false;
    }

    // Focuses a child that accepts focus and returns whether it did.
    private static boolean takeFocus(Interactable child) {
        Focusable focusable = (Focusable) child;
        if (!focusable.canFocus()) {
            return false;
        }
        focusable.setFocused(true);
        return true;
    }

    // Returns the focused control at any depth or null.
    default Focusable getFocusedControl() {
        Interactable child = getFocusedChild();
        if (child instanceof Focusable focusable) {
            return focusable;
        }
        return child instanceof InteractableContainer container ? container.getFocusedControl() : null;
    }

    // Drops focus from every descendant.
    default void clearFocus() {
        interactableChildren().forEach(child -> this.ask(child, InteractableContainer::dropFocus, false));
    }

    // Moves focus one control forward or backward and returns false past the end.
    default boolean moveFocus(boolean forward) {
        List<? extends Interactable> children = interactableChildren().toList();
        Interactable current = getFocusedChild();
        // Lets a focused child container keep the step first
        if (current instanceof InteractableContainer container && container.moveFocus(forward)) {
            return true;
        }
        if (current instanceof Focusable) {
            this.ask(current, InteractableContainer::dropFocus, false);
        }

        int step = forward ? 1 : -1;
        int index = current != null ? children.indexOf(current) + step : forward ? 0 : children.size() - 1;
        for (; index >= 0 && index < children.size(); index += step) {
            Interactable child = children.get(index);
            if (child instanceof Focusable && this.ask(child, InteractableContainer::takeFocus, false)) {
                return true;
            }
            if (child instanceof InteractableContainer container && container.moveFocus(forward)) {
                return true;
            }
        }
        return false;
    }

    // Forwards a click to the first child that handles it and moves focus to that child.
    @Override
    default boolean mouseClicked(InteractionContext context, double mouseX, double mouseY, int button) {
        setCapturedChild(null);
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

    // Forwards a release to the captured child or the first child that handles it.
    @Override
    default boolean mouseReleased(InteractionContext context, double mouseX, double mouseY, int button) {
        Interactable captured = getCapturedChild();
        setCapturedChild(null);
        if (captured != null) {
            this.ask(captured, child -> child.mouseReleased(context, mouseX, mouseY, button), true);
            return true;
        }
        return runSingleChildAction(i -> i.mouseReleased(context, mouseX, mouseY, button));
    }

    // Forwards a drag to the captured child or the first child that handles it.
    @Override
    default boolean mouseDragged(InteractionContext context, double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        Interactable captured = getCapturedChild();
        if (captured != null) {
            return this.ask(captured, child -> child.mouseDragged(context, mouseX, mouseY, button, deltaX, deltaY), true);
        }
        return runSingleChildAction(i -> i.isMouseOver(mouseX, mouseY) && i.mouseDragged(context, mouseX, mouseY, button, deltaX, deltaY));
    }

    // Forwards a scroll to the first child under the cursor that handles it.
    @Override
    default boolean mouseScrolled(InteractionContext context, double mouseX, double mouseY, double deltaX, double deltaY) {
        return runSingleChildAction(i -> i.isMouseOver(mouseX, mouseY) && i.mouseScrolled(context, mouseX, mouseY, deltaX, deltaY));
    }

    // Forwards a key to the focused child first and then to the other children.
    @Override
    default boolean keyTyped(InteractionContext context, char typedChar, int keyCode) {
        Interactable focused = getFocusedChild();
        if (focused != null && this.ask(focused, child -> child.keyTyped(context, typedChar, keyCode), true)) {
            return true;
        }
        return runSingleChildAction(i -> i != focused && i.keyTyped(context, typedChar, keyCode));
    }

    // Forwards a key and moves focus when an unused Tab arrives.
    default boolean dispatchKeyTyped(InteractionContext context, char typedChar, int keyCode) {
        if (keyTyped(context, typedChar, keyCode)) {
            return true;
        }
        if (!Keys.isTab(typedChar, keyCode)) {
            return false;
        }
        boolean forward = !context.isSpecialKeyDown(InteractionContext.SpecialKey.SHIFT);
        // Retries once to wrap around
        return moveFocus(forward) || moveFocus(forward);
    }
}
