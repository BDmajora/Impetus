package com.bdmajora.impetus.engine.impl.gui.frame;

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

    // Draws every child, plus the outline when enabled
    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float delta) {
        if (this.renderOutline) {
            drawContext.drawBorder(this.dim.x(), this.dim.y(), this.dim.getLimitX(), this.dim.getLimitY(), 0xFFAAAAAA);
        }
        for (Renderable drawable : this.drawable) {
            // While a child is captured (mid-drag), don't let the cursor also "hover" its siblings.
            boolean isOtherWhileCapturing = this.capturedChild != null && drawable != this.capturedChild;
            drawable.render(drawContext, isOtherWhileCapturing ? -1 : mouseX, isOtherWhileCapturing ? -1 : mouseY, delta);
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
