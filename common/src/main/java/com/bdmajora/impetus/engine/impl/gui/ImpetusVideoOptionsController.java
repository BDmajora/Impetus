package com.bdmajora.impetus.engine.impl.gui;

import lombok.Getter;
import com.bdmajora.impetus.api.OptionGUIConstructionEvent;
import com.bdmajora.impetus.api.options.OptionIdentifier;
import com.bdmajora.impetus.api.options.control.Control;
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
import com.bdmajora.impetus.engine.impl.notification.ImpetusNotifications;
import com.bdmajora.impetus.engine.impl.util.Dim2i;
import com.bdmajora.impetus.engine.impl.util.FailureText;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class ImpetusVideoOptionsController implements Renderable {
    private static final Logger LOGGER = LogManager.getLogger();
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

    // Every option across every page, flattened once since the page list is fixed after the construction event and the per-frame change scan walks it; an option whose own code throws is taken out
    private final List<Option<?>> allOptions = new ArrayList<>();
    // Options taken out after their own code threw, by identity, so a search passes them over too
    private final Set<Option<?>> dropped = Collections.newSetFromMap(new IdentityHashMap<>());
    // Set when an option was dropped, so the next frame rebuilds the rows with its placeholder
    private boolean rebuildPending;

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

    // Draws the root frame, first rebuilding it when an option was dropped since the last frame
    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float partialTicks) {
        this.updateControls();
        if (this.rebuildPending) {
            this.rebuildPending = false;
            this.frame = this.parentFrameBuilder().build();
        }
        this.frame.render(drawContext, mouseX, mouseY, partialTicks);
    }

    // Enables Apply and Undo only when something changed
    private void updateControls() {
        boolean hasChanges = false;
        // By index with no copy, since this runs every frame; a dropped option restarts the scan, which only ever shortens the list
        for (int i = 0; i < this.allOptions.size(); i++) {
            Option<?> option = this.allOptions.get(i);
            try {
                if (option.hasChanged()) {
                    hasChanges = true;
                    break;
                }
            } catch (VirtualMachineError fatal) {
                throw fatal;
            } catch (Throwable broken) {
                this.drop(option, broken, "checking for changes");
                i = -1;
            }
        }

        this.applyButton.setEnabled(hasChanges);
        this.undoButton.setVisible(hasChanges);
        this.closeButton.setEnabled(!hasChanges);

        this.hasPendingChanges = hasChanges;
    }

    // Writes each changed option, saves every storage, then runs flag side effects
    private void applyChanges() {
        // By identity, so each storage object is saved once and its own equals and hashCode are never asked
        final Set<OptionStorage<?>> dirtyStorages = Collections.newSetFromMap(new IdentityHashMap<>());
        final EnumSet<OptionFlag> flags = EnumSet.noneOf(OptionFlag.class);
        // One option's flags, copied out before it writes so that flags it cannot give leave nothing behind
        final EnumSet<OptionFlag> optionFlags = EnumSet.noneOf(OptionFlag.class);

        // Over a copy, as an option that throws is dropped from the list as the loop goes
        for (Option<?> option : new ArrayList<>(this.allOptions)) {
            if (this.dropped.contains(option)) {
                continue;
            }
            try {
                if (!option.hasChanged()) {
                    continue;
                }
                // Read before writing, so an option that fails here or answers null is left as it was
                optionFlags.clear();
                optionFlags.addAll(Objects.requireNonNull(option.getFlags(), "Option has no flags"));
                OptionStorage<?> storage = Objects.requireNonNull(option.getStorage(), "Option has no storage");
                option.applyChanges();
                // Neither can throw, so a written option always has its flags and storage counted
                flags.addAll(optionFlags);
                dirtyStorages.add(storage);
            } catch (VirtualMachineError fatal) {
                throw fatal;
            } catch (Throwable broken) {
                this.drop(option, broken, "applying");
            }
        }

        // Read-only, so a storage cannot change what the others and the side effects are given
        Set<OptionFlag> applied = Collections.unmodifiableSet(flags);
        for (OptionStorage<?> storage : dirtyStorages) {
            try {
                storage.save(applied);
            } catch (VirtualMachineError fatal) {
                throw fatal;
            } catch (Throwable broken) {
                // A storage that cannot save leaves the others to save theirs, and is tried again on the next Apply
                LOGGER.error("Saving options to {} failed", storage.getClass().getName(), broken);
                ImpetusNotifications.error("Saving options failed", noteFailure(broken));
            }
        }

        applyFlagSideEffects(applied);
    }

    // Platform hook: reload renderer, textures or world as flagged; overrides call this first, which says when only a restart can apply a change
    protected void applyFlagSideEffects(Set<OptionFlag> flags) {
        // The same title and line each time, so a later Apply refreshes this toast rather than stacking another
        if (flags.contains(OptionFlag.REQUIRES_GAME_RESTART)) {
            ImpetusNotifications.warn("Restart required", "The game must be restarted to apply one or more video settings.");
        }
    }

    // Resets every option to its stored value
    private void undoChanges() {
        // Over a copy, as an option that throws is dropped from the list as the loop goes
        for (Option<?> option : new ArrayList<>(this.allOptions)) {
            if (this.dropped.contains(option)) {
                continue;
            }
            try {
                option.reset();
            } catch (VirtualMachineError fatal) {
                throw fatal;
            } catch (Throwable broken) {
                this.drop(option, broken, "undoing changes");
            }
        }
    }

    // Takes an option whose own code threw out of every later scan, search, Apply and Undo, reports it once and has its rows rebuilt as placeholders on the next frame
    private void drop(Option<?> option, Throwable failure, String during) {
        if (!this.dropped.add(option)) {
            return;
        }
        // Every occurrence, as one option can sit on more than one page
        this.allOptions.removeIf(listed -> listed == option);
        Control.reportBroken(option, during, failure);
        this.rebuildPending = true;
    }

    // Restores the interrupt flag after an interruption and returns the first line of the failure for a toast, cut to its limit
    private static String noteFailure(Throwable failure) {
        if (failure instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
        return FailureText.of(failure);
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
                // First child, so Tab reaches it before the sidebar and the rows
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

    // Case-insensitive match on name and tooltip; an option that cannot be read is no match, and is dropped like any broken option
    private boolean matchesQuery(Option<?> option, String needle) {
        if (this.dropped.contains(option)) {
            return false;
        }
        try {
            var name = this.font.extractString(option.getName());

            if (name.toLowerCase(Locale.ROOT).contains(needle)) {
                return true;
            }

            var tooltip = option.getTooltip();

            return tooltip != null && this.font.extractString(tooltip).toLowerCase(Locale.ROOT).contains(needle);
        } catch (VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable broken) {
            this.drop(option, broken, "searching");
            return false;
        }
    }
}
