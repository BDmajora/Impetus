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

// Controller of the video options screen.
public class ImpetusVideoOptionsController implements Renderable {
    // Logger of save failures.
    private static final Logger LOGGER = LogManager.getLogger();
    // Widest shape of the screen.
    private static final float ASPECT_RATIO = 5f / 4f;
    // Narrowest width that is not clamped.
    private static final int MINIMUM_WIDTH = 550;
    // Widest search field.
    private static final int SEARCH_BAR_MAX_WIDTH = 190;
    // Height of the search field.
    private static final int SEARCH_BAR_HEIGHT = 18;
    // Space between the title and the search field.
    private static final int HEADER_GAP = 6;

    // Title of the selected tab shared by all screens.
    private static final AtomicReference<TextComponent> tabFrameSelectedTab = new AtomicReference<>(null);
    // Scroll offset of the sidebar.
    private final AtomicReference<Integer> tabFrameScrollBarOffset = new AtomicReference<>(0);
    // Scroll offset of the page.
    private final AtomicReference<Integer> optionPageScrollBarOffset = new AtomicReference<>(0);

    // Pages of the screen.
    private final List<OptionPage> pages = new ArrayList<>();
    // Action that closes the screen.
    private final Runnable onClose;
    // Font used for measuring and searching.
    private final DrawContext font;

    // Root frame.
    @Getter
    private AbstractFrame frame;
    // Buttons of the screen.
    private FlatButtonWidget applyButton, closeButton, undoButton;

    // Title of the search results page.
    private static final TextComponent SEARCH_RESULTS_TITLE = TextComponent.translatable("impetus.search_results");
    // Title of the screen.
    private static final TextComponent SCREEN_TITLE = TextComponent.translatable("impetus.options.title");

    // Search field.
    private SearchBarWidget searchBar;
    // Title caption.
    private TitleWidget title;
    // Current search text.
    private String searchQuery = "";
    // Tab selected before the search started.
    private @Nullable TextComponent preSearchTabTitle;

    // Unapplied change state of the options.
    @Getter
    private boolean hasPendingChanges;

    // Size of the screen.
    private int width, height;

    // Every option of every page.
    private final List<Option<?>> allOptions = new ArrayList<>();
    // Options that were removed after they threw.
    private final Set<Option<?>> dropped = Collections.newSetFromMap(new IdentityHashMap<>());
    // Pending rebuild state of the rows.
    private boolean rebuildPending;

    // Creates the controller and rejects null arguments.
    public ImpetusVideoOptionsController(Runnable onClose, List<OptionPage> pages, DrawContext font) {
        this.onClose = Objects.requireNonNull(onClose, "onClose");
        this.font = Objects.requireNonNull(font, "font");
        this.pages.addAll(pages);
        OptionGUIConstructionEvent.BUS.post(new OptionGUIConstructionEvent(this.pages));
        for (OptionPage page : this.pages) {
            this.allOptions.addAll(page.getOptions());
        }
    }

    // Rebuilds the frames for a new size.
    public void init(int width, int height) {
        this.width = width;
        this.height = height;
        this.frame = this.parentFrameBuilder().build();
    }

    // Creates the builder of the root frame.
    protected BasicFrame.Builder parentFrameBuilder() {
        BasicFrame.Builder basicFrameBuilder;

        // Limits the width of wide screens
        int newWidth = this.width;
        if (newWidth > MINIMUM_WIDTH && (float) this.width / (float) this.height > ASPECT_RATIO) {
            newWidth = Math.max(MINIMUM_WIDTH, (int) (this.height * ASPECT_RATIO));
        }

        Dim2i basicFrameDim = new Dim2i((this.width - newWidth) / 2, 0, newWidth, this.height);
        Dim2i tabFrameDim = new Dim2i(basicFrameDim.x() + basicFrameDim.width() / 20 / 2, basicFrameDim.y() + basicFrameDim.height() / 4 / 2, basicFrameDim.width() - (basicFrameDim.width() / 20), basicFrameDim.height() / 4 * 3);

        Dim2i undoButtonDim = new Dim2i(tabFrameDim.getLimitX() - 203, tabFrameDim.getLimitY() + 5, 65, 20);
        Dim2i applyButtonDim = new Dim2i(tabFrameDim.getLimitX() - 134, tabFrameDim.getLimitY() + 5, 65, 20);
        Dim2i closeButtonDim = new Dim2i(tabFrameDim.getLimitX() - 65, tabFrameDim.getLimitY() + 5, 65, 20);

        // Creates the header with the title and the search field
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

    // Draws the root frame and rebuilds it first when an option was dropped.
    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float partialTicks) {
        if (this.frame == null) {
            throw new IllegalStateException("Screen is not initialised");
        }
        this.updateControls();
        if (this.rebuildPending) {
            this.rebuildPending = false;
            this.frame = this.parentFrameBuilder().build();
        }
        this.frame.render(drawContext, mouseX, mouseY, partialTicks);
    }

