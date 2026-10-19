package com.bdmajora.impetus.api.config;

import com.bdmajora.impetus.api.options.OptionIdentifier;
import com.bdmajora.impetus.api.options.structure.OptionGroup;
import com.bdmajora.impetus.api.options.structure.OptionPage;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

// Collects a page's id and name and groups until build() is called
public final class ConfigPageBuilder {
    private final OptionIdentifier<Void> id;
    private final TextComponent name;
    private final List<OptionGroup> groups = new ArrayList<>();
    // Copy of the groups given to the last built page or null after a group is added
    private List<OptionGroup> built = List.of();

    // Stores the page id and name and rejects either if null
    ConfigPageBuilder(OptionIdentifier<Void> id, TextComponent name) {
        this.id = Objects.requireNonNull(id, "Page id must not be null");
        this.name = Objects.requireNonNull(name, "Page name must not be null");
    }

    // Adds a group to the end of the page and returns the builder for chaining
    public ConfigPageBuilder addGroup(OptionGroup group) {
        Objects.requireNonNull(group, "Group must not be null");
        // Rejects the same group instance being added to the page twice
        for (OptionGroup added : this.groups) {
            if (added == group) {
                throw new IllegalArgumentException("Group '" + group.getId() + "' is already on page '" + this.id + "'");
            }
        }
        this.groups.add(group);
        this.built = null;
        return this;
    }

    // Creates the OptionPage and reuses the last copy of the groups unless a group was added since
    public OptionPage build() {
        List<OptionGroup> groups = this.built;
        if (groups == null) {
            groups = List.copyOf(this.groups);
            this.built = groups;
        }
        return new OptionPage(this.id, this.name, groups);
    }
}