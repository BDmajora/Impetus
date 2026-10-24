package com.bdmajora.impetus.api;

import com.bdmajora.impetus.api.options.structure.OptionPage;
import com.bdmajora.impetus.api.eventbus.ImpetusEvent;
import com.bdmajora.impetus.api.eventbus.EventHandlerRegistrar;

import java.util.List;
import java.util.Objects;

// Event posted while the options screen is built that gives listeners its list of pages
public final class OptionGUIConstructionEvent extends ImpetusEvent {
    // Listeners for this event
    public static final EventHandlerRegistrar<OptionGUIConstructionEvent> BUS = new EventHandlerRegistrar<>();

    private final List<OptionPage> pages;

    // Stores the screen's page list and rejects it if null
    public OptionGUIConstructionEvent(List<OptionPage> pages) {
        this.pages = Objects.requireNonNull(pages, "Pages must not be null");
    }

    // Returns the screen's page list that listeners change directly
    public List<OptionPage> getPages() {
        return this.pages;
    }

    // Adds a page to the end of the screen's page list and rejects a null page
    public void addPage(OptionPage page) {
        this.pages.add(Objects.requireNonNull(page, "Page must not be null"));
    }
}