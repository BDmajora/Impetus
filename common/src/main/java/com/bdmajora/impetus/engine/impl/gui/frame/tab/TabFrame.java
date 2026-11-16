package com.bdmajora.impetus.engine.impl.gui.frame.tab;

import com.bdmajora.impetus.engine.api.util.ColorARGB;
import com.bdmajora.impetus.engine.impl.gui.framework.DrawContext;
import com.bdmajora.impetus.engine.impl.gui.framework.Focusable;
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
import java.util.stream.Stream;

// Sidebar of page groups beside the frame of the selected page.
public class TabFrame extends AbstractFrame {
    // Indent of tab labels.
    private static final int TAB_OPTION_INDENT = 5;
    // Height of a tab row.
    private static final int TAB_HEIGHT = 18;

    // Ids of folded groups.
    private static final Set<String> COLLAPSED_GROUPS = new HashSet<>();

    // Bounds of the sidebar.
    private Dim2i tabSection;
    // Bounds of the page frame.
    private final Dim2i frameSection;
    // Tabs by group id.
    private final Map<String, List<Tab<?>>> tabs;
    // Accent color of each group.
    private final Map<String, Integer> modAccentColors;
    // Callback after a tab switch.
    private final Runnable onSetTab;
    // Title of the selected tab.
    private final AtomicReference<TextComponent> tabSectionSelectedTab;
    // Scroll offset of the sidebar.
    private final AtomicReference<Integer> tabSectionScrollBarOffset;
    // Selected tab.
    private Tab<?> selectedTab;
    // Frame of the selected tab.
    private AbstractFrame selectedFrame;
    // Scrollable wrapper of the sidebar.
    private ScrollableFrame sidebarFrame;
    // Current sidebar.
    private TabSidebarFrame sidebar;

    // Creates a tab frame and rejects null tabs or null draw context and uses fresh references for null ones.
    public TabFrame(DrawContext drawContext, Dim2i dim, boolean renderOutline, Map<String, List<Tab<?>>> tabs, Runnable onSetTab, AtomicReference<TextComponent> tabSectionSelectedTab, AtomicReference<Integer> tabSectionScrollBarOffset) {
        super(dim, renderOutline);
        Objects.requireNonNull(drawContext, "drawContext");
        Objects.requireNonNull(tabs, "tabs");
        tabSectionSelectedTab = tabSectionSelectedTab != null ? tabSectionSelectedTab : new AtomicReference<>(null);
        tabSectionScrollBarOffset = tabSectionScrollBarOffset != null ? tabSectionScrollBarOffset : new AtomicReference<>(0);
        Map<String, List<Tab<?>>> groups = new LinkedHashMap<>();
        tabs.forEach((id, list) -> groups.put(id, List.copyOf(list)));
        this.tabs = Collections.unmodifiableMap(groups);
        Map<String, Integer> accents = new LinkedHashMap<>();
        this.tabs.keySet().forEach(id -> accents.put(id, drawContext.getModAccentColor(id)));
        this.modAccentColors = accents;
        Optional<Integer> result = Stream.concat(
                // Adds the icon and its padding and the fold marker
                tabs.keySet().stream().map(id -> {
                    int headerTextOffset = 5 + 20 + 5;
                    return drawContext.getStringWidth(drawContext.getFriendlyModName(id)) + headerTextOffset + TabHeaderWidget.MARKER_WIDTH;
                }),
                tabStream().map(tab -> drawContext.getStringWidth(tab.title()) + TAB_OPTION_INDENT)
        ).max(Integer::compareTo);

        int sidebarWidth = result.map(integer -> integer + (24)).orElseGet(() -> (int) (this.dim.width() * 0.35D));
        this.tabSection = new Dim2i(this.dim.x(), this.dim.y(), Math.max(0, Math.min(sidebarWidth, this.dim.width())), this.dim.height());
        this.frameSection = new Dim2i(this.tabSection.getLimitX(), this.dim.y(), this.dim.width() - this.tabSection.width(), this.dim.height());

        this.onSetTab = onSetTab;
        this.tabSectionSelectedTab = tabSectionSelectedTab;
        this.tabSectionScrollBarOffset = tabSectionScrollBarOffset;

        if (this.tabSectionSelectedTab.get() != null) {
            this.selectedTab = tabStream().filter(tab -> tab.title().equals(this.tabSectionSelectedTab.get())).findAny().orElse(null);
        }

        this.buildFrame();
    }

    // Returns every tab of every group.
    private Stream<Tab<?>> tabStream() {
        return this.tabs.values().stream().flatMap(Collection::stream);
    }

    // Creates a builder.
    public static Builder createBuilder() {
        return new Builder();
    }

    // Selects a tab and unfolds its group and rebuilds the frames.
    public void setTab(Tab<?> tab) {
        Objects.requireNonNull(tab, "tab");
        this.selectedTab = tab;
        this.tabSectionSelectedTab.set(this.selectedTab.title());
        COLLAPSED_GROUPS.remove(this.groupOf(tab));
        if (this.onSetTab != null) {
            this.onSetTab.run();
        }
        this.buildFrame();
    }

