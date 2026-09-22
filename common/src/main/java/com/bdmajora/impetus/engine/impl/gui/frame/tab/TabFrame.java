package com.bdmajora.impetus.engine.impl.gui.frame.tab;

import com.bdmajora.impetus.engine.api.util.ColorARGB;
import com.bdmajora.impetus.engine.impl.gui.framework.DrawContext;
import com.bdmajora.impetus.engine.impl.gui.framework.InteractionContext;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.engine.impl.gui.widgets.AbstractWidget;
import com.bdmajora.impetus.engine.impl.gui.widgets.FlatButtonWidget;
import com.bdmajora.impetus.engine.impl.gui.theme.DefaultColors;
import com.bdmajora.impetus.engine.impl.util.Dim2i;
import com.bdmajora.impetus.engine.impl.gui.frame.AbstractFrame;
import com.bdmajora.impetus.engine.impl.gui.frame.ScrollableFrame;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// Sidebar of mod groups, each a header over its pages, beside the selected page's frame; a header click folds its group so a sidebar of twenty pages stays short, and the fold state lives for the session like the selected tab does
public class TabFrame extends AbstractFrame {
    private static final int TAB_OPTION_INDENT = 5;
    private static final int TAB_HEIGHT = 18;

    // Group ids folded shut; static so a rebuild (resize, search, apply) keeps them, matching the controller's selected-tab reference
    private static final Set<String> COLLAPSED_GROUPS = new HashSet<>();

    private Dim2i tabSection;
    private final Dim2i frameSection;
    private final Map<String, List<Tab<?>>> tabs;
    private final Map<String, Integer> modAccentColors;
    private final Runnable onSetTab;
    private final AtomicReference<TextComponent> tabSectionSelectedTab;
    private final AtomicReference<Integer> tabSectionScrollBarOffset;
    private Tab<?> selectedTab;
    private AbstractFrame selectedFrame;
    private ScrollableFrame sidebarFrame;

