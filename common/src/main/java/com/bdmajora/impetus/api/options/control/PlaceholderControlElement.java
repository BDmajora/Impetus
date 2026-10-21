package com.bdmajora.impetus.api.options.control;

import com.bdmajora.impetus.api.options.structure.Option;
import com.bdmajora.impetus.engine.impl.gui.framework.DrawContext;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.engine.impl.notification.ImpetusNotifications;
import com.bdmajora.impetus.engine.impl.util.Dim2i;
import com.bdmajora.impetus.engine.impl.util.FailureText;
import com.bdmajora.impetus.engine.impl.util.collections.WeakIdentitySet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

// Row shown in place of an option whose control failed with the name greyed out and an error label in the control column
final class PlaceholderControlElement<T> extends ControlElement<T> {
    private static final Logger LOGGER = LogManager.getLogger();

    // Error label drawn in the control column
    private static final TextComponent LABEL = TextComponent.translatable("impetus.options.control_failed");
    // Red colour of the error label
    private static final int LABEL_COLOR = 0xFFFF5555;
    // Gap between the label and the right edge of the row
    private static final int LABEL_RIGHT_INSET = 6;
    // Half the glyph height used to centre the label vertically on the row
    private static final int TEXT_RAISE = 4;
    // Deepest name nesting copied before the description is used instead
    private static final int MAX_NAME_DEPTH = 256;
    // Options already reported as broken held weakly and compared by identity
    private static final WeakIdentitySet<Option<?>> BROKEN = new WeakIdentitySet<>();

    // Copy of the option name taken when the placeholder is created
    private final TextComponent name;
    // Width of the label and the text generation it was measured in
    private int labelWidth;
    private long labelGeneration;
    // Label width measured for the column during the current frame and whether it was measured
    private int columnLabelWidth;
    private boolean columnMeasured;

    // Creates a placeholder row showing the option name or the description when the name cannot be read
    PlaceholderControlElement(Option<T> option, Dim2i dim, String description) {
        super(option, dim);
        Objects.requireNonNull(description, "Description must not be null");
        this.name = nameOf(option, description);
    }

    // Returns a copy of the option name or the description when reading or copying it fails
    private static TextComponent nameOf(Option<?> option, String description) {
        TextComponent name = null;
        try {
            name = snapshot(option.getName(), 0);
        } catch (Throwable unreadable) {
            FailureText.absorb(unreadable);
        }
        return name != null ? name : TextComponent.literal(description);
    }

    // Returns a copy built from literal and translated and styled parts or null for any other part or an empty key list or nesting past the limit
    private static TextComponent snapshot(TextComponent component, int depth) {
        if (depth > MAX_NAME_DEPTH) {
            return null;
        }
        if (component instanceof TextComponent.Literal literal) {
            return literal.text() != null ? literal : null;
        }
        if (component instanceof TextComponent.Styled styled) {
            TextComponent inner = snapshot(styled.inner(), depth + 1);
            return inner != null ? new TextComponent.Styled(inner, EnumSet.copyOf(styled.styles())) : null;
        }
        if (component instanceof TextComponent.Translatable translatable) {
            List<String> keys = List.copyOf(translatable.keys());
            if (keys.isEmpty()) {
                return null;
            }
            Object[] args = translatable.args().toArray();
            for (int i = 0; i < args.length; i++) {
                // Converts each argument to text or to a copied component
                Object arg = args[i] instanceof TextComponent part ? snapshot(part, depth + 1) : String.valueOf(args[i]);
                if (arg == null) {
                    return null;
                }
                args[i] = arg;
            }
            return new TextComponent.Translatable(keys, List.of(args));
        }
        return null;
    }

    // Logs the failure and shows an error toast the first time the option fails
    static void report(Option<?> option, String name, Throwable broken) {
        if (BROKEN.add(option)) {
            LOGGER.error("Control of option {} failed, showing a placeholder row", name, broken);
            toast(name, broken);
        }
    }

    // Logs a failure in the option's own code and shows an error toast the first time the option fails
    static void reportOption(Option<?> option, String name, String during, Throwable failure) {
        if (BROKEN.add(option)) {
            LOGGER.error("Option {} failed while {}", name, during, failure);
            toast(name, failure);
        }
    }

    // Returns true when the option has already been reported as broken
    static boolean isBroken(Option<?> option) {
        return BROKEN.contains(option);
    }

    // Shows an error toast with the option name and the first line of the failure
    private static void toast(String name, Throwable failure) {
        ImpetusNotifications.error("Option failed: " + FailureText.firstLine(String.valueOf(name)), FailureText.of(failure));
    }

    // Always reports the option as unavailable
    @Override
    protected boolean isOptionAvailable() {
        return false;
    }

    // Keeps the row from ever taking focus
    @Override
    public void setFocused(boolean focused) {
        super.setFocused(false);
    }

    // Returns the copied option name
    @Override
    protected TextComponent getDisplayName() {
        return this.name;
    }

    // Returns the label width plus both insets limited to the row width
    @Override
    protected int measureColumnWidth(DrawContext drawContext) {
        int width = drawContext.getStringWidth(LABEL);
        this.columnLabelWidth = width;
        this.columnMeasured = true;
        return Math.clamp(width + 2L * LABEL_RIGHT_INSET, 0, Math.max(0, this.dim.width()));
    }

    // Draws the greyed name and then the label aligned right when it clears the name
    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float delta) {
        // Clears the column measurement from any earlier frame
        this.columnMeasured = false;
        super.render(drawContext, mouseX, mouseY, delta);
        // Stops after the base row on a row with no area or no room for a line of text
        if (!this.hasArea() || !this.hasRoomForText()) {
            return;
        }
        // Measures the label again when the text generation changes and reuses the column measurement from this frame
        long generation = this.textGeneration();
        if (generation != this.labelGeneration) {
            this.labelWidth = this.columnMeasured ? this.columnLabelWidth : drawContext.getStringWidth(LABEL);
            this.labelGeneration = generation;
        }
        // Computes the label position as a long and draws the label only when it clears the name
        long labelX = (long) this.dim.getLimitX() - this.labelWidth - LABEL_RIGHT_INSET;
        if (this.labelWidth >= 0 && labelX >= this.getNameLimitX()) {
            drawContext.drawString(LABEL, (int) labelX, this.dim.getCenterY() - TEXT_RAISE, LABEL_COLOR);
        }
    }
}