    // Enables the buttons that depend on pending changes.
    private void updateControls() {
        boolean hasChanges = false;
        List<Option<?>> options = this.allOptions;
        for (int i = 0; i < options.size(); i++) {
            Option<?> option = options.get(i);
            try {
                if (option.hasChanged()) {
                    hasChanges = true;
                    break;
                }
            } catch (VirtualMachineError fatal) {
                throw fatal;
            } catch (Throwable broken) {
                // Counts the earlier copies of the option that the drop removes
                int earlier = 0;
                for (int j = 0; j < i; j++) {
                    if (options.get(j) == option) {
                        earlier++;
                    }
                }
                this.drop(option, broken, "checking for changes");
                // Resumes at the option that moved into the dropped position
                i -= earlier + 1;
            }
        }

        this.applyButton.setEnabled(hasChanges);
        this.undoButton.setVisible(hasChanges);
        this.closeButton.setEnabled(!hasChanges);

        this.hasPendingChanges = hasChanges;
    }

    // Writes every changed option and saves the storages and runs the flag effects.
    private void applyChanges() {
        // Storages with changes
        final Set<OptionStorage<?>> dirtyStorages = Collections.newSetFromMap(new IdentityHashMap<>());
        final EnumSet<OptionFlag> flags = EnumSet.noneOf(OptionFlag.class);
        // Flags of the option being written
        final EnumSet<OptionFlag> optionFlags = EnumSet.noneOf(OptionFlag.class);

        // Walks a copy of the options
        for (Option<?> option : new ArrayList<>(this.allOptions)) {
            if (this.dropped.contains(option)) {
                continue;
            }
            try {
                if (!option.hasChanged()) {
                    continue;
                }
                // Reads the flags and storage before writing
                optionFlags.clear();
                optionFlags.addAll(Objects.requireNonNull(option.getFlags(), "Option has no flags"));
                OptionStorage<?> storage = Objects.requireNonNull(option.getStorage(), "Option has no storage");
                option.applyChanges();
                // Counts the written option
                flags.addAll(optionFlags);
                dirtyStorages.add(storage);
            } catch (VirtualMachineError fatal) {
                throw fatal;
            } catch (Throwable broken) {
                this.drop(option, broken, "applying");
            }
        }

        // Shares the flags read only
        Set<OptionFlag> applied = Collections.unmodifiableSet(flags);
        for (OptionStorage<?> storage : dirtyStorages) {
            try {
                storage.save(applied);
            } catch (VirtualMachineError fatal) {
                throw fatal;
            } catch (Throwable broken) {
                // Continues with the other storages
                LOGGER.error("Saving options to {} failed", storage.getClass().getName(), broken);
                ImpetusNotifications.error("Saving options failed", noteFailure(broken));
            }
        }

        applyFlagSideEffects(applied);
    }

    // Runs the effects of the applied flags and warns when a restart is needed.
    protected void applyFlagSideEffects(Set<OptionFlag> flags) {
        if (flags.contains(OptionFlag.REQUIRES_GAME_RESTART)) {
            ImpetusNotifications.warn("Restart required", "The game must be restarted to apply one or more video settings.");
        }
    }

    // Resets every option to its stored value.
    private void undoChanges() {
        // Walks a copy of the options
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

    // Removes a failed option from later scans and reports it once.
    private void drop(Option<?> option, Throwable failure, String during) {
        if (!this.dropped.add(option)) {
            return;
        }
        // Removes every occurrence
        this.allOptions.removeIf(listed -> listed == option);
        Control.reportBroken(option, during, failure);
        this.rebuildPending = true;
    }

    // Returns the text of a failure and restores the interrupt flag.
    private static String noteFailure(Throwable failure) {
        if (failure instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
        return FailureText.of(failure);
    }

    // Returns whether the page has groups.
    private boolean canShowPage(OptionPage page) {
        return !page.getGroups().isEmpty();
    }

    // Adds tabs that are not option pages.
    protected void createExtraTabs(Map<String, List<Tab<?>>> tabs) {

    }

    // Creates the frame with one tab per page.
    private AbstractFrame createTabFrame(Dim2i tabFrameDim) {
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

    // Creates the builder of the root frame with the search field and tabs and buttons.
    public BasicFrame.Builder parentBasicFrameBuilder(Dim2i parentBasicFrameDim, Dim2i tabFrameDim) {
        return BasicFrame.createBuilder()
                .setDimension(parentBasicFrameDim)
                .shouldRenderOutline(false)
                // Adds the search field before the other children
                .addChild(dim -> this.searchBar)
                .addChild(dim -> this.title)
                .addChild(parentDim -> this.createTabFrame(tabFrameDim))
                .addChild(dim -> this.undoButton)
                .addChild(dim -> this.applyButton)
                .addChild(dim -> this.closeButton);
    }

    // Applies a search query and shows or hides the search results tab.
    private void setSearchQuery(String query) {
        var trimmed = query == null ? "" : query.trim();

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

    // Creates a page of the options that match the query with one group per source page.
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

    // Caption in the header row.
    private static final class TitleWidget extends com.bdmajora.impetus.engine.impl.gui.widgets.AbstractWidget {
        // Bounds of the caption.
        private final Dim2i dim;
        // Text of the caption.
        private final TextComponent text;

        // Creates a caption.
        TitleWidget(Dim2i dim, TextComponent text) {
            this.dim = dim;
            this.text = text;
        }

        // Draws the caption.
        @Override
        public void render(DrawContext drawContext, int mouseX, int mouseY, float delta) {
            drawContext.drawString(this.text, this.dim.x(), this.dim.getCenterY() - drawContext.lineHeight() / 2,
                    drawContext.getModAccentColor("impetus"));
        }

        // Returns false for every point.
        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            return false;
        }
    }

    // Returns whether the name or tooltip of an option contains the text and drops an unreadable option.
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
