package com.bdmajora.impetus.engine.impl.gui;

import lombok.Getter;
import com.bdmajora.impetus.api.OptionGUIConstructionEvent;
import com.bdmajora.impetus.api.options.OptionIdentifier;
import com.bdmajora.impetus.api.options.structure.Option;
import com.bdmajora.impetus.api.options.structure.OptionFlag;
import com.bdmajora.impetus.api.options.structure.OptionGroup;
import com.bdmajora.impetus.api.options.structure.OptionPage;
import com.bdmajora.impetus.api.options.structure.OptionStorage;
import com.bdmajora.impetus.engine.impl.gui.frame.AbstractFrame;
import com.bdmajora.impetus.engine.impl.gui.frame.BasicFrame;
import com.bdmajora.impetus.engine.impl.gui.frame.tab.Tab;
import com.bdmajora.impetus.engine.impl.gui.frame.tab.TabFrame;
import com.bdmajora.impetus.engine.impl.gui.framework.*;
import com.bdmajora.impetus.engine.impl.gui.widgets.FlatButtonWidget;
import com.bdmajora.impetus.engine.impl.gui.widgets.SearchBarWidget;
import com.bdmajora.impetus.engine.impl.util.Dim2i;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class ImpetusVideoOptionsController implements Renderable {
    private static final float ASPECT_RATIO = 5f / 4f;
    private static final int MINIMUM_WIDTH = 550;
    // The search field sits in the top-right corner of the frame, Sodium 0.6 style, rather than spanning the width; wide enough for a few words, never more than a third of the frame
    private static final int SEARCH_BAR_MAX_WIDTH = 190;
    private static final int SEARCH_BAR_HEIGHT = 18;
    private static final int HEADER_GAP = 6;

    private static final AtomicReference<TextComponent> tabFrameSelectedTab = new AtomicReference<>(null);
    private final AtomicReference<Integer> tabFrameScrollBarOffset = new AtomicReference<>(0);
    private final AtomicReference<Integer> optionPageScrollBarOffset = new AtomicReference<>(0);

    private final List<OptionPage> pages = new ArrayList<>();
    private final Runnable onClose;
    private final DrawContext font;

    @Getter
    private AbstractFrame frame;
    private FlatButtonWidget applyButton, closeButton, undoButton;

    // Title/id of the synthesized search-results page
    private static final TextComponent SEARCH_RESULTS_TITLE = TextComponent.translatable("impetus.search_results");
    private static final TextComponent SCREEN_TITLE = TextComponent.translatable("impetus.options.title");

    private SearchBarWidget searchBar;
    private TitleWidget title;
    private String searchQuery = "";
    private @Nullable TextComponent preSearchTabTitle;

    @Getter
    private boolean hasPendingChanges;

    private int width, height;

    // Every option across every page, flattened once since the page list is fixed after the construction event and the per-frame change scan walks it
    private final List<Option<?>> allOptions = new ArrayList<>();

    public ImpetusVideoOptionsController(Runnable onClose, List<OptionPage> pages, DrawContext font) {
        this.onClose = onClose;
        this.pages.addAll(pages);
        OptionGUIConstructionEvent.BUS.post(new OptionGUIConstructionEvent(this.pages));
        for (OptionPage page : this.pages) {
            this.allOptions.addAll(page.getOptions());
        }
        this.font = font;
    }

    // Rebuilds the whole frame tree for a new size
    public void init(int width, int height) {
        this.width = width;
        this.height = height;
        this.frame = this.parentFrameBuilder().build();
    }

    // The root frame: tab area plus the button bar
    protected BasicFrame.Builder parentFrameBuilder() {
        BasicFrame.Builder basicFrameBuilder;

        // Apply aspect ratio clamping on wide enough screens
        int newWidth = this.width;
        if (newWidth > MINIMUM_WIDTH && (float) this.width / (float) this.height > ASPECT_RATIO) {
            newWidth = Math.max(MINIMUM_WIDTH, (int) (this.height * ASPECT_RATIO));
        }

        Dim2i basicFrameDim = new Dim2i((this.width - newWidth) / 2, 0, newWidth, this.height);
        Dim2i tabFrameDim = new Dim2i(basicFrameDim.x() + basicFrameDim.width() / 20 / 2, basicFrameDim.y() + basicFrameDim.height() / 4 / 2, basicFrameDim.width() - (basicFrameDim.width() / 20), basicFrameDim.height() / 4 * 3);

        Dim2i undoButtonDim = new Dim2i(tabFrameDim.getLimitX() - 203, tabFrameDim.getLimitY() + 5, 65, 20);
        Dim2i applyButtonDim = new Dim2i(tabFrameDim.getLimitX() - 134, tabFrameDim.getLimitY() + 5, 65, 20);
        Dim2i closeButtonDim = new Dim2i(tabFrameDim.getLimitX() - 65, tabFrameDim.getLimitY() + 5, 65, 20);

        // Header row above the tab frame: the screen title on the left, the search field right-aligned; recreated on rebuild but preserving query and focus
        int headerY = Math.max(2, tabFrameDim.y() - SEARCH_BAR_HEIGHT - HEADER_GAP);
        int searchWidth = Math.min(SEARCH_BAR_MAX_WIDTH, tabFrameDim.width() / 3);
        Dim2i searchBarDim = new Dim2i(tabFrameDim.getLimitX() - searchWidth, headerY, searchWidth, SEARCH_BAR_HEIGHT);
        boolean searchFocused = this.searchBar != null && this.searchBar.isFocused();
        this.searchBar = new SearchBarWidget(searchBarDim, this.searchQuery, searchFocused, this::setSearchQuery);
        this.title = new TitleWidget(new Dim2i(tabFrameDim.x(), headerY, tabFrameDim.width() - searchWidth - HEADER_GAP, SEARCH_BAR_HEIGHT), SCREEN_TITLE);

        this.undoButton = new FlatButtonWidget(undoButtonDim, TextComponent.translatable("impetus.options.buttons.undo"), this::undoChanges);
        this.applyButton = new FlatButtonWidget(applyButtonDim, TextComponent.translatable("impetus.options.buttons.apply"), this::applyChanges);
        this.closeButton = new FlatButtonWidget(closeButtonDim, TextComponent.translatable("gui.done"), this.onClose);

        basicFrameBuilder = this.parentBasicFrameBuilder(basicFrameDim, tabFrameDim);

        return basicFrameBuilder;
    }

    // Draws the root frame
    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float partialTicks) {
        this.updateControls();
        this.frame.render(drawContext, mouseX, mouseY, partialTicks);
    }

    // Enables Apply and Undo only when something changed
    private void updateControls() {
        boolean hasChanges = false;
        for (Option<?> option : this.allOptions) {
            if (option.hasChanged()) {
                hasChanges = true;
                break;
            }
        }

        this.applyButton.setEnabled(hasChanges);
        this.undoButton.setVisible(hasChanges);
        this.closeButton.setEnabled(!hasChanges);

        this.hasPendingChanges = hasChanges;
    }

    // Writes each changed option, saves every storage, then runs flag side effects
    private void applyChanges() {
        final HashSet<OptionStorage<?>> dirtyStorages = new HashSet<>();
        final EnumSet<OptionFlag> flags = EnumSet.noneOf(OptionFlag.class);

        for (Option<?> option : this.allOptions) {
            if (!option.hasChanged()) {
                continue;
            }
            option.applyChanges();
            flags.addAll(option.getFlags());
            dirtyStorages.add(option.getStorage());
        }

        for (OptionStorage<?> storage : dirtyStorages) {
            storage.save(flags);
        }

        applyFlagSideEffects(Collections.unmodifiableSet(flags));
    }

    // Platform hook: reload renderer, textures or world as flagged
    protected void applyFlagSideEffects(Set<OptionFlag> flags) {

    }

    // Resets every option to its stored value
    private void undoChanges() {
        this.allOptions.forEach(Option::reset);
    }

    // Hides pages with no visible options
    private boolean canShowPage(OptionPage page) {
        return !page.getGroups().isEmpty();
    }

    // Platform hook for pages that are not option pages
    protected void createExtraTabs(Map<String, List<Tab<?>>> tabs) {

    }

    // One tab per page, grouped by owning mod
    private AbstractFrame createTabFrame(Dim2i tabFrameDim) {
        // TabFrame will automatically expand its height to fit all tabs, so the scrollable frame can handle it
        return TabFrame.createBuilder()
                .setDimension(tabFrameDim)
                .shouldRenderOutline(false)
                .setTabSectionScrollBarOffset(tabFrameScrollBarOffset)
                .setTabSectionSelectedTab(tabFrameSelectedTab)
                .addTabs(tabs -> this.pages
                        .stream()
                        .filter(this::canShowPage)
                        .forEach(page -> tabs.computeIfAbsent(page.getId().getModId(), $ -> new ArrayList<>()).add(Tab.from(page, o -> true, optionPageScrollBarOffset)))
                )
                .addTabs(this::createExtraTabs)
                .addTabs(tabs -> {
                    if (!this.searchQuery.isEmpty()) {
                        var resultsPage = this.buildSearchResultsPage();
                        tabs.computeIfAbsent(resultsPage.getId().getModId(), $ -> new ArrayList<>())
                                .add(Tab.from(resultsPage, o -> true, optionPageScrollBarOffset));
                    }
                })
                .onSetTab(() -> {
                    optionPageScrollBarOffset.set(0);
                })
                .build(this.font);
    }

    // Assembles search bar, tabs and buttons
    public BasicFrame.Builder parentBasicFrameBuilder(Dim2i parentBasicFrameDim, Dim2i tabFrameDim) {
        return BasicFrame.createBuilder()
                .setDimension(parentBasicFrameDim)
                .shouldRenderOutline(false)
                // First child so it sees key events before anything else.
                .addChild(dim -> this.searchBar)
                .addChild(dim -> this.title)
                .addChild(parentDim -> this.createTabFrame(tabFrameDim))
                .addChild(dim -> this.undoButton)
                .addChild(dim -> this.applyButton)
                .addChild(dim -> this.closeButton);
    }

    // Live search: while query is non-empty, shows a synthesized "Search Results" tab with matching live option instances (editable in place); clearing restores the previous tab
    private void setSearchQuery(String query) {
        var trimmed = query.trim();

        if (trimmed.equals(this.searchQuery)) {
            return;
        }

        boolean wasSearching = !this.searchQuery.isEmpty();
        boolean searching = !trimmed.isEmpty();
        this.searchQuery = trimmed;

        if (searching && !wasSearching) {
            this.preSearchTabTitle = tabFrameSelectedTab.get();
        }

        if (searching) {
            tabFrameSelectedTab.set(SEARCH_RESULTS_TITLE);
        } else if (wasSearching) {
            tabFrameSelectedTab.set(this.preSearchTabTitle);
        }

        this.optionPageScrollBarOffset.set(0);
        this.frame = this.parentFrameBuilder().build();
    }

    // A synthetic page of every option matching the query, one group per page the matches came from and headed with that page's name, so a hit for "fog" says which Fog it is
    private OptionPage buildSearchResultsPage() {
        var needle = this.searchQuery.toLowerCase(Locale.ROOT);
        List<OptionGroup> groups = new ArrayList<>();

        for (var page : this.pages) {
            OptionGroup.Builder group = null;
            for (var option : page.getOptions()) {
                if (!this.matchesQuery(option, needle)) {
                    continue;
                }
                if (group == null) {
                    group = OptionGroup.createBuilder()
                            .setId(OptionIdentifier.create(page.getId().getModId(), "search_results/" + page.getId().getPath()))
                            .setName(page.getName());
                }
                group.add(option);
            }
            if (group != null) {
                groups.add(group.build());
            }
        }

        return new OptionPage(OptionIdentifier.create("impetus", "search_results"), SEARCH_RESULTS_TITLE, groups);
    }

    // A static caption in the header row; the mod accent colour so it pairs with the sidebar headings
    private static final class TitleWidget extends com.bdmajora.impetus.engine.impl.gui.widgets.AbstractWidget {
        private final Dim2i dim;
        private final TextComponent text;

        TitleWidget(Dim2i dim, TextComponent text) {
            this.dim = dim;
            this.text = text;
        }

        @Override
        public void render(DrawContext drawContext, int mouseX, int mouseY, float delta) {
            drawContext.drawString(this.text, this.dim.x(), this.dim.getCenterY() - drawContext.lineHeight() / 2,
                    drawContext.getModAccentColor("impetus"));
        }

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            return false;
        }
    }

    // Case-insensitive match on name and tooltip
    private boolean matchesQuery(Option<?> option, String needle) {
        var name = this.font.extractString(option.getName());

        if (name.toLowerCase(Locale.ROOT).contains(needle)) {
            return true;
        }

        var tooltip = option.getTooltip();

        return tooltip != null && this.font.extractString(tooltip).toLowerCase(Locale.ROOT).contains(needle);
    }
}
