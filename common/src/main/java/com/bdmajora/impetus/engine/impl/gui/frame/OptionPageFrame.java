package com.bdmajora.impetus.engine.impl.gui.frame;

import com.bdmajora.impetus.api.options.structure.Option;
import com.bdmajora.impetus.api.options.structure.OptionGroup;
import com.bdmajora.impetus.api.options.structure.OptionImpact;
import com.bdmajora.impetus.api.options.structure.OptionPage;
import com.bdmajora.impetus.api.options.control.Control;
import com.bdmajora.impetus.api.options.control.ControlElement;
import com.bdmajora.impetus.engine.impl.gui.framework.DrawContext;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.engine.impl.gui.framework.TextFormattingStyle;
import com.bdmajora.impetus.engine.impl.notification.ImpetusNotifications;
import com.bdmajora.impetus.engine.impl.util.Dim2i;
import com.bdmajora.impetus.engine.impl.util.FailureText;
import com.bdmajora.impetus.engine.impl.util.collections.WeakIdentitySet;
import com.bdmajora.impetus.api.options.OptionIdentifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

// Frame that lists the option rows of one page.
public class OptionPageFrame extends AbstractFrame {
    // Logger of tooltip failures.
    private static final Logger LOGGER = LogManager.getLogger();
    // Options whose tooltip failed.
    private static final WeakIdentitySet<Option<?>> BROKEN_TOOLTIPS = new WeakIdentitySet<>();
    // Height of the page title band.
    private static final int SECTION_HEADER_HEIGHT = 18;
    // Height of a group heading row.
    private static final int GROUP_HEADER_HEIGHT = 14;
    // Height of an option row.
    private static final int OPTION_ROW_HEIGHT = 18;
    // Space between groups.
    private static final int GROUP_PADDING = 4;
    // Color of group headings.
    private static final int GROUP_HEADER_COLOR = 0xFFA8A8A8;
    // Tooltip line of each impact by ordinal.
    private static final TextComponent[] IMPACT_LINES = Arrays.stream(OptionImpact.values())
            .map(impact -> TextComponent.translatable("impetus.options.performance_impact_string", impact.getLocalizedName()).withStyle(TextFormattingStyle.GRAY))
            .toArray(TextComponent[]::new);

    // Page shown by the frame.
    protected final OptionPage page;
    // Time the hover started or zero.
    private long lastTime = 0;
    // Row under the cursor in the last frame.
    private ControlElement<?> lastHoveredElement = null;
    // Filter that selects the visible options.
    protected final Predicate<Option<?>> optionFilter;
    // Headings of named groups.
    private final List<GroupHeading> groupHeadings = new ArrayList<>();

    // Heading text with its offset in the frame.
    private record GroupHeading(TextComponent name, int y) {
    }

    // Creates a frame and rejects a null page and shows every option for a null filter.
    public OptionPageFrame(Dim2i dim, boolean renderOutline, OptionPage page, Predicate<Option<?>> optionFilter) {
        super(dim, renderOutline);
        this.page = Objects.requireNonNull(page, "page");
        this.optionFilter = optionFilter != null ? optionFilter : option -> true;
        this.setupFrame();
        this.buildFrame();
    }

    // Creates a builder.
    public static Builder createBuilder() {
        return new Builder();
    }

    // Sets the height of the frame to the height of its content.
    public void setupFrame() {
        this.children.clear();
        this.drawable.clear();
        this.controlElements.clear();
        this.groupHeadings.clear();

        this.dim = this.dim.withHeight(this.layout(false));
    }

    // Creates the option rows and group headings.
    @Override
    public void buildFrame() {
        if (this.page == null) return;

        this.children.clear();
        this.drawable.clear();
        this.controlElements.clear();
        this.groupHeadings.clear();

        this.layout(true);

        super.buildFrame();
    }