    // Returns the group id of a tab matched by identity or null.
    private String groupOf(Tab<?> tab) {
        for (Map.Entry<String, List<Tab<?>>> entry : this.tabs.entrySet()) {
            for (Tab<?> listed : entry.getValue()) {
                if (listed == tab) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    // Folds or unfolds a group and rebuilds the frames.
    private void toggleGroup(String groupId) {
        if (!COLLAPSED_GROUPS.remove(groupId)) {
            COLLAPSED_GROUPS.add(groupId);
        }
        this.buildFrame();
    }

    // Returns the sidebar height for the current fold state.
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

    // Frame of the group headers and tab buttons.
    class TabSidebarFrame extends AbstractFrame {
        // Creates the sidebar.
        TabSidebarFrame(Dim2i dim) {
            super(dim, false);
        }

        // Rebuilds the headers and tab buttons.
        @Override
        public void buildFrame() {
            this.children.clear();
            this.drawable.clear();
            this.controlElements.clear();

            rebuildTabs();

            super.buildFrame();
        }

        // Creates the headers and the buttons of unfolded groups.
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
                        // Returns the label indent of a tab button.
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

    // Rebuilds the sidebar and the page frame.
    @Override
    public void buildFrame() {
        this.children.clear();
        this.drawable.clear();
        this.controlElements.clear();

        if (this.selectedTab == null) {
            if (!this.tabs.isEmpty()) {
                // Selects the first tab
                this.selectedTab = tabStream().findFirst().orElseThrow();
            }
        }

        // Remembers the focused sidebar entry
        int focusedEntry = this.sidebar != null ? this.sidebar.interactableChildren().toList().indexOf(this.sidebar.getFocusedChild()) : -1;

        // Scrolls the sidebar when it is taller than the frame
        int contentHeight = this.sidebarContentHeight();
        Dim2i sidebarInner = contentHeight > this.tabSection.height() ? this.tabSection.withHeight(contentHeight) : this.tabSection;

        this.sidebar = new TabSidebarFrame(sidebarInner);
        this.sidebarFrame = ScrollableFrame.createBuilder()
                .setDimension(this.tabSection)
                .setFrame(this.sidebar)
                .setVerticalScrollBarOffset(this.tabSectionScrollBarOffset)
                .build();
        if (focusedEntry >= 0 && this.sidebar.interactableChildren().toList().get(focusedEntry) instanceof Focusable entry) {
            entry.setFocused(true);
        }

        this.children.add(this.sidebarFrame);

        this.rebuildTabFrame();

        super.buildFrame();
    }

    // Creates the frame of the selected tab and clears the frame of a tab without one.
    private void rebuildTabFrame() {
        this.selectedFrame = null;
        if (this.selectedTab == null) return;
        AbstractFrame frame = this.selectedTab.createFrame(this.frameSection);
        if (frame != null) {
            this.selectedFrame = frame;
            frame.buildFrame();
            this.children.add(frame);
        }
    }

    // Draws the sidebar and then the page frame.
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

    // Forwards a click inside the frame to the children.
    @Override
    public boolean mouseClicked(InteractionContext context, double mouseX, double mouseY, int button) {
        return (this.dim.containsCursor(mouseX, mouseY) && super.mouseClicked(context, mouseX, mouseY, button));
    }

    // Scrolls the page frame over its area and the sidebar elsewhere.
    @Override
    public boolean mouseScrolled(InteractionContext context, double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.selectedFrame != null && this.frameSection.containsCursor(mouseX, mouseY)) {
            return this.selectedFrame.mouseScrolled(context, mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        return super.mouseScrolled(context, mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    // Collects the settings of a tab frame.
    public static class Builder {
        // Tabs by group id.
        private final Map<String, List<Tab<?>>> functions = new LinkedHashMap<>();
        // Bounds of the frame.
        private Dim2i dim;
        // Outline state of the frame.
        private boolean renderOutline;
        // Callback after a tab switch.
        private Runnable onSetTab;
        // Title of the selected tab.
        private AtomicReference<TextComponent> tabSectionSelectedTab = new AtomicReference<>(null);
        // Scroll offset of the sidebar.
        private AtomicReference<Integer> tabSectionScrollBarOffset = new AtomicReference<>(0);

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

        // Lets a consumer fill the tab groups.
        public Builder addTabs(Consumer<Map<String, List<Tab<?>>>> tabs) {
            tabs.accept(this.functions);
            return this;
        }

        // Stores the callback after a tab switch.
        public Builder onSetTab(Runnable onSetTab) {
            this.onSetTab = onSetTab;
            return this;
        }

        // Stores the reference of the selected title.
        public Builder setTabSectionSelectedTab(AtomicReference<TextComponent> tabSectionSelectedTab) {
            this.tabSectionSelectedTab = tabSectionSelectedTab;
            return this;
        }

        // Stores the reference of the sidebar offset.
        public Builder setTabSectionScrollBarOffset(AtomicReference<Integer> tabSectionScrollBarOffset) {
            this.tabSectionScrollBarOffset = tabSectionScrollBarOffset;
            return this;
        }

        // Creates the frame and rejects missing bounds.
        public TabFrame build(DrawContext font) {
            Objects.requireNonNull(this.dim, "Dimension must be specified");

            return new TabFrame(font, this.dim, this.renderOutline, this.functions, this.onSetTab, this.tabSectionSelectedTab, this.tabSectionScrollBarOffset);
        }
    }
}
