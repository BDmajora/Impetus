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
import java.util.stream.Stream;

public abstract class AbstractFrame extends AbstractWidget implements InteractableContainer {
    protected Dim2i dim;
    protected final List<AbstractWidget> children = new ArrayList<>();
    protected final List<Renderable> drawable = new ArrayList<>();
    protected final List<ControlElement<?>> controlElements = new ArrayList<>();
    protected boolean renderOutline;
    private Interactable capturedChild;

    public AbstractFrame(Dim2i dim, boolean renderOutline) {
        this.dim = dim;
        this.renderOutline = renderOutline;
    }

    // Subclasses populate children here
    public void buildFrame() {
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

    // Draws every child, plus the outline when enabled; a row whose control throws is replaced and its placeholder drawn in its place the same frame
    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float delta) {
        if (this.renderOutline) {
            drawContext.drawBorder(this.dim.x(), this.dim.y(), this.dim.getLimitX(), this.dim.getLimitY(), 0xFFAAAAAA);
        }
        // What is pushed around the children, which a child that fails halfway is unwound back to
        int matrices = drawContext.matrixDepth();
        int scissors = drawContext.scissorDepth();
        // By index, since a replaced row is swapped in place
        for (int i = 0; i < this.drawable.size(); i++) {
            Renderable drawable = this.drawable.get(i);
            // While a child is captured (mid-drag), don't let the cursor also "hover" its siblings.
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

    // Puts a placeholder in place of an option row of this frame whose own code threw, in every list holding it; VM errors, other widgets and placeholders are never replaced
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
        // A placeholder runs no control code, so one that throws has nothing better to fall back to
        if (placeholder == row) {
            return false;
        }
        this.children.set(index, placeholder);
        replace(this.drawable, row, placeholder);
        replace(this.controlElements, row, placeholder);
        // A drag on the broken row ends with it
        if (this.capturedChild == row) {
            this.capturedChild = null;
        }
        return true;
    }

    // Position by identity, since an element may define its own equality
    private static int indexOf(List<?> list, Object element) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == element) {
                return i;
            }
        }
        return -1;
    }

    // Swaps one element for another where the list holds it
    private static <T> void replace(List<T> list, Object element, T replacement) {
        int index = indexOf(list, element);
        if (index >= 0) {
            list.set(index, replacement);
        }
    }

    // Children that take input
    @Override
    public Stream<? extends Interactable> interactableChildren() {
        return this.children.stream();
    }

    @Override
    public Interactable getCapturedChild() {
        return this.capturedChild;
    }

    @Override
    public void setCapturedChild(Interactable child) {
        this.capturedChild = child;
    }

    public Dim2i getDimensions() {
        return this.dim;
    }
}