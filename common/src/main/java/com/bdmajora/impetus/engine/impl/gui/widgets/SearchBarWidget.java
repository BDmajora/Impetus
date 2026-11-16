package com.bdmajora.impetus.engine.impl.gui.widgets;

import com.bdmajora.impetus.engine.impl.gui.framework.DrawContext;
import com.bdmajora.impetus.engine.impl.gui.framework.Focusable;
import com.bdmajora.impetus.engine.impl.gui.framework.InteractionContext;
import com.bdmajora.impetus.engine.impl.gui.framework.Keys;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.engine.impl.gui.theme.DefaultColors;
import com.bdmajora.impetus.engine.impl.util.Dim2i;

import java.util.Objects;
import java.util.function.Consumer;

// Text field that filters the options list.
public class SearchBarWidget extends AbstractWidget implements Focusable {
    // Space between the box edge and the text.
    private static final int TEXT_PADDING = 6;

    // Character of the backspace key.
    private static final char CHAR_BACKSPACE = '\b';
    // Character of the escape key.
    private static final char CHAR_ESCAPE = 27;
    // Key code of escape.
    private static final int KEY_ESCAPE = 1;
    // Key code of backspace.
    private static final int KEY_BACK = 14;

    // Bounds of the field.
    private final Dim2i dim;
    // Text typed so far.
    private final StringBuilder query = new StringBuilder();
    // Receiver of every changed query.
    private final Consumer<String> queryListener;
    // Text shown while the query is empty.
    private final TextComponent placeholder = TextComponent.translatable("impetus.search_bar_empty");
    // Focus state of the field.
    private boolean focused;
    // Query text of the last edit.
    private String queryText = "";
    // Query text of the last edit followed by the caret.
    private String queryWithCaret = "_";

    // Creates a field and rejects null bounds or a null listener and reads a null query as empty.
    public SearchBarWidget(Dim2i dim, String initialQuery, boolean focused, Consumer<String> queryListener) {
        this.dim = Objects.requireNonNull(dim, "dim");
        if (initialQuery != null) {
            this.query.append(initialQuery);
        }
        this.refreshText();
        this.focused = focused;
        this.queryListener = Objects.requireNonNull(queryListener, "queryListener");
    }

    // Draws the box and the placeholder or query and a caret when focused.
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
            boolean caret = this.focused && (System.currentTimeMillis() / 500) % 2 == 0;
            drawContext.drawString(caret ? this.queryWithCaret : this.queryText, textX, textY, 0xFFFFFFFF);
        }
    }

    // Returns whether the cursor is over the field.
    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return this.dim.containsCursor(mouseX, mouseY);
    }

    // Focuses the field on a click inside and removes focus on a click outside.
    @Override
    public boolean mouseClicked(InteractionContext context, double mouseX, double mouseY, int button) {
        var inside = this.dim.containsCursor(mouseX, mouseY);
        this.focused = inside;
        return inside;
    }

    // Edits the query for printable characters and backspace and escape while focused.
    @Override
    public boolean keyTyped(InteractionContext context, char typedChar, int keyCode) {
        if (!this.focused || Keys.isTab(typedChar, keyCode)) {
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
            int length = this.query.length();
            if (length > 0) {
                // Removes a whole surrogate pair at the end
                boolean pair = length > 1 && Character.isLowSurrogate(this.query.charAt(length - 1))
                        && Character.isHighSurrogate(this.query.charAt(length - 2));
                this.query.setLength(length - (pair ? 2 : 1));
                this.fireQueryChanged();
            }
        } else if (typedChar >= ' ' && (typedChar < 127 || typedChar > 159)) {
            this.query.append(typedChar);
            this.fireQueryChanged();
        }

        // Consumes every key while focused
        return true;
    }

    // Refreshes the cached query text and sends it to the listener.
    private void fireQueryChanged() {
        this.refreshText();
        this.queryListener.accept(this.queryText);
    }

    // Stores the query text with and without the caret.
    private void refreshText() {
        this.queryText = this.query.toString();
        this.queryWithCaret = this.queryText + "_";
    }

    // Returns whether the field holds focus.
    @Override
    public boolean isFocused() {
        return this.focused;
    }

    // Stores whether the field holds focus.
    @Override
    public void setFocused(boolean focused) {
        this.focused = focused;
    }

    // Returns the bounds of the field.
    @Override
    public Dim2i getDimensions() {
        return this.dim;
    }

    // Returns the query.
    public String getQuery() {
        return this.queryText;
    }
}
