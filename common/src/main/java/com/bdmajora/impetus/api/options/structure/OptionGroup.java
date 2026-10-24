package com.bdmajora.impetus.api.options.structure;

import com.bdmajora.impetus.api.OptionGroupConstructionEvent;
import com.bdmajora.impetus.api.options.OptionIdentifier;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

// Group of options shown together on a page with an optional heading
public class OptionGroup {
    // Untyped interned id used by a group built without one
    public static final OptionIdentifier<Void> DEFAULT_ID = OptionIdentifier.intern("impetus", "empty").cast();

    private final List<Option<?>> options;

    public final OptionIdentifier<Void> id;
    // Heading drawn above the group rows or null for a group without a heading
    private final @Nullable TextComponent name;

    // Stores the id and heading and options
    private OptionGroup(OptionIdentifier<Void> id, @Nullable TextComponent name, List<Option<?>> options) {
        this.id = id;
        this.name = name;
        this.options = options;
    }

    // Returns the group id
    public OptionIdentifier<Void> getId() {
        return id;
    }

    // Returns the heading or null for an unnamed group
    public @Nullable TextComponent getName() {
        return this.name;
    }

    // Returns a new builder
    public static Builder createBuilder() {
        return new Builder();
    }

    // Returns the unmodifiable list of options in the order they were added
    public List<Option<?>> getOptions() {
        return this.options;
    }

    // Collects the id and heading and options of a group until build() is called
    public static class Builder {
        private final List<Option<?>> options = new ArrayList<>();
        // Group id starting as the default id
        private OptionIdentifier<Void> id = OptionGroup.DEFAULT_ID;
        private @Nullable TextComponent name;

        // Sets the group id and rejects a null id
        public Builder setId(OptionIdentifier<Void> id) {
            this.id = Objects.requireNonNull(id, "Group id must not be null");
            return this;
        }

        // Sets the heading drawn above the group or clears it with null
        public Builder setName(@Nullable TextComponent name) {
            this.name = name;
            return this;
        }

        // Adds an option to the end of the group and rejects a null option
        public Builder add(Option<?> option) {
            this.options.add(Objects.requireNonNull(option, "Option must not be null"));
            return this;
        }

        // Adds the supplied option only when the condition holds and rejects a null supplier or result
        public Builder addConditionally(boolean shouldAdd, Supplier<Option<?>> option) {
            Objects.requireNonNull(option, "Option supplier must not be null");
            if (shouldAdd) {
                Option<?> made = option.get();
                if (made == null) {
                    throw new NullPointerException("Option supplier returned null");
                }
                this.options.add(made);
            }
            return this;
        }

        // Posts the construction event so listeners can add options and then creates the group
        public OptionGroup build() {
            // FIXME actually enforce IDs on groups
            OptionIdentifier<Void> id = this.id;
            // Gives listeners a copy of the options to add to
            List<Option<?>> working = new ArrayList<>(this.options);
            OptionGroupConstructionEvent.BUS.post(new OptionGroupConstructionEvent(id, working));
            List<Option<?>> options;
            try {
                // Copies the result into an unmodifiable list
                options = List.copyOf(working);
            } catch (NullPointerException e) {
                // Reports a null option added by a listener
                throw new NullPointerException("A listener added a null option to group " + id);
            }
            return new OptionGroup(id, this.name, options);
        }
    }
}