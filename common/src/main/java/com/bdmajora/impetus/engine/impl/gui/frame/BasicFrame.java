package com.bdmajora.impetus.engine.impl.gui.frame;

import com.bdmajora.impetus.engine.impl.gui.widgets.AbstractWidget;
import com.bdmajora.impetus.engine.impl.util.Dim2i;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

// Frame whose children come from factories that receive the frame bounds.
public class BasicFrame extends AbstractFrame {

    // Factories of the children.
    protected List<Function<Dim2i, AbstractWidget>> functions;

    // Creates a frame from a copy of the factories and rejects null factories.
    public BasicFrame(Dim2i dim, boolean renderOutline, List<Function<Dim2i, AbstractWidget>> functions) {
        super(dim, renderOutline);
        this.functions = List.copyOf(functions);
        this.buildFrame();
    }

    // Creates a builder.
    public static Builder createBuilder() {
        return new Builder();
    }

    // Creates every child from its factory at the frame bounds and rejects a null child.
    @Override
    public void buildFrame() {
        this.children.clear();
        this.drawable.clear();
        this.controlElements.clear();

        for (Function<Dim2i, AbstractWidget> function : this.functions) {
            this.children.add(Objects.requireNonNull(function.apply(this.dim), "child"));
        }

        super.buildFrame();
    }

    // Collects the settings of a frame.
    public static class Builder {
        // Factories of the children.
        private final List<Function<Dim2i, AbstractWidget>> functions = new ArrayList<>();
        // Bounds of the frame.
        private Dim2i dim;
        // Outline state of the frame.
        private boolean renderOutline;

        // Stores the bounds of the frame.
        public Builder setDimension(Dim2i dim) {
            this.dim = dim;
            return this;
        }

        // Stores whether the frame draws an outline.
        public Builder shouldRenderOutline(boolean renderOutline) {
            this.renderOutline = renderOutline;
            return this;
        }

        // Adds a child factory and rejects null.
        public Builder addChild(Function<Dim2i, AbstractWidget> function) {
            this.functions.add(Objects.requireNonNull(function, "function"));
            return this;
        }

        // Creates the frame and rejects missing bounds.
        public BasicFrame build() {
            Objects.requireNonNull(this.dim, "Dimension must be specified");

            return new BasicFrame(this.dim, this.renderOutline, this.functions);
        }
    }
}