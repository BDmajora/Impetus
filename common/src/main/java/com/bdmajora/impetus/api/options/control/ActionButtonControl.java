package com.bdmajora.impetus.api.options.control;

import com.bdmajora.impetus.api.options.structure.Option;
import com.bdmajora.impetus.engine.impl.gui.framework.DrawContext;
import com.bdmajora.impetus.engine.impl.gui.framework.InteractionContext;
import com.bdmajora.impetus.engine.impl.gui.framework.Keys;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.engine.impl.gui.framework.TextFormattingStyle;
import com.bdmajora.impetus.engine.impl.notification.ImpetusNotifications;
import com.bdmajora.impetus.engine.impl.util.Dim2i;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.EnumSet;
import java.util.Objects;

// Control that shows a labelled button and runs an action when clicked
// Uses the option for its name and id and availability without reading or writing its value
public class ActionButtonControl implements Control<Boolean> {
    private static final Logger LOGGER = LogManager.getLogger();

    // Label used when none is given
    private static final TextComponent DEFAULT_LABEL = TextComponent.translatable("impetus.options.buttons.open");

    // Width of the button column at the right of the row
    private static final int MAX_WIDTH = 70;
    // Distance from the right edge of the row to the right end of the label
    private static final int LABEL_RIGHT_INSET = 18;
    // Distance from the right edge of the row to the left edge of the arrow and the narrowest row width that shows the arrow
    private static final int ARROW_RIGHT_INSET = 10;
    // Half the glyph height used to centre text vertically on the row
    private static final int TEXT_RAISE = 4;
    // Text drawn to the right of the label
    private static final TextComponent ARROW = TextComponent.literal(">");

    private final Option<Boolean> option;
    private final TextComponent actionLabel;
    private final Runnable action;

    // Creates a button with the default label
    public ActionButtonControl(Option<Boolean> option, Runnable action) {
        this(option, DEFAULT_LABEL, action);
    }

    // Creates a button with the given label and rejects any null argument
    public ActionButtonControl(Option<Boolean> option, TextComponent actionLabel, Runnable action) {
        this.option = Objects.requireNonNull(option, "Option must not be null");
        // Stores a copy of the label
        this.actionLabel = FittedText.copied(Objects.requireNonNull(actionLabel, "Action label must not be null"));
        this.action = Objects.requireNonNull(action, "Action must not be null");
    }

    // Returns the option the button is attached to
    @Override
    public Option<Boolean> getOption() {
        return this.option;
    }

    // Creates the button element at the given position and size and rejects null bounds
    @Override
    public ControlElement<Boolean> createElement(Dim2i dim) {
        return new Button(this.option, Objects.requireNonNull(dim, "Dimensions must not be null"), this.actionLabel, this.action);
    }

    // Returns the maximum control width in pixels
    @Override
    public int getMaxWidth() {
        return MAX_WIDTH;
    }

    // Clickable element that draws the label and an arrow
    private static final class Button extends ControlElement<Boolean> {
        private final TextComponent actionLabel;
        // Underlined label in its enabled and disabled styles
        private final TextComponent enabledLabel;
        private final TextComponent disabledLabel;
        private final Runnable action;

        // Label as drawn in both styles or null when the ellipsis alone does not fit
        private TextComponent shownEnabled;
        private TextComponent shownDisabled;
        // Width of the label as drawn
        private int shownWidth;
        // Text generation of the row the label was last fitted for
        private long shownGeneration;

        // Draw context from the last frame used to convert the option name to text
        private DrawContext lastDrawContext;

        // Stores the label and action and builds both label styles
        Button(Option<Boolean> option, Dim2i dim, TextComponent actionLabel, Runnable action) {
            super(option, dim);
            this.actionLabel = actionLabel;
            this.enabledLabel = actionLabel.withStyle(TextFormattingStyle.UNDERLINE);
            this.disabledLabel = this.disabled(actionLabel);
            this.action = action;
        }

