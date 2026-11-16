package com.bdmajora.impetus.engine.impl.gui.frame.components;

import com.bdmajora.impetus.engine.api.util.ColorARGB;
import com.bdmajora.impetus.engine.impl.common.util.MathUtil;
import com.bdmajora.impetus.engine.impl.gui.framework.DrawContext;
import com.bdmajora.impetus.engine.impl.gui.framework.InteractionContext;
import com.bdmajora.impetus.engine.impl.gui.theme.DefaultColors;
import com.bdmajora.impetus.engine.impl.gui.widgets.AbstractWidget;
import com.bdmajora.impetus.engine.impl.util.Dim2i;

import java.util.Objects;
import java.util.function.Consumer;

// Scroll bar with a track and a draggable thumb.
public class ScrollBarComponent extends AbstractWidget {
    // Pixels scrolled by one wheel step.
    protected static final int SCROLL_OFFSET = 18;

    // Bounds of the track.
    protected final Dim2i dim;

    // Direction of the bar.
    private final Mode mode;
    // Length of the content.
    private final int frameLength;
    // Length of the visible area.
    private final int viewPortLength;
    // Largest scroll offset.
    private final int maxScrollBarOffset;
    // Receiver of every new offset.
    private final Consumer<Integer> onSetOffset;
    // Color of the thumb.
    private final int accentColor;
    // Current scroll offset.
    private int offset = 0;
    // Drag state of the thumb.
    private boolean isDragging;

    // Bounds of the thumb.
    private Dim2i scrollThumb = null;
    // Distance of the cursor from the thumb center at the start of a drag.
    private int scrollThumbClickOffset;

    // Extra area that reacts to the wheel.
    private Dim2i extendedScrollArea = null;

    // Creates a bar with the default color.
    public ScrollBarComponent(Dim2i trackArea, Mode mode, int frameLength, int viewPortLength, Consumer<Integer> onSetOffset) {
        this(trackArea, mode, frameLength, viewPortLength, onSetOffset, DefaultColors.ELEMENT_ACTIVATED);
    }

    // Creates a bar and rejects a null track or mode or receiver.
    public ScrollBarComponent(Dim2i trackArea, Mode mode, int frameLength, int viewPortLength, Consumer<Integer> onSetOffset, int accentColor) {
        this.dim = Objects.requireNonNull(trackArea, "trackArea");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.frameLength = frameLength;
        // Reads a negative viewport as an empty one
        this.viewPortLength = Math.max(0, viewPortLength);
        this.onSetOffset = Objects.requireNonNull(onSetOffset, "onSetOffset");
        this.maxScrollBarOffset = Math.max(0, (int) Math.min(Integer.MAX_VALUE, (long) frameLength - this.viewPortLength));
        this.accentColor = accentColor;
        this.updateThumbPosition();
    }

    // Creates a bar with the default color and an extra wheel area.
    public ScrollBarComponent(Dim2i scrollBarArea, Mode mode, int frameLength, int viewPortLength, Consumer<Integer> onSetOffset, Dim2i extendedTrackArea) {
        this(scrollBarArea, mode, frameLength, viewPortLength, onSetOffset, DefaultColors.ELEMENT_ACTIVATED, extendedTrackArea);
    }

    // Creates a bar with an extra wheel area that accepts a null area.
    public ScrollBarComponent(Dim2i scrollBarArea, Mode mode, int frameLength, int viewPortLength, Consumer<Integer> onSetOffset, int accentColor, Dim2i extendedTrackArea) {
        this(scrollBarArea, mode, frameLength, viewPortLength, onSetOffset, accentColor);
        this.extendedScrollArea = extendedTrackArea;
    }

    // Recalculates the thumb bounds from the offset and the content size.
    public void updateThumbPosition() {
        long track = this.mode == Mode.VERTICAL ? this.dim.height() : this.dim.width() - 6;
        int scrollThumbLength = this.frameLength <= 0 || this.frameLength <= this.viewPortLength ? (int) track : (int) Math.min(track, this.viewPortLength * track / this.frameLength);
        int maximumScrollThumbOffset = this.viewPortLength - scrollThumbLength;
        int scrollThumbOffset = this.maxScrollBarOffset <= 0 ? 0 : (int) ((long) this.offset * maximumScrollThumbOffset / this.maxScrollBarOffset);
        this.scrollThumb = new Dim2i(this.dim.x() + 2 + (this.mode == Mode.HORIZONTAL ? scrollThumbOffset : 0), this.dim.y() + 2 + (this.mode == Mode.VERTICAL ? scrollThumbOffset : 0), (this.mode == Mode.VERTICAL ? this.dim.width() : scrollThumbLength) - 4, (this.mode == Mode.VERTICAL ? scrollThumbLength : this.dim.height()) - 4);
    }

