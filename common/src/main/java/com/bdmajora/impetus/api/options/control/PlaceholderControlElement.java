package com.bdmajora.impetus.api.options.control;

import com.bdmajora.impetus.api.options.structure.Option;
import com.bdmajora.impetus.engine.impl.gui.framework.DrawContext;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.engine.impl.notification.ImpetusNotifications;
import com.bdmajora.impetus.engine.impl.util.Dim2i;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

// Row standing in for an option whose control broke, with the name greyed out and an error label where the control would be
final class PlaceholderControlElement<T> extends ControlElement<T> {
    private static final Logger LOGGER = LogManager.getLogger();

    // Drawn in the control's column
    private static final TextComponent LABEL = TextComponent.translatable("impetus.options.control_failed");
    // Minecraft's red
    private static final int LABEL_COLOR = 0xFFFF5555;
    // Gap between the label and the row's right edge, as the built-in controls leave
    private static final int LABEL_RIGHT_INSET = 6;
    // Options known to be broken, each reported once and shown as a placeholder from then on, held weakly so the options of a closed screen can be collected
    private static final Set<Option<?>> BROKEN = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    // The option's name, read once so drawing never calls into the broken option again
    private final TextComponent name;
    // Width of the label, measured with the column on the first frame
    private int labelWidth;

    // Shows the option's name, or the given description when the option cannot give one
    PlaceholderControlElement(Option<T> option, Dim2i dim, String description) {
        super(option, dim);
        this.name = nameOf(option, description);
    }

    // The option's own name, or the description when reading it fails or gives none
    private static TextComponent nameOf(Option<?> option, String description) {
        TextComponent name = null;
        try {
            name = option.getName();
        } catch (VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable unreadable) {
            // Restores the interrupt flag after an interruption, and the description stands in below
            if (unreadable instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
        return name != null ? name : TextComponent.literal(description);
    }

    // Logs the failure and shows it as an error toast the first time an option fails, since every rebuild of the page fails it again
    static void report(Option<?> option, String name, Throwable broken) {
        if (!BROKEN.add(option)) {
            return;
        }
        LOGGER.error("Control of option {} failed, showing a placeholder row", name, broken);
        // Shows only the first line of the failure message
        ImpetusNotifications.error("Option failed: " + name, String.valueOf(broken).lines().findFirst().orElse(""));
    }

    // Logs a failure in the option's own code away from its rows and shows it as a toast, the first time the option fails anywhere
    static void reportOption(Option<?> option, String name, String during, Throwable failure) {
        if (!BROKEN.add(option)) {
            return;
        }
        LOGGER.error("Option {} failed while {}", name, during, failure);
        // Shows only the first line of the failure message
        ImpetusNotifications.error("Option failed: " + name, String.valueOf(failure).lines().findFirst().orElse(""));
    }

    // Whether the option failed before, so a row made for it now is a placeholder without its control being asked again
    static boolean isBroken(Option<?> option) {
        return BROKEN.contains(option);
    }

    // Greyed out whatever the option says, which also keeps Tab from stopping here
    @Override
    protected boolean isOptionAvailable() {
        return false;
    }

    // The name read when the placeholder was made
    @Override
    protected TextComponent getDisplayName() {
        return this.name;
    }

    // Room for the label and its insets, held to the row, without asking the broken control
    @Override
    protected int measureColumnWidth(DrawContext drawContext) {
        this.labelWidth = drawContext.getStringWidth(LABEL);
        return Math.clamp(this.labelWidth + 2L * LABEL_RIGHT_INSET, 0, Math.max(0, this.dim.width()));
    }

    // The greyed name, then the label right-aligned when the row can hold it
    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float delta) {
        super.render(drawContext, mouseX, mouseY, delta);
        // Draws nothing more on a row with no area or no room for a line of text, as the base row did
        if (!this.hasArea() || !this.hasRoomForText()) {
            return;
        }
        if (FittedText.fits(this.labelWidth, this.dim.width() - LABEL_RIGHT_INSET)) {
            drawContext.drawString(LABEL, this.dim.getLimitX() - this.labelWidth - LABEL_RIGHT_INSET, this.dim.getCenterY() - 4, LABEL_COLOR);
        }
    }
}