        // Draws the background and then the label and arrow at the right edge in the enabled or disabled colour
        @Override
        public void render(DrawContext drawContext, int mouseX, int mouseY, float delta) {
            super.render(drawContext, mouseX, mouseY, delta);
            this.lastDrawContext = drawContext;
            // Stops after the background on a row with no area or no room for a line of text
            if (!this.hasArea() || !this.hasRoomForText()) {
                return;
            }

            boolean enabled = this.isDrawnAvailable();
            int color = enabled ? this.getAccentColor(drawContext) : this.getDisabledControlColor();
            int limitX = this.dim.getLimitX();
            int textY = this.dim.getCenterY() - TEXT_RAISE;

            // Fits the label again when the text generation of the row changes
            long generation = this.textGeneration();
            if (generation != this.shownGeneration) {
                // Limits the label to the button column within the row width
                this.fitLabel(drawContext, generation, Math.min(MAX_WIDTH, this.dim.width()) - LABEL_RIGHT_INSET);
            }
            TextComponent label = enabled ? this.shownEnabled : this.shownDisabled;
            if (label != null) {
                drawContext.drawString(label, limitX - this.shownWidth - LABEL_RIGHT_INSET, textY, color);
            }
            // Leaves the arrow out of a row too narrow to hold it
            if (this.dim.width() >= ARROW_RIGHT_INSET) {
                drawContext.drawString(ARROW, limitX - ARROW_RIGHT_INSET, textY, color);
            }
        }

        // Fits the label to the given width in both styles by keeping it whole or cutting it or leaving it out
        private void fitLabel(DrawContext drawContext, long generation, int maxWidth) {
            TextComponent enabled = this.enabledLabel;
            TextComponent disabled = this.disabledLabel;
            int width = drawContext.getStringWidth(enabled);
            if (!FittedText.fits(width, maxWidth)) {
                enabled = null;
                disabled = null;
                width = 0;
                // Cuts the text by whole characters to fit underlined with the ellipsis and shortens it again when the underline widens it past the width
                FittedText.Cut cut = FittedText.cutToFit(drawContext, drawContext.extractString(this.actionLabel), this.enabledLabel, maxWidth);
                if (cut != null) {
                    enabled = cut.styled();
                    // Greys out the same cut text in the label's own styles
                    disabled = this.disabled(FittedText.restyled(this.actionLabel, TextComponent.literal(cut.text())));
                    width = cut.width();
                }
            }
            // Stores the fitted label and the generation it was fitted for
            this.shownEnabled = enabled;
            this.shownDisabled = disabled;
            this.shownWidth = width;
            this.shownGeneration = generation;
        }

        // Returns the label underlined and greyed out with its own colours removed
        private TextComponent disabled(TextComponent label) {
            return this.formatDisabledControlValue(withoutColours(label).withStyle(TextFormattingStyle.UNDERLINE));
        }

        // Presses the button on a left click inside it
        @Override
        public boolean mouseClicked(InteractionContext context, double mouseX, double mouseY, int button) {
            return button == 0 && this.dim.containsCursor(mouseX, mouseY) && this.press(context);
        }

        // Presses the button on Enter or Space while it has focus
        @Override
        public boolean keyTyped(InteractionContext context, char typedChar, int keyCode) {
            return this.isFocused() && Keys.isPress(typedChar, keyCode) && this.press(context);
        }

        // Plays the click sound and runs the action when the option is available
        private boolean press(InteractionContext context) {
            // Returns false while the option is unavailable
            if (!this.isOptionAvailable()) {
                return false;
            }

            // Plays the click sound before running the action
            context.playClickSound();
            try {
                this.action.run();
            } catch (VirtualMachineError fatal) {
                throw fatal;
            } catch (Throwable thrown) {
                // Restores the interrupt flag after an interruption
                if (thrown instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                this.reportFailure(thrown);
            }
            // Reports the input as handled even when the action fails
            return true;
        }

        // Logs the failure and shows it as an error toast
        private void reportFailure(Throwable thrown) {
            TextComponent name = this.option.getName();
            String nameText = name != null && this.lastDrawContext != null ? this.lastDrawContext.extractString(name) : String.valueOf(name);
            LOGGER.error("Action button {} ({}) failed", nameText, this.option.getId(), thrown);
            // Shows only the first line of the failure message
            ImpetusNotifications.error("Button failed: " + nameText, String.valueOf(thrown).lines().findFirst().orElse(""));
        }
    }

    // Removes colour styles from every layer of the label and from any translation arguments that are components
    private static TextComponent withoutColours(TextComponent label) {
        if (label instanceof TextComponent.Styled styled) {
            TextComponent inner = withoutColours(styled.inner());
            EnumSet<TextFormattingStyle> styles = EnumSet.copyOf(styled.styles());
            styles.removeIf(TextFormattingStyle::isColor);
            // Drops a layer that held only colours
            return styles.isEmpty() ? inner : new TextComponent.Styled(inner, styles);
        }
        if (label instanceof TextComponent.Translatable translatable && translatable.args().stream().anyMatch(TextComponent.class::isInstance)) {
            return new TextComponent.Translatable(translatable.keys(),
                    translatable.args().stream().map(arg -> arg instanceof TextComponent component ? withoutColours(component) : arg).toList());
        }
        return label;
    }
}