    // Draws the track and the thumb.
    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float delta) {
        boolean hovered = this.dim.containsCursor(mouseX, mouseY);
        int trackColor = hovered || this.isDragging ? 0x50000000 : 0x26000000;
        int thumbColor = ColorARGB.withAlpha(this.accentColor, hovered || this.isDragging ? 0xFF : 0xB8);

        drawContext.fill(this.dim.x(), this.dim.y(), this.dim.getLimitX(), this.dim.getLimitY(), trackColor);
        drawContext.fill(this.scrollThumb.x(), this.scrollThumb.y(), this.scrollThumb.getLimitX(), this.scrollThumb.getLimitY(), thumbColor);
    }

    // Returns the cursor coordinate along the scrolling axis.
    private double along(double mouseX, double mouseY) {
        return this.mode == Mode.VERTICAL ? mouseY : mouseX;
    }

    // Returns the track start along the scrolling axis.
    private int trackStart() {
        return this.mode == Mode.VERTICAL ? this.dim.y() : this.dim.x();
    }

    // Returns the track length along the scrolling axis.
    private int trackLength() {
        return this.mode == Mode.VERTICAL ? this.dim.height() : this.dim.width();
    }

    // Returns the thumb start along the scrolling axis.
    private int thumbStart() {
        return this.mode == Mode.VERTICAL ? this.scrollThumb.y() : this.scrollThumb.x();
    }

    // Returns the thumb length along the scrolling axis.
    private int thumbLength() {
        return this.mode == Mode.VERTICAL ? this.scrollThumb.height() : this.scrollThumb.width();
    }

    // Returns the content offset for a cursor position with the thumb centered on it.
    private int offsetForCursor(double along) {
        int thumbLength = this.thumbLength();
        if (this.trackLength() <= thumbLength) {
            return 0;
        }
        return (int) ((along - this.trackStart() - (thumbLength / 2)) / (this.trackLength() - thumbLength) * this.maxScrollBarOffset);
    }

    // Starts a drag on the thumb or jumps to the clicked track position.
    @Override
    public boolean mouseClicked(InteractionContext context, double mouseX, double mouseY, int button) {
        if (this.dim.containsCursor(mouseX, mouseY)) {
            if (this.scrollThumb.containsCursor(mouseX, mouseY)) {
                this.scrollThumbClickOffset = (int) (this.along(mouseX, mouseY) - (this.thumbStart() + this.thumbLength() / 2));
                this.isDragging = true;
            } else {
                this.setOffset(this.offsetForCursor(this.along(mouseX, mouseY)));
                this.isDragging = false;
            }
            return true;
        }
        this.isDragging = false;
        return false;
    }

    // Ends a drag on a left button release.
    @Override
    public boolean mouseReleased(InteractionContext context, double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.isDragging = false;
        }
        return false;
    }

    // Moves the thumb with the cursor while dragging.
    @Override
    public boolean mouseDragged(InteractionContext context, double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (this.isDragging) {
            this.setOffset(this.offsetForCursor(this.along(mouseX, mouseY) - this.scrollThumbClickOffset));
            return true;
        }
        return false;
    }

    // Scrolls by a fixed step when the cursor is over the bar or its extra area.
    @Override
    public boolean mouseScrolled(InteractionContext context, double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.dim.containsCursor(mouseX, mouseY) || this.extendedScrollArea != null && this.extendedScrollArea.containsCursor(mouseX, mouseY)) {
            this.setOffset((int) (this.offset - verticalAmount * SCROLL_OFFSET));
            return true;
        }
        return false;
    }

    // Returns the scroll offset in content pixels.
    public int getOffset() {
        return this.offset;
    }

    // Stores the offset limited to the scrollable range and reports it.
    public void setOffset(int value) {
        this.offset = MathUtil.clamp(value, 0, this.maxScrollBarOffset);
        this.updateThumbPosition();
        this.onSetOffset.accept(this.offset);
    }

    // Returns whether the cursor is over the track or the extra area.
    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return this.dim.containsCursor(mouseX, mouseY) || this.extendedScrollArea != null && this.extendedScrollArea.containsCursor(mouseX, mouseY);
    }

    // Directions of a scroll bar.
    public enum Mode {
        HORIZONTAL,
        VERTICAL
    }
}
