package com.bdmajora.impetus.api.options.structure;

import com.bdmajora.impetus.api.OptionPageConstructionEvent;
import com.bdmajora.impetus.api.options.OptionIdentifier;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;

import java.util.ArrayList;
import java.util.List;

public class OptionPage {
    private final OptionIdentifier<Void> id;
    private final TextComponent name;
    private final List<OptionGroup> groups;
    private final List<Option<?>> options;

    public OptionPage(OptionIdentifier<Void> id, TextComponent name, List<OptionGroup> groups) {
        this.id = id;
        this.name = name;
        this.groups = collectExtraGroups(groups);

        List<Option<?>> options = new ArrayList<>();
        for (OptionGroup group : this.groups) {
            options.addAll(group.getOptions());
        }
        this.options = options;
    }

    // Fires the construction event and appends whatever other mods add
    private List<OptionGroup> collectExtraGroups(List<OptionGroup> groups) {
        OptionPageConstructionEvent event = new OptionPageConstructionEvent(this.id, this.name);
        OptionPageConstructionEvent.BUS.post(event);
        List<OptionGroup> extraGroups = event.getAdditionalGroups();
        if (extraGroups.isEmpty()) {
            return groups;
        }
        List<OptionGroup> all = new ArrayList<>(groups.size() + extraGroups.size());
        all.addAll(groups);
        all.addAll(extraGroups);
        return all;
    }

    // Page id
    public OptionIdentifier<Void> getId() {
        return id;
    }

    // In display order
    public List<OptionGroup> getGroups() {
        return this.groups;
    }

    // Flattened across groups
    public List<Option<?>> getOptions() {
        return this.options;
    }

    // Tab label
    public TextComponent getName() {
        return this.name;
    }
}
