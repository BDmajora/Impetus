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
import com.bdmajora.impetus.engine.impl.util.Dim2i;
import com.bdmajora.impetus.api.options.OptionIdentifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

public class OptionPageFrame extends AbstractFrame {
    // Height of the page-title header band rendered above the option rows
    private static final int SECTION_HEADER_HEIGHT = 18;
    // Height of a group heading row, drawn above a named group's rows; unnamed groups keep only the padding
    private static final int GROUP_HEADER_HEIGHT = 14;
    private static final int OPTION_ROW_HEIGHT = 18;
    private static final int GROUP_PADDING = 4;
    private static final int GROUP_HEADER_COLOR = 0xFFA8A8A8;

    protected final OptionPage page;
    private long lastTime = 0;
    private ControlElement<?> lastHoveredElement = null;
    protected final Predicate<Option<?>> optionFilter;
    // Named-group headings at their frame-relative y, rebuilt with the frame
    private final List<GroupHeading> groupHeadings = new ArrayList<>();

    private record GroupHeading(TextComponent name, int y) {
    }

    public OptionPageFrame(Dim2i dim, boolean renderOutline, OptionPage page, Predicate<Option<?>> optionFilter) {
        super(dim, renderOutline);
        this.page = page;
        this.optionFilter = optionFilter;
        this.setupFrame();
        this.buildFrame();
    }

    // Starts a builder
    public static Builder createBuilder() {
        return new Builder();
    }

    // Sizes the frame to its content: one layout pass with no elements created, so the scroll container knows the height before the rows exist
    public void setupFrame() {
        this.children.clear();
        this.drawable.clear();
        this.controlElements.clear();
        this.groupHeadings.clear();

        this.dim = this.dim.withHeight(this.layout(false));
    }

    // Stacks the groups vertically, a heading over each named one and padding between them
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

