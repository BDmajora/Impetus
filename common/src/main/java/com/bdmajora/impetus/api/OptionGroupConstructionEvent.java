package com.bdmajora.impetus.api;

import com.bdmajora.impetus.api.options.structure.Option;
import com.bdmajora.impetus.api.eventbus.ImpetusEvent;
import com.bdmajora.impetus.api.eventbus.EventHandlerRegistrar;
import com.bdmajora.impetus.api.options.OptionIdentifier;

import java.util.List;
import java.util.Objects;

// Event posted while an option group is built so listeners can change or add to its options
public final class OptionGroupConstructionEvent extends ImpetusEvent {
    // Listeners for this event
    public static final EventHandlerRegistrar<OptionGroupConstructionEvent> BUS = new EventHandlerRegistrar<>();

    private final OptionIdentifier<Void> id;
    private final List<Option<?>> options;

    // Stores the group id and the list of options and rejects either if null
    public OptionGroupConstructionEvent(OptionIdentifier<Void> id, List<Option<?>> options) {
        this.id = Objects.requireNonNull(id, "Group id must not be null");
        this.options = Objects.requireNonNull(options, "Options must not be null");
    }

    // Returns the list of options that listeners change directly
    public List<Option<?>> getOptions() {
        return this.options;
    }

    // Returns the id of the group being built
    public OptionIdentifier<Void> getId() {
        return this.id;
    }
}