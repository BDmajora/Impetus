package com.bdmajora.impetus.api.options.structure;

import com.bdmajora.impetus.api.OptionGroupConstructionEvent;
import com.bdmajora.impetus.api.options.OptionIdentifier;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public class OptionGroup {
    public static final OptionIdentifier<Void> DEFAULT_ID = OptionIdentifier.create("impetus", "empty");

    private final List<Option<?>> options;

    public final OptionIdentifier<Void> id;
    // Optional heading drawn above the group's rows; null keeps Sodium's bare padded block
    private final @Nullable TextComponent name;

    private OptionGroup(OptionIdentifier<Void> id, @Nullable TextComponent name, List<Option<?>> options) {
        this.id = id;
        this.name = name;
        this.options = options;
    }

    // Group id
    public OptionIdentifier<Void> getId() {
        return id;
    }

    // The heading, or null for an unnamed group
    public @Nullable TextComponent getName() {
        return this.name;
    }

    // Starts a builder
    public static Builder createBuilder() {
        return new Builder();
    }

    // In declaration order, listener additions last; unmodifiable and the same list on every call, which OptionPage reuses as is
    public List<Option<?>> getOptions() {
        return this.options;
    }

    public static class Builder {
        private final List<Option<?>> options = new ArrayList<>();
        // Never null, so an unset id stays the default and build has nothing to fill in
        private OptionIdentifier<Void> id = OptionGroup.DEFAULT_ID;
        private @Nullable TextComponent name;

        // Required
        public Builder setId(OptionIdentifier<Void> id) {
            this.id = Objects.requireNonNull(id, "Group id must not be null");
            return this;
        }

        // Optional heading; a page holding several groups reads far better with one over each
        public Builder setName(@Nullable TextComponent name) {
            this.name = name;
            return this;
        }

        // Appends; a null is refused here, where the caller is, instead of failing the build after every listener ran
        public Builder add(Option<?> option) {
            this.options.add(Objects.requireNonNull(option, "Option must not be null"));
            return this;
        }

        // Appends only when the condition holds, without building otherwise; a missing supplier fails either way, so it shows on every machine
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

        // Fires the construction event so other mods can add options, then finalises
        public OptionGroup build() {
            // FIXME actually enforce IDs on groups
            OptionIdentifier<Void> id = this.id;
            // Listeners edit a copy, so a reused builder never collects their additions twice and a failed build leaves it as it was
            List<Option<?>> working = new ArrayList<>(this.options);
            OptionGroupConstructionEvent.BUS.post(new OptionGroupConstructionEvent(id, working));
            List<Option<?>> options;
            try {
                // Also cuts the group loose from a listener that kept the working list
                options = List.copyOf(working);
            } catch (NullPointerException e) {
                // add refuses nulls, so only a listener can have put one there
                throw new NullPointerException("A listener added a null option to group " + id);
            }
            return new OptionGroup(id, this.name, options);
        }
    }
}