    // Walks the groups once; with create, control elements and headings are added, without it only the height is measured. Returns the content height including the page header; groups with nothing visible take no space at all
    private int layout(boolean create) {
        int y = SECTION_HEADER_HEIGHT;
        boolean first = true;

        for (OptionGroup group : this.page.getGroups()) {
            List<Option<?>> visible = new ArrayList<>();
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
                    Control<?> control = option.getControl();
                    Dim2i dim = new Dim2i(0, y, this.dim.width(), OPTION_ROW_HEIGHT).withParentOffset(this.dim);
                    this.children.add(control.createElement(dim));
                }
                y += OPTION_ROW_HEIGHT;
            }
        }

        return y;
    }

    // Draws controls, then the tooltip for whichever is hovered
    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float delta) {
        this.renderSectionHeader(drawContext);
        this.renderGroupHeadings(drawContext);

        ControlElement<?> hoveredElement = this.isMouseOver(mouseX, mouseY) ? this.findHoveredControl(mouseX, mouseY) : null;
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

    // The first control under the cursor, a plain loop since this runs every frame
    private ControlElement<?> findHoveredControl(int mouseX, int mouseY) {
        for (ControlElement<?> control : this.controlElements) {
            if (control.isMouseOver(mouseX, mouseY)) {
                return control;
            }
        }
        return null;
    }

    // Muted group names, indented like the page title so the columns line up
    private void renderGroupHeadings(DrawContext drawContext) {
        for (GroupHeading heading : this.groupHeadings) {
            int textY = this.dim.y() + heading.y() + (GROUP_HEADER_HEIGHT - drawContext.lineHeight()) / 2 + 1;
            drawContext.drawString(heading.name(), this.dim.x() + 7, textY, GROUP_HEADER_COLOR);
        }
    }

    // Accent bar + page name, styled after Umbra/Sodium's settings screen section headers
    private void renderSectionHeader(DrawContext drawContext) {
        int x = this.dim.x();
        int y = this.dim.y();
        int textY = y + (SECTION_HEADER_HEIGHT - 4 - drawContext.lineHeight() / 2) / 2;
        int accentColor = drawContext.getModAccentColor(this.page.getId().getModId());

        drawContext.fill(x, y + 2, x + 2, y + SECTION_HEADER_HEIGHT - 6, accentColor);
        drawContext.drawString(this.page.getName(), x + 7, textY, accentColor);
    }

    // Owning mod for the tooltip footer; vanilla options are Impetus' own pages, so they never get an "added by" line
    private static String normalizeModForTooltip(@Nullable String mod) {
        return "minecraft".equals(mod) ? "impetus" : mod;
    }

    // Wrapped tooltip beside the control, flipped left when it would overflow
    private void renderOptionTooltip(DrawContext drawContext, ControlElement<?> element) {
        if (this.lastTime + 500 > System.currentTimeMillis()) return;

        Dim2i dim = element.getDimensions();

        int textPadding = 3;
        int boxPadding = 3;

        int boxWidth = dim.width();

        //Offset based on mouse position, width and height of content and width and height of the window
        int boxY = dim.getLimitY();
        int boxX = dim.x();

        Option<?> option = element.getOption();
        var tooltip = new ArrayList<>(drawContext.split(option.getTooltip(), boxWidth - (textPadding * 2)));

        OptionImpact impact = option.getImpact();

        if (impact != null) {
            var impactString = TextComponent.translatable("impetus.options.performance_impact_string", impact.getLocalizedName()).withStyle(TextFormattingStyle.GRAY);
            tooltip.add(impactString);
        }

        var id = option.getId();

        if (OptionIdentifier.isPresent(page.getId()) && OptionIdentifier.isPresent(id) && !Objects.equals(normalizeModForTooltip(page.getId().getModId()), normalizeModForTooltip(id.getModId()))) {
            var addedByModString = TextComponent.translatable("impetus.options.added_by_mod_string", TextComponent.literal(id.getModId()).withStyle(TextFormattingStyle.WHITE)).withStyle(TextFormattingStyle.GRAY);
            tooltip.add(addedByModString);
        }

        int boxHeight = (tooltip.size() * 12) + boxPadding;
        int boxYLimit = boxY + boxHeight;
        int boxYCutoff = this.dim.getLimitY();

        // If the box is going to be cutoff on the Y-axis, move it back up the difference
        if (boxYLimit > boxYCutoff) {
            boxY -= boxHeight + dim.height();
        }

        if (boxY < 0) {
            boxY = dim.getLimitY();
        }

        drawContext.pushMatrix();

        drawContext.translate(0, 0, 90);

        drawContext.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xE0000000);
        drawContext.drawBorder(boxX, boxY, boxX + boxWidth, boxY + boxHeight, drawContext.getModAccentColor(this.page.getId().getModId()));

        for (int i = 0; i < tooltip.size(); i++) {
            drawContext.drawString(tooltip.get(i), boxX + textPadding, boxY + textPadding + (i * 12), 0xFFFFFFFF, true);
        }

        drawContext.popMatrix();
    }

    public static class Builder {
        private Dim2i dim;
        private boolean renderOutline;
        private OptionPage page;
        private Predicate<Option<?>> optionFilter = o -> true;

        // Frame bounds
        public Builder setDimension(Dim2i dim) {
            this.dim = dim;
            return this;
        }

        // Debug outline
        public Builder shouldRenderOutline(boolean renderOutline) {
            this.renderOutline = renderOutline;
            return this;
        }

        // The page to show
        public Builder setOptionPage(OptionPage page) {
            this.page = page;
            return this;
        }

        // Hides options failing the predicate, for search
        public Builder setOptionFilter(Predicate<Option<?>> optionFilter) {
            this.optionFilter = optionFilter;
            return this;
        }

        // Finalises
        public OptionPageFrame build() {
            Objects.requireNonNull(this.dim, "Dimension must be specified");
            Objects.requireNonNull(this.page, "Option Page must be specified");

            return new OptionPageFrame(this.dim, this.renderOutline, this.page, this.optionFilter);
        }
    }
}
