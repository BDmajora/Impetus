package com.bdmajora.impetus.api.options.control;

import com.bdmajora.impetus.api.config.theme.ConfigThemeRegistry;
import com.bdmajora.impetus.api.options.structure.Option;
import com.bdmajora.impetus.engine.impl.gui.framework.DrawContext;
import com.bdmajora.impetus.engine.impl.gui.framework.Focusable;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.engine.impl.gui.framework.TextFormattingStyle;
import com.bdmajora.impetus.engine.impl.gui.theme.DefaultColors;
import com.bdmajora.impetus.engine.impl.gui.widgets.AbstractWidget;
import com.bdmajora.impetus.engine.impl.util.Dim2i;

import java.util.Objects;

// Row on the options screen that draws an option's name and background and holds keyboard focus
public class ControlElement<T> extends AbstractWidget implements OptionControlElement<T>, Focusable {
    // Text appended to the name of an option with a pending change
    private static final String CHANGED_MARKER = " *";
    // Text appended to a shortened name without and with the pending change marker
    private static final String CUT_SUFFIX = "...";
    private static final String CUT_CHANGED_SUFFIX = CUT_SUFFIX + CHANGED_MARKER;
    // Gap between the row's left edge and the name
    private static final int NAME_LEFT_MARGIN = 6;
    // Gap between the end of the name and the control column
    private static final int COLUMN_GAP = 6;
    // Half the glyph height used to centre the name vertically on the row
    private static final int TEXT_RAISE = 4;
    // Colour the name is drawn in beneath its own style
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    // Line height used when the font source reports none
    private static final int DEFAULT_LINE_HEIGHT = 9;
    // Largest distance from the origin allowed for any edge of a drawn row
    private static final int COORDINATE_LIMIT = 1 << 30;

    // Label states that each use a different style
    private static final int STATE_NORMAL = 0;
    private static final int STATE_CHANGED = 1;
    private static final int STATE_DISABLED = 2;

    protected final Option<T> option;

    protected final Dim2i dim;

    private boolean focused;
    // Whether the last frame drew the row as hovered or focused
    private boolean hovered;

    // Whether the row has an area within the drawable coordinates
    private final boolean drawable;

    // Width left for the name after the control column or minus one before the first frame
    private int nameRoom = -1;

    // Name and font source and text version and state of the last fit with a state of minus one before the first fit
    private TextComponent labelName;
    private DrawContext labelContext;
    private long labelVersion;
    private int labelState = -1;
    // Counter that advances when the font source or its text version changes
    private long textGeneration;
    // Fitted and styled name or null when none of it fits
    private TextComponent label;
    // Width of the fitted name
    private int labelWidth;
    // Whether a line of text fits the row height in the last font source
    private boolean textFits;

    // Font source and theme generation of the last accent lookup and the accent found
    private DrawContext accentContext;
    private int accentGeneration;
    private int accent;

    // Creates a row for the option at the given bounds and rejects a null option or bounds
    public ControlElement(Option<T> option, Dim2i dim) {
        this.option = Objects.requireNonNull(option, "Option must not be null");
        this.dim = Objects.requireNonNull(dim, "Dimensions must not be null");
        this.drawable = isDrawable(dim);
    }

    // Returns true for bounds with positive width and height whose edges all lie within the coordinate limit
    private static boolean isDrawable(Dim2i dim) {
        int x = dim.x();
        int y = dim.y();
        int width = dim.width();
        int height = dim.height();
        return width > 0 && height > 0 && x >= -COORDINATE_LIMIT && y >= -COORDINATE_LIMIT && x <= COORDINATE_LIMIT - width && y <= COORDINATE_LIMIT - height;
    }

