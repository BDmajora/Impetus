package com.bdmajora.impetus.engine.impl.gui.frame.components;

import com.bdmajora.impetus.engine.api.util.ColorARGB;
import com.bdmajora.impetus.engine.impl.common.util.MathUtil;
import com.bdmajora.impetus.engine.impl.gui.framework.DrawContext;
import com.bdmajora.impetus.engine.impl.gui.framework.InteractionContext;
import com.bdmajora.impetus.engine.impl.gui.theme.DefaultColors;
import com.bdmajora.impetus.engine.impl.gui.widgets.AbstractWidget;
import com.bdmajora.impetus.engine.impl.util.Dim2i;

import java.util.function.Consumer;

public class ScrollBarComponent extends AbstractWidget {
    protected static final int SCROLL_OFFSET = 18;

    protected final Dim2i dim;

    private final Mode mode;
    private final int frameLength;
    private final int viewPortLength;
    private final int maxScrollBarOffset;
    private final Consumer<Integer> onSetOffset;
    private final int accentColor;
    private int offset = 0;
    private boolean isDragging;

    private Dim2i scrollThumb = null;
    private int scrollThumbClickOffset;

    private Dim2i extendedScrollArea = null;

    public ScrollBarComponent(Dim2i trackArea, Mode mode, int frameLength, int viewPortLength, Consumer<Integer> onSetOffset) {
        this(trackArea, mode, frameLength, viewPortLength, onSetOffset, DefaultColors.ELEMENT_ACTIVATED);
    }

    public ScrollBarComponent(Dim2i trackArea, Mode mode, int frameLength, int viewPortLength, Consumer<Integer> onSetOffset, int accentColor) {
        this.dim = trackArea;
        this.mode = mode;
        this.frameLength = frameLength;
        this.viewPortLength = viewPortLength;
        this.onSetOffset = onSetOffset;
        this.maxScrollBarOffset = this.frameLength - this.viewPortLength;
        this.accentColor = accentColor;
    }

    public ScrollBarComponent(Dim2i scrollBarArea, Mode mode, int frameLength, int viewPortLength, Consumer<Integer> onSetOffset, Dim2i extendedTrackArea) {
        this(scrollBarArea, mode, frameLength, viewPortLength, onSetOffset, DefaultColors.ELEMENT_ACTIVATED, extendedTrackArea);
    }

    public ScrollBarComponent(Dim2i scrollBarArea, Mode mode, int frameLength, int viewPortLength, Consumer<Integer> onSetOffset, int accentColor, Dim2i extendedTrackArea) {
        this(scrollBarArea, mode, frameLength, viewPortLength, onSetOffset, accentColor);
        this.extendedScrollArea = extendedTrackArea;
    }

    // Recomputes the thumb rect from the offset and content size
    public void updateThumbPosition() {
        int scrollThumbLength = (this.viewPortLength * (this.mode == Mode.VERTICAL ? this.dim.height() : this.dim.width() - 6)) / this.frameLength;
        int maximumScrollThumbOffset = this.viewPortLength - scrollThumbLength;
        int scrollThumbOffset = this.offset * maximumScrollThumbOffset / this.maxScrollBarOffset;
        this.scrollThumb = new Dim2i(this.dim.x() + 2 + (this.mode == Mode.HORIZONTAL ? scrollThumbOffset : 0), this.dim.y() + 2 + (this.mode == Mode.VERTICAL ? scrollThumbOffset : 0), (this.mode == Mode.VERTICAL ? this.dim.width() : scrollThumbLength) - 4, (this.mode == Mode.VERTICAL ? scrollThumbLength : this.dim.height()) - 4);
    }

    // Track and thumb
    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float delta) {
        boolean hovered = this.dim.containsCursor(mouseX, mouseY);
        int trackColor = hovered || this.isDragging ? 0x50000000 : 0x26000000;
        int thumbColor = ColorARGB.withAlpha(this.accentColor, hovered || this.isDragging ? 0xFF : 0xB8);

        drawContext.fill(this.dim.x(), this.dim.y(), this.dim.getLimitX(), this.dim.getLimitY(), trackColor);
        drawContext.fill(this.scrollThumb.x(), this.scrollThumb.y(), this.scrollThumb.getLimitX(), this.scrollThumb.getLimitY(), thumbColor);
    }

    // The cursor coordinate, track origin and lengths along the scrolling axis, so the click and drag maths is written once for both orientations
    private double along(double mouseX, double mouseY) {
        return this.mode == Mode.VERTICAL ? mouseY : mouseX;
    }

    private int trackStart() {
        return this.mode == Mode.VERTICAL ? this.dim.y() : this.dim.x();
    }

    private int trackLength() {
        return this.mode == Mode.VERTICAL ? this.dim.height() : this.dim.width();
    }

    private int thumbStart() {
        return this.mode == Mode.VERTICAL ? this.scrollThumb.y() : this.scrollThumb.x();
    }

    private int thumbLength() {
        return this.mode == Mode.VERTICAL ? this.scrollThumb.height() : this.scrollThumb.width();
    }

    // Content offset for a cursor position along the track, the thumb centred on it
    private int offsetForCursor(double along) {
        int thumbLength = this.thumbLength();
        return (int) ((along - this.trackStart() - (thumbLength / 2)) / (this.trackLength() - thumbLength) * this.maxScrollBarOffset);
    }

    // Starts a drag on the thumb, or jumps on the track
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

    // Ends a drag
    @Override
    public boolean mouseReleased(InteractionContext context, double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.isDragging = false;
        }
        return false;
    }

    // Moves the thumb with the cursor
    @Override
    public boolean mouseDragged(InteractionContext context, double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (this.isDragging) {
            this.setOffset(this.offsetForCursor(this.along(mouseX, mouseY) - this.scrollThumbClickOffset));
            return true;
        }
        return false;
    }

    // Wheel scroll by a fixed step
    @Override
    public boolean mouseScrolled(InteractionContext context, double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.dim.containsCursor(mouseX, mouseY) || this.extendedScrollArea != null && this.extendedScrollArea.containsCursor(mouseX, mouseY)) {
            if (this.offset <= this.maxScrollBarOffset && this.offset >= 0) {
                int value = (int) (this.offset - verticalAmount * SCROLL_OFFSET); // todo: horizontal separation
                this.setOffset(value);
                return true;
            }
        }
        return false;
    }

    // Scroll position in content pixels
    public int getOffset() {
        return this.offset;
    }

    // Clamped to the scrollable range
    public void setOffset(int value) {
        this.offset = MathUtil.clamp(value, 0, this.maxScrollBarOffset);
        this.updateThumbPosition();
        this.onSetOffset.accept(this.offset);
    }

    // Over the track
    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return this.dim.containsCursor(mouseX, mouseY) || this.extendedScrollArea != null && this.extendedScrollArea.containsCursor(mouseX, mouseY);
    }

    public enum Mode {
        HORIZONTAL,
        VERTICAL
    }
}
