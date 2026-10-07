package com.bdmajora.impetus.engine.impl.gui.widgets;

import com.bdmajora.impetus.engine.impl.gui.framework.DrawContext;
import com.bdmajora.impetus.engine.impl.gui.framework.InteractionContext;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.engine.impl.gui.theme.DefaultColors;
import com.bdmajora.impetus.engine.impl.util.Dim2i;

import java.util.function.Consumer;

// Text-input field for filtering the options list; matches key input on typedChar first, vanilla's key code as fallback
public class SearchBarWidget extends AbstractWidget {
    private static final int TEXT_PADDING = 6;

    // Editing keys, by produced character and by the key code vanilla screens pass on (lwjglx keeps the LWJGL2 numbering)
    private static final char CHAR_BACKSPACE = '\b';
    private static final char CHAR_ESCAPE = 27;
    private static final int KEY_ESCAPE = 1;
    private static final int KEY_BACK = 14;

    private final Dim2i dim;
    private final StringBuilder query = new StringBuilder();
    private final Consumer<String> queryListener;
    private final TextComponent placeholder = TextComponent.translatable("impetus.search_bar_empty");
    private boolean focused;

    public SearchBarWidget(Dim2i dim, String initialQuery, boolean focused, Consumer<String> queryListener) {
        this.dim = dim;
        this.query.append(initialQuery);
        this.focused = focused;
        this.queryListener = queryListener;
    }

    // Box, placeholder or query, and a caret when focused
    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float delta) {
        boolean highlight = this.focused || this.dim.containsCursor(mouseX, mouseY);

        drawContext.fill(this.dim.x(), this.dim.y(), this.dim.getLimitX(), this.dim.getLimitY(), 0x90000000);
        drawContext.drawBorder(this.dim.x(), this.dim.y(), this.dim.getLimitX(), this.dim.getLimitY(),
                highlight ? DefaultColors.ELEMENT_ACTIVATED : 0xFF3F3F3F);

        int textX = this.dim.x() + TEXT_PADDING;
        int textY = this.dim.getCenterY() - (drawContext.lineHeight() / 2);

        if (this.query.length() == 0 && !this.focused) {
            drawContext.drawString(this.placeholder, textX, textY, 0xFF808080);
        } else {
            var text = this.query.toString();

            if (this.focused && (System.currentTimeMillis() / 500) % 2 == 0) {
                text += "_";
            }

            drawContext.drawString(text, textX, textY, 0xFFFFFFFF);
        }
    }

    // Bounds test
    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return this.dim.containsCursor(mouseX, mouseY);
    }

    // Focuses on click inside, blurs on click outside
    @Override
    public boolean mouseClicked(InteractionContext context, double mouseX, double mouseY, int button) {
        var inside = this.dim.containsCursor(mouseX, mouseY);
        this.focused = inside;
        return inside;
    }

    // Edits the query when focused; backspace, escape and printable characters
    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        if (!this.focused) {
            return false;
        }

        if (typedChar == CHAR_ESCAPE || keyCode == KEY_ESCAPE) {
            if (this.query.length() == 0) {
                this.focused = false;
            } else {
                this.query.setLength(0);
                this.fireQueryChanged();
            }
        } else if (typedChar == CHAR_BACKSPACE || keyCode == KEY_BACK) {
            if (this.query.length() > 0) {
                this.query.setLength(this.query.length() - 1);
                this.fireQueryChanged();
            }
        } else if (typedChar >= ' ' && typedChar != 127) {
            this.query.append(typedChar);
            this.fireQueryChanged();
        }

        // Swallow everything while focused so typing doesn't trigger game keybinds.
        return true;
    }

    // Notifies the listener
    private void fireQueryChanged() {
        this.queryListener.accept(this.query.toString());
    }

    // Whether keystrokes go here
    public boolean isFocused() {
        return this.focused;
    }

    // Current text
    public String getQuery() {
        return this.query.toString();
    }
}