    // Draws the background and hover highlight and option name with an accent outline while focused
    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float delta) {
        // Draws nothing for a row with no area
        if (!this.drawable) {
            return;
        }
        // Stores whether the row is hovered or focused for this frame
        boolean hovered = this.hovered = this.focused || this.isMouseOver(mouseX, mouseY);
        boolean available = this.isOptionAvailable();
        int state = !available ? STATE_DISABLED : this.option.hasChanged() ? STATE_CHANGED : STATE_NORMAL;
        TextComponent name = this.getDisplayName();
        long version = drawContext.textVersion();
        boolean fontsChanged = drawContext != this.labelContext || version != this.labelVersion;
        // Fits the name again when its component or font source or text version or state has changed
        if (fontsChanged || state != this.labelState || !sameName(name, this.labelName)) {
            // Measures the column again when the same font source reports a new text version
            if (drawContext == this.labelContext && version != this.labelVersion) {
                this.nameRoom = -1;
            }
            TextComponent label = this.fitLabel(drawContext, name, state);
            int labelWidth = label != null ? drawContext.getStringWidth(label) : 0;
            // Drops a styled name that no longer fits
            if (label != null && !FittedText.fits(labelWidth, this.nameRoom)) {
                label = null;
                labelWidth = 0;
            }
            boolean textFits = fontsChanged ? this.fitsTextLine(drawContext) : this.textFits;
            // Stores the fitted label and the key it was fitted for
            this.label = label;
            this.labelWidth = labelWidth;
            this.textFits = textFits;
            this.labelName = name;
            this.labelContext = drawContext;
            this.labelVersion = version;
            this.labelState = state;
            if (fontsChanged) {
                this.textGeneration++;
            }
        }

        drawContext.fill(this.dim.x(), this.dim.y(), this.dim.getLimitX(), this.dim.getLimitY(), hovered ? DefaultColors.BACKGROUND_HOVERED : DefaultColors.BACKGROUND_DEFAULT);
        if (this.focused) {
            drawContext.drawBorder(this.dim.x(), this.dim.y(), this.dim.getLimitX(), this.dim.getLimitY(), this.getAccentColor(drawContext));
        }
        if (this.label != null && this.textFits) {
            drawContext.drawString(this.label, this.dim.x() + NAME_LEFT_MARGIN, this.dim.getCenterY() - TEXT_RAISE, TEXT_COLOR);
        }
    }

    // Returns true when the row has width and height within the drawable coordinates
    protected final boolean hasArea() {
        return this.drawable;
    }

    // Returns whether the current frame drew the row as hovered or focused
    protected final boolean isHovered() {
        return this.hovered;
    }

    // Returns the generation of the font source the name was last fitted in
    protected final long textGeneration() {
        return this.textGeneration;
    }

    // Returns true when a line of text fits the row height
    protected final boolean hasRoomForText() {
        return this.textFits;
    }

    // Returns whether a line of text raised above the row centre stays inside the row
    private boolean fitsTextLine(DrawContext drawContext) {
        int lineHeight = drawContext.lineHeight();
        long top = this.dim.y() + (long) (this.dim.height() / 2) - TEXT_RAISE;
        return top >= this.dim.y() && top + (lineHeight > 0 ? lineHeight : DEFAULT_LINE_HEIGHT) <= (long) this.dim.y() + this.dim.height();
    }

    // Returns whether the name was last drawn as available
    protected final boolean isDrawnAvailable() {
        return this.labelState != STATE_DISABLED;
    }

    // Returns the leftmost x for a control's own text after the drawn name and gap or the row's left edge when no name is drawn
    protected final int getNameLimitX() {
        return this.label != null && this.textFits ? this.dim.x() + NAME_LEFT_MARGIN + this.labelWidth + COLUMN_GAP : this.dim.x();
    }

    // Returns true for the same component or an equal one
    private static boolean sameName(TextComponent name, TextComponent last) {
        return name == last || name != null && name.equals(last);
    }

    // Returns the name fitted to the room and styled for the state or null when none of it fits
    private TextComponent fitLabel(DrawContext drawContext, TextComponent name, int state) {
        String text = fitName(drawContext, textOf(drawContext, name), state == STATE_CHANGED, this.nameRoom(drawContext));
        if (text.isEmpty()) {
            return null;
        }
        TextComponent literal = TextComponent.literal(text);
        return switch (state) {
            case STATE_DISABLED -> this.formatDisabledControlValue(literal);
            case STATE_CHANGED -> literal.withStyle(TextFormattingStyle.ITALIC);
            default -> literal.withStyle(TextFormattingStyle.WHITE);
        };
    }

    // Returns the width left for the name and measures the column on the first call
    private int nameRoom(DrawContext drawContext) {
        int room = this.nameRoom;
        if (room < 0) {
            int rowWidth = Math.max(0, this.dim.width());
            // Clamps the column between zero and the row width
            int column = Math.clamp(this.measureColumnWidth(drawContext), 0, rowWidth);
            room = this.nameRoom = Math.max(0, rowWidth - column - NAME_LEFT_MARGIN - COLUMN_GAP);
        }
        return room;
    }

    // Returns the name's text or an empty string when there is no name or no text for it
    private static String textOf(DrawContext drawContext, TextComponent name) {
        String text = name != null ? drawContext.extractString(name) : null;
        return text != null ? text : "";
    }

    // Returns the full name and marker when they fit or the shortened name with an ellipsis and marker or an empty string
    private static String fitName(DrawContext drawContext, String name, boolean changed, int room) {
        String whole = changed ? name + CHANGED_MARKER : name;
        if (FittedText.fits(drawContext.getStringWidth(whole), room)) {
            return whole;
        }
        // Keeps the marker after the ellipsis on a shortened name
        return FittedText.cut(drawContext, name, changed ? CUT_CHANGED_SUFFIX : CUT_SUFFIX, room);
    }

    // Returns the width of the control column limited to the row width
    protected int measureColumnWidth(DrawContext drawContext) {
        return Control.columnWidth(this.option.getControl(), drawContext, this.dim.width());
    }

    // Returns whether the option can currently be edited
    protected boolean isOptionAvailable() {
        return this.option.isAvailable();
    }

    // Returns the current name of the option
    protected TextComponent getDisplayName() {
        return this.option.getName();
    }

    // Returns the option this row belongs to
    @Override
    public Option<T> getOption() {
        return this.option;
    }

    // Returns the bounds of the row
    public Dim2i getDimensions() {
        return this.dim;
    }

    // Returns the accent colour of the mod that owns the option and looks it up again for a new font source or theme generation
    protected int getAccentColor(DrawContext drawContext) {
        // Reads the theme generation before the lookup
        int generation = ConfigThemeRegistry.generation();
        if (drawContext != this.accentContext || generation != this.accentGeneration) {
            var id = this.option.getId();
            this.accent = drawContext.getModAccentColor(id != null ? id.getModId() : null);
            this.accentGeneration = generation;
            this.accentContext = drawContext;
        }
        return this.accent;
    }

    // Returns the text colour used for unavailable controls
    protected int getDisabledControlColor() {
        return DefaultColors.TEXT_DISABLED;
    }

    // Returns the value greyed and struck through
    protected TextComponent formatDisabledControlValue(TextComponent value) {
        return value.withStyle(TextFormattingStyle.GRAY, TextFormattingStyle.STRIKETHROUGH);
    }

    // Returns true when the point is inside the row
    @Override
    public boolean isMouseOver(double x, double y) {
        return this.dim.containsCursor(x, y);
    }

    // Allows focus only while the row has an area and the option is available
    @Override
    public boolean canFocus() {
        return this.hasArea() && this.isOptionAvailable();
    }

    // Returns true while the row has keyboard focus
    @Override
    public boolean isFocused() {
        return this.focused;
    }

    // Sets whether the row has keyboard focus
    @Override
    public void setFocused(boolean focused) {
        this.focused = focused;
    }
}