    public TabFrame(DrawContext drawContext, Dim2i dim, boolean renderOutline, Map<String, List<Tab<?>>> tabs, Runnable onSetTab, AtomicReference<TextComponent> tabSectionSelectedTab, AtomicReference<Integer> tabSectionScrollBarOffset) {
        super(dim, renderOutline);
        this.tabs = Collections.unmodifiableMap(tabs.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> List.copyOf(e.getValue()), (a, b) -> a, LinkedHashMap::new)));
        this.modAccentColors = this.tabs.keySet().stream().collect(Collectors.toMap(id -> id, drawContext::getModAccentColor, (a, b) -> a, LinkedHashMap::new));
        Optional<Integer> result = Stream.concat(
                // Icon padding + icon + icon padding, matching where TabHeaderWidget starts drawing its name, plus the fold marker on the right
                tabs.keySet().stream().map(id -> {
                    int headerTextOffset = 5 + 20 + 5;
                    return drawContext.getStringWidth(drawContext.getFriendlyModName(id)) + headerTextOffset + TabHeaderWidget.MARKER_WIDTH;
                }),
                tabStream().map(tab -> drawContext.getStringWidth(tab.title()) + TAB_OPTION_INDENT)
        ).max(Integer::compareTo);

        this.tabSection = new Dim2i(this.dim.x(), this.dim.y(), result.map(integer -> integer + (24)).orElseGet(() -> (int) (this.dim.width() * 0.35D)), this.dim.height());
        this.frameSection = new Dim2i(this.tabSection.getLimitX(), this.dim.y(), this.dim.width() - this.tabSection.width(), this.dim.height());

        this.onSetTab = onSetTab;
        this.tabSectionSelectedTab = tabSectionSelectedTab;
        this.tabSectionScrollBarOffset = tabSectionScrollBarOffset;

        if (this.tabSectionSelectedTab.get() != null) {
            this.selectedTab = tabStream().filter(tab -> tab.title().equals(this.tabSectionSelectedTab.get())).findAny().orElse(null);
        }

        this.buildFrame();
    }

    // Every tab across every group
    private Stream<Tab<?>> tabStream() {
        return this.tabs.values().stream().flatMap(Collection::stream);
    }

    // Starts a builder
    public static Builder createBuilder() {
        return new Builder();
    }

    // Switches the selected tab and rebuilds the content frame; the tab's group unfolds so the selection is never hidden
    public void setTab(Tab<?> tab) {
        this.selectedTab = tab;
        this.tabSectionSelectedTab.set(this.selectedTab.title());
        COLLAPSED_GROUPS.remove(this.groupOf(tab));
        if (this.onSetTab != null) {
            this.onSetTab.run();
        }
        this.buildFrame();
    }

    // The group id a tab belongs to, or null for one not in the sidebar
    private String groupOf(Tab<?> tab) {
        for (Map.Entry<String, List<Tab<?>>> entry : this.tabs.entrySet()) {
            if (entry.getValue().contains(tab)) {
                return entry.getKey();
            }
        }
        return null;
    }

    // Folds or unfolds a group and relays the sidebar; the content frame is untouched
    private void toggleGroup(String groupId) {
        if (!COLLAPSED_GROUPS.remove(groupId)) {
            COLLAPSED_GROUPS.add(groupId);
        }
        this.buildFrame();
    }

    // Sidebar height for the current fold state: every header plus the rows of unfolded groups
    private int sidebarContentHeight() {
        int height = 0;
        for (Map.Entry<String, List<Tab<?>>> entry : this.tabs.entrySet()) {
            height += TabHeaderWidget.HEIGHT;
            if (!COLLAPSED_GROUPS.contains(entry.getKey())) {
                height += entry.getValue().size() * TAB_HEIGHT;
            }
        }
        return height;
    }

    class TabSidebarFrame extends AbstractFrame {
        TabSidebarFrame(Dim2i dim) {
            super(dim, false);
        }

        // Lays out children for the current dimensions
        @Override
        public void buildFrame() {
            this.children.clear();
            this.drawable.clear();
            this.controlElements.clear();

            rebuildTabs();

            super.buildFrame();
        }

        // Recreates the group headers and, under each unfolded one, its tab buttons
        private void rebuildTabs() {
            int offsetY = 0;
            int width = tabSection.width() - 4;

            for (var modEntry : tabs.entrySet()) {
                String groupId = modEntry.getKey();
                int accentColor = modAccentColors.getOrDefault(groupId, DefaultColors.ELEMENT_ACTIVATED);
                boolean collapsed = COLLAPSED_GROUPS.contains(groupId);

                Dim2i modHeaderDim = new Dim2i(0, offsetY, width, TabHeaderWidget.HEIGHT).withParentOffset(tabSection);
                offsetY += TabHeaderWidget.HEIGHT;
                TabHeaderWidget headerButton = new TabHeaderWidget(modHeaderDim, groupId, collapsed, () -> TabFrame.this.toggleGroup(groupId));
                headerButton.setLeftAligned(true);
                this.children.add(headerButton);

                if (collapsed) {
                    continue;
                }

                for (Tab<?> tab : modEntry.getValue()) {
                    Dim2i tabDim = new Dim2i(0, offsetY, width, TAB_HEIGHT).withParentOffset(tabSection);

                    FlatButtonWidget button = new FlatButtonWidget(tabDim, tab.title(), () -> {
                        if(tab.onSelectFunction() == null || tab.onSelectFunction().get()) {
                            TabFrame.this.setTab(tab);
                        }
                    }) {
                        // Indent for the tab label
                        @Override
                        protected int getLeftAlignedTextOffset(DrawContext drawContext) {
                            return TAB_OPTION_INDENT + super.getLeftAlignedTextOffset(drawContext);
                        }
                    };

                    button.setSelected(TabFrame.this.selectedTab == tab);
                    button.setLeftAligned(true);
                    FlatButtonWidget.Style style = FlatButtonWidget.Style.defaults();
                    style.textDefault = ColorARGB.withAlpha(accentColor, 0xB8);
                    style.textSelected = 0xFFFFFFFF;
                    style.accentColor = accentColor;
                    button.setStyle(style);
                    this.children.add(button);

                    offsetY += TAB_HEIGHT;
                }
            }
        }
    }

    // Lays out children for the current dimensions
    @Override
    public void buildFrame() {
        this.children.clear();
        this.drawable.clear();
        this.controlElements.clear();

        if (this.selectedTab == null) {
            if (!this.tabs.isEmpty()) {
                // Just use the first tab for now
                this.selectedTab = tabStream().findFirst().orElseThrow();
            }
        }

        // The sidebar scrolls only when the folded layout is taller than the frame
        int contentHeight = this.sidebarContentHeight();
        Dim2i sidebarInner = contentHeight > this.tabSection.height() ? this.tabSection.withHeight(contentHeight) : this.tabSection;

        this.sidebarFrame = ScrollableFrame.createBuilder()
                .setDimension(this.tabSection)
                .setFrame(new TabSidebarFrame(sidebarInner))
                .setVerticalScrollBarOffset(this.tabSectionScrollBarOffset)
                .build();

        this.children.add(this.sidebarFrame);

        this.rebuildTabFrame();

        super.buildFrame();
    }

    // Recreates the content area for the selected tab
    private void rebuildTabFrame() {
        if (this.selectedTab == null) return;
        AbstractFrame frame = this.selectedTab.createFrame(this.frameSection);
        if (frame != null) {
            this.selectedFrame = frame;
            frame.buildFrame();
            this.children.add(frame);
        }
    }

    // Draws header, separator and content
    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float delta) {
        for (AbstractWidget widget : this.children) {
            if (widget != this.selectedFrame) {
                widget.render(drawContext, mouseX, mouseY, delta);
            }
        }
        if(this.selectedFrame != null) {
            this.selectedFrame.render(drawContext, mouseX, mouseY, delta);
        }
    }

    // Forwards to children
    @Override
    public boolean mouseClicked(InteractionContext context, double mouseX, double mouseY, int button) {
        return (this.dim.containsCursor(mouseX, mouseY) && super.mouseClicked(context, mouseX, mouseY, button));
    }

    // Scrolls the tab header when over it, else the content
    @Override
    public boolean mouseScrolled(InteractionContext context, double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.selectedFrame != null && this.frameSection.containsCursor(mouseX, mouseY)) {
            return this.selectedFrame.mouseScrolled(context, mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        return super.mouseScrolled(context, mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    public static class Builder {
        private final Map<String, List<Tab<?>>> functions = new LinkedHashMap<>();
        private Dim2i dim;
        private boolean renderOutline;
        private Runnable onSetTab;
        private AtomicReference<TextComponent> tabSectionSelectedTab = new AtomicReference<>(null);
        private AtomicReference<Integer> tabSectionScrollBarOffset = new AtomicReference<>(0);

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

        // Lets the caller populate grouped tabs
        public Builder addTabs(Consumer<Map<String, List<Tab<?>>>> tabs) {
            tabs.accept(this.functions);
            return this;
        }

        // Callback after a tab switch
        public Builder onSetTab(Runnable onSetTab) {
            this.onSetTab = onSetTab;
            return this;
        }

        // Shared ref so the selection survives a rebuild
        public Builder setTabSectionSelectedTab(AtomicReference<TextComponent> tabSectionSelectedTab) {
            this.tabSectionSelectedTab = tabSectionSelectedTab;
            return this;
        }

        // Shared ref so the header scroll survives a rebuild
        public Builder setTabSectionScrollBarOffset(AtomicReference<Integer> tabSectionScrollBarOffset) {
            this.tabSectionScrollBarOffset = tabSectionScrollBarOffset;
            return this;
        }

        // Finalises; needs the font to measure tab labels
        public TabFrame build(DrawContext font) {
            Objects.requireNonNull(this.dim, "Dimension must be specified");

            return new TabFrame(font, this.dim, this.renderOutline, this.functions, this.onSetTab, this.tabSectionSelectedTab, this.tabSectionScrollBarOffset);
        }
    }
}
