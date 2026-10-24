package com.bdmajora.impetus.api;

import com.bdmajora.impetus.api.options.structure.OptionGroup;
import com.bdmajora.impetus.api.eventbus.ImpetusEvent;
import com.bdmajora.impetus.api.eventbus.EventHandlerRegistrar;
import com.bdmajora.impetus.api.options.OptionIdentifier;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.RandomAccess;

// Event posted while an option page is built that collects extra groups from listeners
public final class OptionPageConstructionEvent extends ImpetusEvent {
    // Listeners for this event
    public static final EventHandlerRegistrar<OptionPageConstructionEvent> BUS = new EventHandlerRegistrar<>();

    private final OptionIdentifier<Void> id;
    private final TextComponent name;
    // Groups added by listeners
    private final Groups groups = new Groups();

    // Stores the page id and name and rejects either if null
    public OptionPageConstructionEvent(OptionIdentifier<Void> id, TextComponent name) {
        this.id = Objects.requireNonNull(id, "Page id must not be null");
        this.name = Objects.requireNonNull(name, "Page name must not be null");
    }

    // Adds a group after the groups added before it and rejects a null group
    public void addGroup(OptionGroup group) {
        if (group == null) {
            throw new NullPointerException("A listener added a null group to page " + this.id);
        }
        this.groups.append(group);
    }

    // Returns the same live unmodifiable view of the added groups in the order they were added
    public List<OptionGroup> getAdditionalGroups() {
        return this.groups;
    }

    // Returns the id of the page being built
    public OptionIdentifier<Void> getId() {
        return this.id;
    }

    // Returns the page name
    public TextComponent getTranslationKey() {
        return this.name;
    }

    // List of added groups that allows only reads through the List methods
    private static final class Groups extends AbstractList<OptionGroup> implements RandomAccess {
        // Shared empty array every list starts from
        private static final OptionGroup[] NONE = new OptionGroup[0];

        // Backing array and number of groups held
        private OptionGroup[] items = NONE;
        private int size;

        // Adds a group and grows the array to twice its length plus one when full
        private void append(OptionGroup group) {
            OptionGroup[] items = this.items;
            int size = this.size;
            if (size == items.length) {
                this.items = items = Arrays.copyOf(items, (size << 1) | 1);
            }
            items[size] = group;
            this.size = size + 1;
            // Increments the modification count for open iterators and sub lists
            this.modCount++;
        }

        // Returns the group at the index after checking it is in range
        @Override
        public OptionGroup get(int index) {
            return this.items[Objects.checkIndex(index, this.size)];
        }

        // Returns the number of groups added
        @Override
        public int size() {
            return this.size;
        }
    }
}