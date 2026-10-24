package com.bdmajora.impetus.api.options.structure;

import com.bdmajora.impetus.api.OptionPageConstructionEvent;
import com.bdmajora.impetus.api.options.OptionIdentifier;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;

import java.util.List;
import java.util.Objects;

// Page of option groups shown as one tab on the options screen
public final class OptionPage {
    private final OptionIdentifier<Void> id;
    private final TextComponent name;
    private final List<OptionGroup> groups;
    private final List<Option<?>> options;

    // Stores the id and name and copies the groups before adding listener groups and flattening the options
    public OptionPage(OptionIdentifier<Void> id, TextComponent name, List<OptionGroup> groups) {
        this.id = Objects.requireNonNull(id, "Page id must not be null");
        this.name = Objects.requireNonNull(name, "Page name must not be null");
        this.groups = appendListenerGroups(copyOwnGroups(groups));
        this.options = flattenOptions(this.groups);
    }

    // Returns an unmodifiable copy of the groups and rejects a null list or group
    private List<OptionGroup> copyOwnGroups(List<OptionGroup> groups) {
        Objects.requireNonNull(groups, "Page groups must not be null");
        try {
            return List.copyOf(groups);
        } catch (NullPointerException e) {
            throw new NullPointerException("A null group was given to page " + this.id);
        }
    }

    // Posts the construction event and returns the groups with any listener groups added after them
    private List<OptionGroup> appendListenerGroups(List<OptionGroup> own) {
        OptionPageConstructionEvent event = new OptionPageConstructionEvent(this.id, this.name);
        OptionPageConstructionEvent.BUS.post(event);
        List<OptionGroup> extra = event.getAdditionalGroups();
        int extraCount = extra.size();
        if (extraCount == 0) {
            return own;
        }
        int ownCount = own.size();
        // Copies both sets of groups into one array sized for all of them; the event already refused any null a listener added
        OptionGroup[] all = own.toArray(new OptionGroup[ownCount + extraCount]);
        for (int i = 0; i < extraCount; i++) {
            all[ownCount + i] = extra.get(i);
        }
        return List.of(all);
    }

    // Returns every option across the groups in group order as an unmodifiable list
    private List<Option<?>> flattenOptions(List<OptionGroup> groups) {
        int count = groups.size();
        // Returns the options of a single group
        if (count == 1) {
            return List.copyOf(groups.get(0).getOptions());
        }
        // Counts the options as a long and rejects a total above the list size limit
        long total = 0;
        for (int i = 0; i < count; i++) {
            total += groups.get(i).getOptions().size();
        }
        if (total > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Page " + this.id + " holds " + total + " options, more than a list can");
        }
        if (total == 0) {
            return List.of();
        }
        Option<?>[] all = new Option<?>[(int) total];
        int at = 0;
        for (int i = 0; i < count; i++) {
            for (Option<?> option : groups.get(i).getOptions()) {
                all[at++] = option;
            }
        }
        return List.of(all);
    }

    // Returns the page id
    public OptionIdentifier<Void> getId() {
        return id;
    }

    // Returns the unmodifiable list of groups in display order
    public List<OptionGroup> getGroups() {
        return this.groups;
    }

    // Returns the unmodifiable list of options across all groups
    public List<Option<?>> getOptions() {
        return this.options;
    }

    // Returns the tab label
    public TextComponent getName() {
        return this.name;
    }
}