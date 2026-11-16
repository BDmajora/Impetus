package com.bdmajora.impetus.engine.impl.gui.frame;

import com.bdmajora.impetus.api.options.control.Control;
import com.bdmajora.impetus.api.options.control.ControlElement;
import com.bdmajora.impetus.engine.impl.gui.framework.DrawContext;
import com.bdmajora.impetus.engine.impl.gui.framework.Interactable;
import com.bdmajora.impetus.engine.impl.gui.framework.InteractableContainer;
import com.bdmajora.impetus.engine.impl.gui.framework.Renderable;
import com.bdmajora.impetus.engine.impl.gui.widgets.AbstractWidget;
import com.bdmajora.impetus.engine.impl.util.Dim2i;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

// Base of widgets that hold and forward to child widgets.
public abstract class AbstractFrame extends AbstractWidget implements InteractableContainer {
    // Bounds of the frame.
    protected Dim2i dim;
    // Child widgets in tree order.
    protected final List<AbstractWidget> children = new ArrayList<>();
    // Children that draw.
    protected final List<Renderable> drawable = new ArrayList<>();
    // Option rows of this frame and its nested frames.
    protected final List<ControlElement<?>> controlElements = new ArrayList<>();
    // Outline state of the frame.
    protected boolean renderOutline;
    // Child that captured the last click.
    private Interactable capturedChild;

    // Creates a frame and rejects null bounds.
    public AbstractFrame(Dim2i dim, boolean renderOutline) {
        this.dim = Objects.requireNonNull(dim, "dim");
        this.renderOutline = renderOutline;
    }

    // Replaces the drawable children and option rows with those of the current children.
    public void buildFrame() {
        this.drawable.clear();
        this.controlElements.clear();
        for (AbstractWidget element : this.children) {
            if (element instanceof AbstractFrame) {
                this.controlElements.addAll(((AbstractFrame) element).controlElements);
            }
            if (element instanceof ControlElement<?>) {
                this.controlElements.add((ControlElement<?>) element);
            }
            if (element instanceof Renderable) {
                this.drawable.add((Renderable) element);
            }
        }
    }

    // Draws the outline and every child and replaces a row that throws.
    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float delta) {
        if (this.renderOutline) {
            drawContext.drawBorder(this.dim.x(), this.dim.y(), this.dim.getLimitX(), this.dim.getLimitY(), 0xFFAAAAAA);
        }
        // Records the draw state to restore after a failure
        int matrices = drawContext.matrixDepth();
        int scissors = drawContext.scissorDepth();
        // Visits each drawable child by index
        for (int i = 0; i < this.drawable.size(); i++) {
            Renderable drawable = this.drawable.get(i);
            // Moves the cursor away from siblings of a captured child
            boolean isOtherWhileCapturing = this.capturedChild != null && drawable != this.capturedChild;
            int x = isOtherWhileCapturing ? -1 : mouseX;
            int y = isOtherWhileCapturing ? -1 : mouseY;
            try {
                drawable.render(drawContext, x, y, delta);
            } catch (Throwable broken) {
                if (!(drawable instanceof Interactable child) || !this.recoverChild(child, broken)) {
                    throw broken;
                }
                drawContext.unwindTo(matrices, scissors);
                this.drawable.get(i).render(drawContext, x, y, delta);
            }
        }
    }

    // Replaces a failed option row with a placeholder and returns whether it did.
    @Override
    public boolean recoverChild(Interactable child, Throwable failure) {
        if (failure instanceof VirtualMachineError || !(child instanceof ControlElement<?> row)) {
            return false;
        }
        int index = indexOf(this.children, row);
        if (index < 0) {
            return false;
        }
        ControlElement<?> placeholder = Control.placeholderFor(row, failure);
        // Rejects a placeholder that is the failed row itself
        if (placeholder == row) {
            return false;
        }
        this.children.set(index, placeholder);
        replace(this.drawable, row, placeholder);
        replace(this.controlElements, row, placeholder);
        // Ends a drag on the replaced row
        if (this.capturedChild == row) {
            this.capturedChild = null;
        }
        return true;
    }

    // Returns the index of an element by identity or minus one.
    private static int indexOf(List<?> list, Object element) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == element) {
                return i;
            }
        }
        return -1;
    }

    // Replaces an element by identity when the list holds it.
    private static <T> void replace(List<T> list, Object element, T replacement) {
        int index = indexOf(list, element);
        if (index >= 0) {
            list.set(index, replacement);
        }
    }

    // Returns the children that take input.
    @Override
    public Stream<? extends Interactable> interactableChildren() {
        return this.children.stream();
    }

    // Returns the child that captured the last click.
    @Override
    public Interactable getCapturedChild() {
        return this.capturedChild;
    }

    // Stores the child that captured the last click.
    @Override
    public void setCapturedChild(Interactable child) {
        this.capturedChild = child;
    }

    // Returns the bounds of the frame.
    public Dim2i getDimensions() {
        return this.dim;
    }
}