    // Measures the content height and creates rows and headings when asked.
    private int layout(boolean create) {
        int y = SECTION_HEADER_HEIGHT;
        boolean first = true;
        // Visible options of the current group
        List<Option<?>> visible = new ArrayList<>();

        for (OptionGroup group : this.page.getGroups()) {
            visible.clear();
            for (Option<?> option : group.getOptions()) {
                if (this.optionFilter.test(option)) {
                    visible.add(option);
                }
            }
            if (visible.isEmpty()) {
                continue;
            }
            if (!first) {
                y += GROUP_PADDING;
            }
            first = false;

            if (group.getName() != null) {
                if (create) {
                    this.groupHeadings.add(new GroupHeading(group.getName(), y));
                }
                y += GROUP_HEADER_HEIGHT;
            }

            for (Option<?> option : visible) {
                if (create) {
                    Dim2i dim = new Dim2i(0, y, this.dim.width(), OPTION_ROW_HEIGHT).withParentOffset(this.dim);
                    this.children.add(Control.createElementOrPlaceholder(option, dim));
                }
                y += OPTION_ROW_HEIGHT;
            }
        }

        return y;
    }

    // Draws the headings and rows and the tooltip of the hovered row.
    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float delta) {
        this.renderSectionHeader(drawContext);
        this.renderGroupHeadings(drawContext);

        // Finds the hovered row
        ControlElement<?> hoveredElement = this.getCapturedChild() == null ? this.findHoveredControl(mouseX, mouseY) : null;
        super.render(drawContext, mouseX, mouseY, delta);
        if (hoveredElement != null && this.lastHoveredElement == hoveredElement) {
            if (this.lastTime == 0) {
                this.lastTime = System.currentTimeMillis();
            }
            this.renderOptionTooltip(drawContext, hoveredElement);
        } else {
            this.lastTime = 0;
            this.lastHoveredElement = hoveredElement;
        }
    }

    // Returns the first row under the cursor and replaces a row that throws.
    private ControlElement<?> findHoveredControl(int mouseX, int mouseY) {
        for (int i = 0; i < this.controlElements.size(); i++) {
            ControlElement<?> control = this.controlElements.get(i);
            try {
                if (control.isMouseOver(mouseX, mouseY)) {
                    return control;
                }
            } catch (Throwable broken) {
                if (!this.recoverChild(control, broken)) {
                    throw broken;
                }
            }
        }
        return null;
    }

    // Draws the group names.
    private void renderGroupHeadings(DrawContext drawContext) {
        for (GroupHeading heading : this.groupHeadings) {
            int textY = this.dim.y() + heading.y() + (GROUP_HEADER_HEIGHT - drawContext.lineHeight()) / 2 + 1;
            drawContext.drawString(heading.name(), this.dim.x() + 7, textY, GROUP_HEADER_COLOR);
        }
    }

    // Draws the accent bar and the page name.
    private void renderSectionHeader(DrawContext drawContext) {
        int x = this.dim.x();
        int y = this.dim.y();
        int textY = y + (SECTION_HEADER_HEIGHT - 4 - drawContext.lineHeight() / 2) / 2;
        int accentColor = drawContext.getModAccentColor(this.page.getId().getModId());

        drawContext.fill(x, y + 2, x + 2, y + SECTION_HEADER_HEIGHT - 6, accentColor);
        drawContext.drawString(this.page.getName(), x + 7, textY, accentColor);
    }

    // Returns the mod id used to compare owners of a tooltip.
    private static String normalizeModForTooltip(@Nullable String mod) {
        return mod == null ? null : switch (mod) {
            case "minecraft", "extras", "coarctatio", "fulgor", "equilibrium", "umbra", "dynamiclights", "linuxextras" -> "impetus";
            default -> mod;
        };
    }

    // Draws the tooltip after a hover delay and reports a failing tooltip once.
    private void renderOptionTooltip(DrawContext drawContext, ControlElement<?> element) {
        if (this.lastTime + 500 > System.currentTimeMillis()) return;

        // Reads the option of the row
        Option<?> option;
        try {
            option = element.getOption();
        } catch (Throwable broken) {
            if (!this.recoverChild(element, broken)) {
                throw broken;
            }
            return;
        }
        // Replaces a row without an option
        if (option == null) {
            this.recoverChild(element, new IllegalStateException("Element reports no option"));
            return;
        }
        if (BROKEN_TOOLTIPS.contains(option)) {
            return;
        }
        int matrices = drawContext.matrixDepth();
        int scissors = drawContext.scissorDepth();
        try {
            this.drawOptionTooltip(drawContext, element.getDimensions(), option);
        } catch (VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable broken) {
            // Restores the draw state
            drawContext.unwindTo(matrices, scissors);
            if (BROKEN_TOOLTIPS.add(option)) {
                String name = nameOf(option);
                LOGGER.error("Tooltip of option {} failed and is no longer shown", name, broken);
                // Shows only the first lines in the notification
                ImpetusNotifications.error("Tooltip failed: " + FailureText.firstLine(name), FailureText.of(broken));
            }
        }
    }

    // Returns the id or the name or the class name of an option.
    private static String nameOf(Option<?> option) {
        try {
            OptionIdentifier<?> id = option.getId();
            return OptionIdentifier.isPresent(id) ? id.toString() : String.valueOf(option.getName());
        } catch (Throwable unreadable) {
            FailureText.absorb(unreadable);
            return option.getClass().getName();
        }
    }

    // Draws the tooltip below or above the row.
    private void drawOptionTooltip(DrawContext drawContext, Dim2i dim, Option<?> option) {
        int textPadding = 3;
        int boxPadding = 3;

        int boxWidth = dim.width();

        int boxY = dim.getLimitY();
        int boxX = dim.x();

        // Collects the tooltip lines
        List<TextComponent> tooltip = new ArrayList<>();
        TextComponent text = option.getTooltip();
        if (text != null) {
            tooltip.addAll(drawContext.split(text, boxWidth - (textPadding * 2)));
        }

        OptionImpact impact = option.getImpact();

        if (impact != null) {
            tooltip.add(IMPACT_LINES[impact.ordinal()]);
        }

        var id = option.getId();

        if (OptionIdentifier.isPresent(page.getId()) && OptionIdentifier.isPresent(id) && !Objects.equals(normalizeModForTooltip(page.getId().getModId()), normalizeModForTooltip(id.getModId()))) {
            var addedByModString = TextComponent.translatable("impetus.options.added_by_mod_string", TextComponent.literal(id.getModId()).withStyle(TextFormattingStyle.WHITE)).withStyle(TextFormattingStyle.GRAY);
            tooltip.add(addedByModString);
        }

        // Skips an empty tooltip
        if (tooltip.isEmpty()) {
            return;
        }

        int boxHeight = (tooltip.size() * 12) + boxPadding;
        int boxYLimit = boxY + boxHeight;
        int boxYCutoff = this.dim.getLimitY();

        // Moves the box above the row when it passes the bottom
        if (boxYLimit > boxYCutoff) {
            boxY -= boxHeight + dim.height();
        }

        if (boxY < 0) {
            boxY = dim.getLimitY();
        }

        drawContext.pushMatrix();
        // Pops the transform on every path
        try {
            drawContext.translate(0, 0, 90);

            drawContext.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xE0000000);
            drawContext.drawBorder(boxX, boxY, boxX + boxWidth, boxY + boxHeight, drawContext.getModAccentColor(this.page.getId().getModId()));

            for (int i = 0; i < tooltip.size(); i++) {
                drawContext.drawString(tooltip.get(i), boxX + textPadding, boxY + textPadding + (i * 12), 0xFFFFFFFF, true);
            }
        } finally {
            drawContext.popMatrix();
        }
    }

    // Collects the settings of an option page frame.
    public static class Builder {
        // Bounds of the frame.
        private Dim2i dim;
        // Outline state of the frame.
        private boolean renderOutline;
        // Page shown by the frame.
        private OptionPage page;
        // Filter that selects the visible options.
        private Predicate<Option<?>> optionFilter = o -> true;

        // Stores the bounds of the frame.
        public Builder setDimension(Dim2i dim) {
            this.dim = dim;
            return this;
        }

        // Stores whether the frame draws an outline.
        public Builder shouldRenderOutline(boolean renderOutline) {
            this.renderOutline = renderOutline;
            return this;
        }

        // Stores the page.
        public Builder setOptionPage(OptionPage page) {
            this.page = page;
            return this;
        }

        // Stores the option filter.
        public Builder setOptionFilter(Predicate<Option<?>> optionFilter) {
            this.optionFilter = optionFilter;
            return this;
        }

        // Creates the frame and rejects missing bounds or page.
        public OptionPageFrame build() {
            Objects.requireNonNull(this.dim, "Dimension must be specified");
            Objects.requireNonNull(this.page, "Option Page must be specified");

            return new OptionPageFrame(this.dim, this.renderOutline, this.page, this.optionFilter);
        }
    }
}
