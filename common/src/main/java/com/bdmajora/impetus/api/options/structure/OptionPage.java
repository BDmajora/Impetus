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

    // A null group, from the caller or a listener, throws NullPointerException here, as it has no options to draw
    public OptionPage(OptionIdentifier<Void> id, TextComponent name, List<OptionGroup> groups) {
        this.id = id;
        this.name = name;
        this.groups = collectExtraGroups(groups);
        this.options = flattenOptions(this.groups);
    }

    // Fires the construction event and appends whatever other mods add, as an unmodifiable copy so neither the caller nor a reader can change the built page
    private List<OptionGroup> collectExtraGroups(List<OptionGroup> groups) {
        OptionPageConstructionEvent event = new OptionPageConstructionEvent(this.id, this.name);
        OptionPageConstructionEvent.BUS.post(event);
        List<OptionGroup> extraGroups = event.getAdditionalGroups();
        if (extraGroups.isEmpty()) {
            // No copy when the caller already holds a JDK unmodifiable list, as ConfigPageBuilder.build passes
            return List.copyOf(groups);
        }
        List<OptionGroup> all = new ArrayList<>(groups.size() + extraGroups.size());
        all.addAll(groups);
        all.addAll(extraGroups);
        return List.copyOf(all);
    }

    // Unmodifiable, in group order; a lone group's list is reused, since OptionGroup already holds it as an unmodifiable copy
    private static List<Option<?>> flattenOptions(List<OptionGroup> groups) {
        if (groups.size() == 1) {
            return List.copyOf(groups.get(0).getOptions());
        }
        int size = 0;
        for (OptionGroup group : groups) {
            size += group.getOptions().size();
        }
        List<Option<?>> options = new ArrayList<>(size);
        for (OptionGroup group : groups) {
            options.addAll(group.getOptions());
        }
        return List.copyOf(options);
    }

    // Page id
    public OptionIdentifier<Void> getId() {
        return id;
    }

    // In display order; unmodifiable
    public List<OptionGroup> getGroups() {
        return this.groups;
    }

    // Flattened across groups; unmodifiable
    public List<Option<?>> getOptions() {
        return this.options;
    }

    // Tab label
    public TextComponent getName() {
        return this.name;
    }
}
