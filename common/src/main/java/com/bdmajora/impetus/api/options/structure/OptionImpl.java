package com.bdmajora.impetus.api.options.structure;

import com.bdmajora.impetus.api.options.binding.GenericBinding;
import com.bdmajora.impetus.api.options.binding.OptionBinding;
import com.bdmajora.impetus.api.options.control.Control;
import com.bdmajora.impetus.api.options.OptionIdentifier;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

public class OptionImpl<S, T> implements Option<T> {

    private final OptionStorage<S> storage;

    private final OptionBinding<S, T> binding;
    private final Control<T> control;

    private final EnumSet<OptionFlag> flags;

    private final OptionIdentifier<T> id;
    private final TextComponent name;
    private final TextComponent tooltip;

    private final OptionImpact impact;

    // Null until the user changes the option; applyChanges() flushes it back into the binding and clears it
    private @Nullable T modifiedValue;

    private final BooleanSupplier enabled;

    private OptionImpl(OptionStorage<S> storage,
                       OptionIdentifier<T> id,
                       TextComponent name,
                       TextComponent tooltip,
                       OptionBinding<S, T> binding,
                       Function<OptionImpl<S, T>, Control<T>> control,
                       EnumSet<OptionFlag> flags,
                       OptionImpact impact,
                       BooleanSupplier enabled) {
        this.id = id;
        this.storage = storage;
        this.name = name;
        this.tooltip = tooltip;
        this.binding = binding;
        this.impact = impact;
        this.flags = flags;
        this.control = control.apply(this);
        this.enabled = enabled;

        this.reset();
    }

    // Unique id, for lookup and events
    @Override
    public OptionIdentifier<T> getId() {
        return id;
    }

    // Label
    @Override
    public TextComponent getName() {
        return this.name;
    }

    // Hover text
    @Override
    public TextComponent getTooltip() {
        return this.tooltip;
    }

    // Performance impact shown in the tooltip
    @Override
    public OptionImpact getImpact() {
        return this.impact;
    }

    // The widget type
    @Override
    public Control<T> getControl() {
        return this.control;
    }

    // Pending value, not yet applied
    @Override
    public T getValue() {
        return this.modifiedValue != null ? this.modifiedValue : this.binding.getValue(this.storage.getData());
    }

    // Sets the pending value
    @Override
    public void setValue(T value) {
        this.modifiedValue = value;
    }

    // Discards the pending value
    @Override
    public void reset() {
        this.modifiedValue = null;
    }

    // Where the value lives
    @Override
    public OptionStorage<?> getStorage() {
        return this.storage;
    }

    // From the enabled predicate; greyed out when false
    @Override
    public boolean isAvailable() {
        return this.enabled.getAsBoolean();
    }

    // Pending differs from stored
    @Override
    public boolean hasChanged() {
        return this.modifiedValue != null && !this.binding.getValue(this.storage.getData()).equals(this.modifiedValue);
    }

    // Writes pending into storage
    @Override
    public void applyChanges() {
        if (this.modifiedValue != null) {
            this.binding.setValue(this.storage.getData(), this.modifiedValue);
            this.modifiedValue = null;
        }
    }

    // What must happen after applying: reload renderer, textures and so on
    @Override
    public Collection<OptionFlag> getFlags() {
        return this.flags;
    }

    public static <S, T> OptionImpl.Builder<S, T> createBuilder(@SuppressWarnings("unused") Class<T> type, OptionStorage<S> storage) {
        return new Builder<>(storage, type);
    }

    public static class Builder<S, T> {
        private final OptionStorage<S> storage;
        private final Class<T> type;
        private OptionIdentifier<T> id;
        private TextComponent name;
        private TextComponent tooltip;
        private OptionBinding<S, T> binding;
        private Function<OptionImpl<S, T>, Control<T>> control;
        private OptionImpact impact;
        private final EnumSet<OptionFlag> flags = EnumSet.noneOf(OptionFlag.class);
        private static final BooleanSupplier ALWAYS_ENABLED = () -> true;
        private static final BooleanSupplier ALWAYS_DISABLED = () -> false;
        private BooleanSupplier enabled = ALWAYS_ENABLED;

        private Builder(OptionStorage<S> storage, Class<T> type) {
            this.storage = storage;
            this.type = type;
        }

        // Required
        public Builder<S, T> setId(OptionIdentifier<T> id) {
            Objects.requireNonNull(id, "Id must not be null");

            this.id = id;

            return this;
        }

        // Required
        public Builder<S, T> setName(TextComponent name) {
            Objects.requireNonNull(name, "Argument must not be null");

            this.name = name;

            return this;
        }

        // Required
        public Builder<S, T> setTooltip(TextComponent tooltip) {
            Objects.requireNonNull(tooltip, "Argument must not be null");

            this.tooltip = tooltip;

            return this;
        }

        // Getter and setter over the storage object
        public Builder<S, T> setBinding(BiConsumer<S, T> setter, Function<S, T> getter) {
            Objects.requireNonNull(setter, "Setter must not be null");
            Objects.requireNonNull(getter, "Getter must not be null");

            this.binding = new GenericBinding<>(setter, getter);

            return this;
        }


        // Binding object form
        public Builder<S, T> setBinding(OptionBinding<S, T> binding) {
            Objects.requireNonNull(binding, "Argument must not be null");

            this.binding = binding;

            return this;
        }

        // Widget factory
        public Builder<S, T> setControl(Function<OptionImpl<S, T>, Control<T>> control) {
            Objects.requireNonNull(control, "Argument must not be null");

            this.control = control;

            return this;
        }

        // Optional
        public Builder<S, T> setImpact(OptionImpact impact) {
            this.impact = impact;

            return this;
        }

        // Evaluated every frame so a master switch greys its children live
        public Builder<S, T> setEnabledPredicate(BooleanSupplier value) {
            this.enabled = value;

            return this;
        }

        // Constant form
        public Builder<S, T> setEnabled(boolean value) {
            setEnabledPredicate(value ? ALWAYS_ENABLED : ALWAYS_DISABLED);

            return this;
        }

        // Post-apply side effects
        public Builder<S, T> setFlags(OptionFlag... flags) {
            Collections.addAll(this.flags, flags);

            return this;
        }

        // Validates the required fields and finalises
        public OptionImpl<S, T> build() {
            if (this.id == null) {
                // FIXME enforce IDs and make nullable
                this.id = (OptionIdentifier<T>)OptionIdentifier.EMPTY;
                //Impetus.logger().warn("Id must be specified in option '{}', this might throw a exception on a future release", this.name.getString());
            } else {
                if (this.name == null) {
                    this.name = TextComponent.translatable(this.id.getModId() + ".options." + this.id.getPath() + ".name");
                }

                if (this.tooltip == null) {
                    this.tooltip = TextComponent.translatable(this.id.getModId() + ".options." + this.id.getPath() + ".tooltip");
                }
            }

            Objects.requireNonNull(this.name, "Name must be specified or inferred from a specified ID");
            Objects.requireNonNull(this.tooltip, "Tooltip must be specified or inferred from a specified ID");
            Objects.requireNonNull(this.binding, "Option binding must be specified");
            Objects.requireNonNull(this.control, "Control must be specified");

            return new OptionImpl<>(this.storage, this.id, this.name, this.tooltip, this.binding, this.control, this.flags, this.impact, this.enabled);
        }
    }
}
