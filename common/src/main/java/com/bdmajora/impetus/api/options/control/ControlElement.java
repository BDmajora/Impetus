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
    protected final Option<T> option;

    protected final Dim2i dim;

    private @NotNull FlatButtonWidget.Style style = FlatButtonWidget.Style.defaults();

    private boolean focused;

    public ControlElement(Option<T> option, Dim2i dim) {
        this.option = option;
        this.dim = dim;
    }

    // Background, hover highlight and the option name; focus looks hovered and adds an outline in the mod's accent
    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float delta) {
        String name = drawContext.extractString(this.option.getName());
        TextComponent label;

        boolean hovered = this.focused || this.isMouseOver(mouseX, mouseY);
        int availableLabelWidth = Math.max(0, this.dim.width() - this.option.getControl().getMaxWidth() - 12);

        if (drawContext.getStringWidth(name) > availableLabelWidth) {
            int ellipsisWidth = drawContext.getStringWidth("...");
            name = drawContext.substrByWidth(name, Math.max(0, availableLabelWidth - ellipsisWidth)) + "...";
        }

        if (this.option.isAvailable()) {
            if (this.option.hasChanged()) {
                label = TextComponent.literal(name + " *").withStyle(TextFormattingStyle.ITALIC);
            } else {
                label = TextComponent.literal(name).withStyle(TextFormattingStyle.WHITE);
            }
        } else {
            label = this.formatDisabledControlValue(TextComponent.literal(name));
        }

        drawContext.fill(this.dim.x(), this.dim.y(), this.dim.getLimitX(), this.dim.getLimitY(), hovered ? style.bgHovered : style.bgDefault);
        if (this.focused) {
            drawContext.drawBorder(this.dim.x(), this.dim.y(), this.dim.getLimitX(), this.dim.getLimitY(), this.getAccentColor(drawContext));
        }
        drawContext.drawString(label, this.dim.x() + 6, this.dim.getCenterY() - 4, style.textDefault);
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
        return this.option.isAvailable();
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
