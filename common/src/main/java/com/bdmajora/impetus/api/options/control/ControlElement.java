package com.bdmajora.impetus.api.options.control;

import com.bdmajora.impetus.api.options.structure.Option;
import com.bdmajora.impetus.engine.impl.gui.framework.DrawContext;
import com.bdmajora.impetus.engine.impl.gui.framework.Focusable;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.engine.impl.gui.framework.TextFormattingStyle;
import com.bdmajora.impetus.engine.impl.gui.theme.DefaultColors;
import com.bdmajora.impetus.engine.impl.gui.widgets.AbstractWidget;
import com.bdmajora.impetus.engine.impl.gui.widgets.FlatButtonWidget;
import com.bdmajora.impetus.engine.impl.util.Dim2i;

import org.jetbrains.annotations.NotNull;

public class ControlElement<T> extends AbstractWidget implements OptionControlElement<T>, Focusable {
    // Appended to a name cut to fit
    private static final String ELLIPSIS = "...";
    // Appended to the name of an option with a pending change
    private static final String CHANGED_MARKER = " *";

    protected final Option<T> option;

    protected final Dim2i dim;

    private @NotNull FlatButtonWidget.Style style = FlatButtonWidget.Style.defaults();

    private boolean focused;

    // Width of the control's column, measured on the first frame and kept since an element's bounds and control never change; -1 until then
    private int columnWidth = -1;

    public ControlElement(Option<T> option, Dim2i dim) {
        this.option = option;
        this.dim = dim;
    }

    // Background, hover highlight and the option name; focus looks hovered and adds an outline in the mod's accent
    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float delta) {
        boolean hovered = this.focused || this.isMouseOver(mouseX, mouseY);
        boolean available = this.isOptionAvailable();
        boolean changed = available && this.option.hasChanged();
        int column = this.columnWidth;
        if (column < 0) {
            // Never negative, so a bad override is measured once like any other
            column = this.columnWidth = Math.max(0, this.measureColumnWidth(drawContext));
        }
        // The name starts 6 in and stops 6 short of the control's column; a negative width counts as none, so the subtraction cannot wrap
        int availableLabelWidth = Math.max(0, Math.max(0, this.dim.width()) - column - 12);
        String name = fitName(drawContext, drawContext.extractString(this.getDisplayName()), changed ? CHANGED_MARKER : "", availableLabelWidth);

        drawContext.fill(this.dim.x(), this.dim.y(), this.dim.getLimitX(), this.dim.getLimitY(), hovered ? style.bgHovered : style.bgDefault);
        if (this.focused) {
            drawContext.drawBorder(this.dim.x(), this.dim.y(), this.dim.getLimitX(), this.dim.getLimitY(), this.getAccentColor(drawContext));
        }
        if (name.isEmpty()) {
            return;
        }

        TextComponent label;
        if (!available) {
            label = this.formatDisabledControlValue(TextComponent.literal(name));
        } else if (changed) {
            label = TextComponent.literal(name).withStyle(TextFormattingStyle.ITALIC);
        } else {
            label = TextComponent.literal(name).withStyle(TextFormattingStyle.WHITE);
        }
        drawContext.drawString(label, this.dim.x() + 6, this.dim.getCenterY() - 4, style.textDefault);
    }

    // The name and marker whole when they fit, else the name cut with an ellipsis before the marker, else nothing,
    // so neither the ellipsis nor the marker ever runs into the control's column
    private static String fitName(DrawContext drawContext, String name, String marker, int room) {
        String whole = name + marker;
        if (drawContext.getStringWidth(whole) <= room) {
            return whole;
        }
        int nameRoom = room - drawContext.getStringWidth(ELLIPSIS + marker);
        if (nameRoom < 0) {
            return "";
        }
        // Drops the spaces the cut ends on, so the ellipsis follows the last word
        return drawContext.substrByWidth(name, nameRoom).stripTrailing() + ELLIPSIS + marker;
    }

    // The control's column held to the row, measured once per element
    protected int measureColumnWidth(DrawContext drawContext) {
        return Control.columnWidth(this.option.getControl(), drawContext, this.dim.width());
    }

    // Whether the option can be edited, read every frame so a master switch greys its children live
    protected boolean isOptionAvailable() {
        return this.option.isAvailable();
    }

    // The option's name, read every frame so a renamed option shows its new name
    protected TextComponent getDisplayName() {
        return this.option.getName();
    }

    // Bound option
    public Option<T> getOption() {
        return this.option;
    }

    // Bounds
    public Dim2i getDimensions() {
        return this.dim;
    }

    // Owning mod's colour
    protected int getAccentColor(DrawContext drawContext) {
        var id = this.option.getId();
        return drawContext.getModAccentColor(id != null ? id.getModId() : null);
    }

    // Greyed text colour
    protected int getDisabledControlColor() {
        return DefaultColors.TEXT_DISABLED;
    }

    // Strikes through or greys a value when unavailable
    protected TextComponent formatDisabledControlValue(TextComponent value) {
        return value.withStyle(TextFormattingStyle.GRAY, TextFormattingStyle.STRIKETHROUGH);
    }

    // Bounds test
    @Override
    public boolean isMouseOver(double x, double y) {
        return this.dim.containsCursor(x, y);
    }

    // Tab passes over the rows of an unavailable option, as clicks do
    @Override
    public boolean canFocus() {
        return this.isOptionAvailable();
    }

    // Whether this row's keys go here
    @Override
    public boolean isFocused() {
        return this.focused;
    }

    // Set by the container when Tab or a click moves focus
    @Override
    public void setFocused(boolean focused) {
        this.focused = focused;
    }
}
