package com.bdmajora.impetus.api.config;

import com.bdmajora.impetus.api.OptionGUIConstructionEvent;
import com.bdmajora.impetus.api.OptionGroupConstructionEvent;
import com.bdmajora.impetus.api.OptionPageConstructionEvent;
import com.bdmajora.impetus.api.options.OptionIdentifier;
import com.bdmajora.impetus.api.options.structure.Option;
import com.bdmajora.impetus.api.options.structure.OptionGroup;
import com.bdmajora.impetus.api.options.structure.OptionPage;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

// Entry point for mods to add pages, groups and options to the options screen
public final class ConfigApi {
    // Guards the three registries; held only to append or to read one bucket, never while a supplier runs
    private static final Object LOCK = new Object();

    // Page suppliers in registration order
    private static final Bucket PAGES = new Bucket();

    // Group suppliers by page id; identity keys are exact because OptionIdentifier is interned and matches() compares references
    private static final Map<OptionIdentifier<?>, Bucket> GROUPS = new IdentityHashMap<>();

    // Option suppliers by group id, kept apart from GROUPS since one id can name both a page and a group
    private static final Map<OptionIdentifier<?>, Bucket> OPTIONS = new IdentityHashMap<>();

    // One listener per bus serves every registration, so the buses never grow and a page or group build costs one lookup
    static {
        OptionGUIConstructionEvent.BUS.addListener(ConfigApi::addPages);
        OptionPageConstructionEvent.BUS.addListener(ConfigApi::addGroups);
        OptionGroupConstructionEvent.BUS.addListener(ConfigApi::addOptions);
    }

    // Prevents instantiation
    private ConfigApi() {
    }

    // Adds a page to the options screen each time the screen is built
    public static void registerPage(Supplier<OptionPage> page) {
        Objects.requireNonNull(page, "Page supplier must not be null");
        synchronized (LOCK) {
            PAGES.add(page);
        }
    }

    // Adds a group to the page with the given id each time that page is built
    public static void registerGroup(OptionIdentifier<Void> pageId, Supplier<OptionGroup> group) {
        Objects.requireNonNull(pageId, "Page id must not be null");
        Objects.requireNonNull(group, "Group supplier must not be null");
        synchronized (LOCK) {
            GROUPS.computeIfAbsent(pageId, id -> new Bucket()).add(group);
        }
    }

    // Adds an option to the group with the given id each time that group is built
    public static void registerOption(OptionIdentifier<Void> groupId, Supplier<Option<?>> option) {
        Objects.requireNonNull(groupId, "Group id must not be null");
        Objects.requireNonNull(option, "Option supplier must not be null");
        synchronized (LOCK) {
            OPTIONS.computeIfAbsent(groupId, id -> new Bucket()).add(option);
        }
    }

    // Appends a fresh page from every page supplier, in registration order
    private static void addPages(OptionGUIConstructionEvent event) {
        Supplier<?>[] suppliers;
        int count;
        synchronized (LOCK) {
            suppliers = PAGES.suppliers;
            count = PAGES.size;
        }
        for (int i = 0; i < count; i++) {
            // The cast fails fast on a heap-polluted supplier instead of letting a stranger into the page list
            OptionPage page = (OptionPage) suppliers[i].get();
            if (page == null) {
                throw new NullPointerException("Page supplier returned null");
            }
            event.addPage(page);
        }
    }

    // Appends a fresh group from every supplier registered under the page being built
    private static void addGroups(OptionPageConstructionEvent event) {
        OptionIdentifier<Void> pageId = event.getId();
        Supplier<?>[] suppliers;
        int count;
        synchronized (LOCK) {
            Bucket bucket = GROUPS.get(pageId);
            // Pages nobody registered a group for stop at this lookup
            if (bucket == null) {
                return;
            }
            suppliers = bucket.suppliers;
            count = bucket.size;
        }
        for (int i = 0; i < count; i++) {
            OptionGroup group = (OptionGroup) suppliers[i].get();
            if (group == null) {
                throw new NullPointerException("Group supplier for page " + pageId + " returned null");
            }
            event.addGroup(group);
        }
    }

    // Appends a fresh option from every supplier registered under the group being built
    private static void addOptions(OptionGroupConstructionEvent event) {
        OptionIdentifier<Void> groupId = event.getId();
        Supplier<?>[] suppliers;
        int count;
        synchronized (LOCK) {
            Bucket bucket = OPTIONS.get(groupId);
            // Groups nobody registered an option for stop at this lookup
            if (bucket == null) {
                return;
            }
            suppliers = bucket.suppliers;
            count = bucket.size;
        }
        List<Option<?>> options = event.getOptions();
        for (int i = 0; i < count; i++) {
            Option<?> option = (Option<?>) suppliers[i].get();
            if (option == null) {
                throw new NullPointerException("Option supplier for group " + groupId + " returned null");
            }
            options.add(option);
        }
    }

    // Append-only supplier list; slots below size are never rewritten, so an array and size read under the lock stay valid after it is released
    private static final class Bucket {
        private Supplier<?>[] suppliers = new Supplier<?>[1];
        private int size;

        // Doubles the array when full, so an append is amortised O(1) and a reader's old array keeps its slots
        private void add(Supplier<?> supplier) {
            if (this.size == this.suppliers.length) {
                this.suppliers = Arrays.copyOf(this.suppliers, this.size << 1);
            }
            this.suppliers[this.size++] = supplier;
        }
    }
}
