package com.bdmajora.impetus.engine.impl.gui.frame;

import com.bdmajora.impetus.engine.impl.gui.framework.DrawContext;
import com.bdmajora.impetus.engine.impl.gui.framework.InteractionContext;
import com.bdmajora.impetus.engine.impl.gui.theme.DefaultColors;
import com.bdmajora.impetus.engine.impl.util.Dim2i;
import com.bdmajora.impetus.engine.impl.gui.frame.components.ScrollBarComponent;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

// Frame that clips an inner frame to a viewport and scrolls it with scroll bars.
public class ScrollableFrame extends AbstractFrame {

    // Origin of the inner frame.
    protected final Dim2i frameOrigin;
    // Inner frame that scrolls.
    protected final AbstractFrame frame;
    // Bounds of the inner frame before any bar narrows it.
    private final Dim2i contentDimension;

    // Horizontal overflow state of the content.
    private boolean canScrollHorizontal;
    // Vertical overflow state of the content.
    private boolean canScrollVertical;
    // Visible area of the content.
    private Dim2i viewPortDimension = null;
    // Vertical bar or null.
    private ScrollBarComponent verticalScrollBar = null;
    // Horizontal bar or null.
    private ScrollBarComponent horizontalScrollBar = null;
    // Color of the bar thumbs.
    private final int scrollBarAccentColor;

    // Creates a scrollable frame with the default thumb color.
    public ScrollableFrame(Dim2i dim, AbstractFrame frame, boolean renderOutline, AtomicReference<Integer> verticalScrollBarOffset, AtomicReference<Integer> horizontalScrollBarOffset) {
        this(dim, frame, renderOutline, verticalScrollBarOffset, horizontalScrollBarOffset, DefaultColors.ELEMENT_ACTIVATED);
    }

    // Creates a scrollable frame and rejects null bounds or a null inner frame and uses fresh offsets for null references.
    public ScrollableFrame(Dim2i dim, AbstractFrame frame, boolean renderOutline, AtomicReference<Integer> verticalScrollBarOffset, AtomicReference<Integer> horizontalScrollBarOffset, int scrollBarAccentColor) {
        super(dim, renderOutline);
        this.frame = Objects.requireNonNull(frame, "frame");
        verticalScrollBarOffset = verticalScrollBarOffset != null ? verticalScrollBarOffset : new AtomicReference<>(0);
        horizontalScrollBarOffset = horizontalScrollBarOffset != null ? horizontalScrollBarOffset : new AtomicReference<>(0);
        this.scrollBarAccentColor = scrollBarAccentColor;
        this.frameOrigin = new Dim2i(frame.dim.x(), frame.dim.y(), 0, 0);
        this.contentDimension = frame.dim;
        this.setupFrame(verticalScrollBarOffset, horizontalScrollBarOffset);
        this.buildFrame();
    }

    // Creates a builder.
    public static Builder createBuilder() {
        return new Builder();
    }

    // Creates scroll bars for the axes where the content overflows and starts from the original inner bounds.
    public void setupFrame(AtomicReference<Integer> verticalScrollBarOffset, AtomicReference<Integer> horizontalScrollBarOffset) {
        this.frame.dim = this.contentDimension;
        this.canScrollHorizontal = false;
        this.canScrollVertical = false;
        this.viewPortDimension = null;
        this.horizontalScrollBar = null;
        this.verticalScrollBar = null;
        // Enables an axis when the content passes the viewport
        if (!this.dim.canFitDimension(this.frame.dim)) {
            this.canScrollHorizontal = this.dim.getLimitX() < this.frame.dim.getLimitX()
                    && this.frame.dim.x() - this.dim.x() + this.frame.dim.width() > 0;
            this.canScrollVertical = this.dim.getLimitY() < this.frame.dim.getLimitY()
                    && this.frame.dim.y() - this.dim.y() + this.frame.dim.height() > 0;
        }

        if (this.canScrollHorizontal && this.canScrollVertical) {
            this.viewPortDimension = new Dim2i(this.dim.x(), this.dim.y(), this.dim.width() - 11, this.dim.height() - 11);
        } else if (this.canScrollHorizontal) {
            this.viewPortDimension = new Dim2i(this.dim.x(), this.dim.y(), this.dim.width(), this.dim.height() - 11);
            // Shortens the inner frame by the bar height
            this.frame.dim = this.frame.dim.withHeight(this.frame.dim.height() - 11);
        } else if (this.canScrollVertical) {
            this.viewPortDimension = new Dim2i(this.dim.x(), this.dim.y(), this.dim.width() - 11, this.dim.height());
            // Narrows the inner frame by the bar width
            this.frame.dim = this.frame.dim.withWidth(this.frame.dim.width() - 11);
        }

        if (this.canScrollHorizontal) {
            this.horizontalScrollBar = new ScrollBarComponent(new Dim2i(this.viewPortDimension.x(), this.viewPortDimension.getLimitY() + 1, this.viewPortDimension.width(), 10), ScrollBarComponent.Mode.HORIZONTAL, this.frame.dim.width(), this.viewPortDimension.width(), offset -> {
                horizontalScrollBarOffset.set(offset);
            }, this.scrollBarAccentColor);
            this.horizontalScrollBar.setOffset(Objects.requireNonNullElse(horizontalScrollBarOffset.get(), 0));
        }
        if (this.canScrollVertical) {
            this.verticalScrollBar = new ScrollBarComponent(new Dim2i(this.viewPortDimension.getLimitX() + 1, this.viewPortDimension.y(), 10, this.viewPortDimension.height()), ScrollBarComponent.Mode.VERTICAL, this.frame.dim.height(), this.viewPortDimension.height(), offset -> {
                verticalScrollBarOffset.set(offset);
            }, this.scrollBarAccentColor, this.viewPortDimension);
            this.verticalScrollBar.setOffset(Objects.requireNonNullElse(verticalScrollBarOffset.get(), 0));
        }
    }

    // Rebuilds the inner frame and the scroll bar thumbs.
    @Override
    public void buildFrame() {
        this.children.clear();
        this.drawable.clear();
        this.controlElements.clear();

        if (this.canScrollHorizontal) {
            this.horizontalScrollBar.updateThumbPosition();
        }

        if (this.canScrollVertical) {
            this.verticalScrollBar.updateThumbPosition();
        }

        this.frame.buildFrame();
        this.children.add(this.frame);
        super.buildFrame();
    }

    // Returns a coordinate shifted by the offset of a bar or unchanged without a bar.
    private double applyOffset(ScrollBarComponent component, double coord, boolean negate) {
        if (component == null) {
            return coord;
        }
        int offset = component.getOffset();
        return negate ? coord - offset : coord + offset;
    }


    // Draws the inner frame inside the viewport and then the bars.
    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float delta) {
        if (this.canScrollHorizontal || this.canScrollVertical) {
            if (this.renderOutline) {
                drawContext.drawBorder(this.dim.x(), this.dim.y(), this.dim.getLimitX(), this.dim.getLimitY(), 0xFFAAAAAA);
            }
            boolean mouseInViewport = this.viewPortDimension.containsCursor(mouseX, mouseY);
            drawContext.enableScissor(this.viewPortDimension.x(), this.viewPortDimension.y(), this.viewPortDimension.getLimitX(), this.viewPortDimension.getLimitY());
            drawContext.pushMatrix();
            // Pops the transform and the scissor on every path
            try {
                drawContext.translate(applyOffset(this.horizontalScrollBar, 0, true), applyOffset(this.verticalScrollBar, 0, true), 0);
                super.render(drawContext, mouseInViewport ? (int)applyOffset(this.horizontalScrollBar, mouseX, false) : -1, mouseInViewport ? (int)applyOffset(this.verticalScrollBar, mouseY, false) : -1, delta);
            } finally {
                drawContext.popMatrix();
                drawContext.disableScissor();
            }
        } else {
            super.render(drawContext, mouseX, mouseY, delta);
        }

        if (this.canScrollHorizontal) {
            this.horizontalScrollBar.render(drawContext, mouseX, mouseY, delta);
        }

        if (this.canScrollVertical) {
            this.verticalScrollBar.render(drawContext, mouseX, mouseY, delta);
        }
    }

    // Forwards a click to the bars and then the inner frame.
    @Override
    public boolean mouseClicked(InteractionContext context, double mouseX, double mouseY, int button) {
        return (this.canScrollHorizontal && this.horizontalScrollBar.mouseClicked(context, mouseX, mouseY, button)) ||
                (this.canScrollVertical && this.verticalScrollBar.mouseClicked(context, mouseX, mouseY, button)) ||
                super.mouseClicked(context, applyOffset(this.horizontalScrollBar, mouseX, false), applyOffset(this.verticalScrollBar, mouseY, false), button);
    }

    // Forwards a drag to the bars and then the inner frame.
    @Override
    public boolean mouseDragged(InteractionContext context, double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return (this.canScrollHorizontal && this.horizontalScrollBar.mouseDragged(context, mouseX, mouseY, button, deltaX, deltaY)) ||
                (this.canScrollVertical && this.verticalScrollBar.mouseDragged(context, mouseX, mouseY, button, deltaX, deltaY)) ||
                super.mouseDragged(context, applyOffset(this.horizontalScrollBar, mouseX, false), applyOffset(this.verticalScrollBar, mouseY, false), button, deltaX, deltaY);
    }

    // Forwards a release to the bars and then the inner frame.
    @Override
    public boolean mouseReleased(InteractionContext context, double mouseX, double mouseY, int button) {
        return (this.canScrollHorizontal && this.horizontalScrollBar.mouseReleased(context, mouseX, mouseY, button))
                || (this.canScrollVertical && this.verticalScrollBar.mouseReleased(context, mouseX, mouseY, button))
                || super.mouseReleased(context, applyOffset(this.horizontalScrollBar, mouseX, false), applyOffset(this.verticalScrollBar, mouseY, false), button);
    }

    // Forwards a scroll to the bars and then the inner frame.
    @Override
    public boolean mouseScrolled(InteractionContext context, double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return (this.canScrollHorizontal && this.horizontalScrollBar.mouseScrolled(context, mouseX, mouseY, horizontalAmount, verticalAmount))
                || (this.canScrollVertical && this.verticalScrollBar.mouseScrolled(context, mouseX, mouseY, horizontalAmount, verticalAmount))
                || super.mouseScrolled(context, applyOffset(this.horizontalScrollBar, mouseX, false), applyOffset(this.verticalScrollBar, mouseY, false), horizontalAmount, verticalAmount);
    }

    // Returns whether the cursor is inside the frame.
    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return this.dim.containsCursor(mouseX, mouseY);
    }

    // Moves focus and scrolls the focused control into view.
    @Override
    public boolean moveFocus(boolean forward) {
        if (!super.moveFocus(forward)) {
            return false;
        }
        // Skips a frame without a viewport
        var focused = this.getFocusedControl();
        if (this.viewPortDimension != null && focused != null) {
            Dim2i bounds = focused.getDimensions();
            reveal(this.horizontalScrollBar, bounds.x(), bounds.getLimitX(), this.viewPortDimension.x(), this.viewPortDimension.getLimitX());
            reveal(this.verticalScrollBar, bounds.y(), bounds.getLimitY(), this.viewPortDimension.y(), this.viewPortDimension.getLimitY());
        }
        return true;
    }

    // Scrolls a bar by the least amount that shows a span.
    private static void reveal(ScrollBarComponent bar, int start, int end, int viewStart, int viewEnd) {
        if (bar == null) {
            return;
        }
        int offset = bar.getOffset();
        int target = Math.min(Math.max(offset, end - viewEnd), start - viewStart);
        if (target != offset) {
            bar.setOffset(target);
        }
    }

    // Collects the settings of a scrollable frame.
    public static class Builder {
        // Outline state of the frame.
        private boolean renderOutline = false;
        // Bounds of the viewport.
        private Dim2i dim = null;
        // Inner frame.
        private AbstractFrame frame = null;
        // Vertical offset reference.
        private AtomicReference<Integer> verticalScrollBarOffset = new AtomicReference<>(0);
        // Horizontal offset reference.
        private AtomicReference<Integer> horizontalScrollBarOffset = new AtomicReference<>(0);
        // Color of the bar thumbs.
        private int scrollBarAccentColor = DefaultColors.ELEMENT_ACTIVATED;

        // Stores the viewport bounds.
        public Builder setDimension(Dim2i dim) {
            this.dim = dim;
            return this;
        }

        // Stores whether the frame draws an outline.
        public Builder shouldRenderOutline(boolean state) {
            this.renderOutline = state;
            return this;
        }

        // Stores the vertical offset reference.
        public Builder setVerticalScrollBarOffset(AtomicReference<Integer> verticalScrollBarOffset) {
            this.verticalScrollBarOffset = verticalScrollBarOffset;
            return this;
        }

        // Stores the horizontal offset reference.
        public Builder setHorizontalScrollBarOffset(AtomicReference<Integer> horizontalScrollBarOffset) {
            this.horizontalScrollBarOffset = horizontalScrollBarOffset;
            return this;
        }

        // Stores the inner frame.
        public Builder setFrame(AbstractFrame frame) {
            this.frame = frame;
            return this;
        }

        // Stores the thumb color.
        public Builder setScrollBarAccentColor(int scrollBarAccentColor) {
            this.scrollBarAccentColor = scrollBarAccentColor;
            return this;
        }

        // Creates the frame.
        public ScrollableFrame build() {
            return new ScrollableFrame(this.dim, this.frame, this.renderOutline, this.verticalScrollBarOffset, this.horizontalScrollBarOffset, this.scrollBarAccentColor);
        }
    }
}
