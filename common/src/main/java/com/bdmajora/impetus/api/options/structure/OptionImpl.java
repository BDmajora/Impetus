package com.bdmajora.impetus.api.options.structure;

import com.bdmajora.impetus.api.options.binding.GenericBinding;
import com.bdmajora.impetus.api.options.binding.OptionBinding;
import com.bdmajora.impetus.api.options.control.Control;
import com.bdmajora.impetus.api.options.OptionIdentifier;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodType;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

// Option that reads and writes its value through a binding on a storage object
public final class OptionImpl<S, T> implements Option<T> {
    // Shared unmodifiable flag set for each combination of flags indexed by the ordinal bits
    @SuppressWarnings("unchecked")
    private static final Set<OptionFlag>[] SHARED_FLAGS = new Set[1 << OptionFlag.values().length];

    private final OptionStorage<S> storage;

    private final OptionBinding<S, T> binding;
    private final Control<T> control;
    // Failure thrown while creating the control or null when it was created
    private final Throwable controlFailure;

    // Shared unmodifiable set of flags
    private final Set<OptionFlag> flags;

    private final OptionIdentifier<T> id;
    // Boxed value type given to the builder
    private final Class<?> valueType;
    private final TextComponent name;
    private final TextComponent tooltip;

    private final OptionImpact impact;

    // Pending value or null when the option has not been changed
    private @Nullable T modifiedValue;

    // Predicate that decides whether the option can be edited
    private final BooleanSupplier enabled;

    // Stores the option parts and creates the control
    private OptionImpl(OptionStorage<S> storage,
                       Class<T> type,
                       OptionIdentifier<T> id,
                       TextComponent name,
                       TextComponent tooltip,
                       OptionBinding<S, T> binding,
                       Function<OptionImpl<S, T>, Control<T>> control,
                       Set<OptionFlag> flags,
                       OptionImpact impact,
                       BooleanSupplier enabled) {
        this.id = id;
        // Uses Object for a missing or void type and the wrapper class for a primitive type
        this.valueType = type == null || type == void.class || type == Void.class ? Object.class
                : type.isPrimitive() ? MethodType.methodType(type).wrap().returnType() : type;
        this.storage = storage;
        this.name = name;
        this.tooltip = tooltip;
        this.binding = binding;
        this.impact = impact;
        this.flags = flags;
        // Stores the enabled predicate before the control factory runs
        this.enabled = enabled;
        // Stores the failure when the control factory throws
        Control<T> made = null;
        Throwable failure = null;
        try {
            made = control.apply(this);
        } catch (VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable broken) {
            // Restores the interrupt flag after an interruption
            if (broken instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            failure = broken;
        }
        this.control = made;
        this.controlFailure = failure;
        // Clears any value set while the control was created
        this.modifiedValue = null;
    }

    // Returns the shared unmodifiable set holding exactly these flags
    private static Set<OptionFlag> sharedFlags(EnumSet<OptionFlag> flags) {
        int bits = 0;
        for (OptionFlag flag : flags) {
            bits |= 1 << flag.ordinal();
        }
        Set<OptionFlag> shared = SHARED_FLAGS[bits];
        // Creates and stores the shared set on first use
        if (shared == null) {
            SHARED_FLAGS[bits] = shared = Collections.unmodifiableSet(EnumSet.copyOf(flags));
        }
        return shared;
    }

    // Returns the option id
    @Override
    public OptionIdentifier<T> getId() {
        return id;
    }

    // Returns the boxed value type given to the builder
    @Override
    public Class<?> getValueType() {
        return this.valueType;
    }

    // Returns the label of the row
    @Override
    public TextComponent getName() {
        return this.name;
    }

    // Returns the hover text
    @Override
    public TextComponent getTooltip() {
        return this.tooltip;
    }

    // Returns the performance impact shown in the tooltip
    @Override
    public OptionImpact getImpact() {
        return this.impact;
    }

    // Returns the control or throws when it could not be created
    @Override
    public Control<T> getControl() {
        if (this.controlFailure != null) {
            // Names the option by its id or by its label when it has no id
            Object named = OptionIdentifier.isPresent(this.id) ? this.id : this.name;
            throw new IllegalStateException("Control of option " + named + " could not be made", this.controlFailure);
        }
        return this.control;
    }

    // Returns the pending value or the stored value when nothing is pending
    @Override
    public T getValue() {
        T pending = this.modifiedValue;
        return pending != null ? pending : this.binding.getValue(this.storage.getData());
    }

    // Sets the pending value and clears it when the value is null
    @Override
    public void setValue(T value) {
        this.modifiedValue = value;
    }

    // Discards the pending value
    @Override
    public void reset() {
        this.modifiedValue = null;
    }

    // Returns the storage the value is written to
    @Override
    public OptionStorage<?> getStorage() {
        return this.storage;
    }

    // Returns the result of the enabled predicate
    @Override
    public boolean isAvailable() {
        return this.enabled.getAsBoolean();
    }

    // Returns whether a pending value exists and differs from the stored value
    @Override
    public boolean hasChanged() {
        T pending = this.modifiedValue;
        return pending != null && !pending.equals(this.binding.getValue(this.storage.getData()));
    }

    // Writes the pending value into the storage and keeps it pending when the write fails
    @Override
    public void applyChanges() {
        T pending = this.modifiedValue;
        if (pending != null) {
            this.binding.setValue(this.storage.getData(), pending);
            // Clears the pending value only when it is still the value that was written
            if (this.modifiedValue == pending) {
                this.modifiedValue = null;
            }
        }
    }

    // Returns the actions required after the change is applied
    @Override
    public Collection<OptionFlag> getFlags() {
        return this.flags;
    }

    // Returns a builder for an option of the given type and storage and rejects a null storage
    public static <S, T> OptionImpl.Builder<S, T> createBuilder(Class<T> type, OptionStorage<S> storage) {
        return new Builder<>(Objects.requireNonNull(storage, "Storage must not be null"), type);
    }

    // Collects the parts of an option until build() is called
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
        // Predicates for an option that is always enabled or always disabled
        private static final BooleanSupplier ALWAYS_ENABLED = () -> true;
        private static final BooleanSupplier ALWAYS_DISABLED = () -> false;
        private BooleanSupplier enabled = ALWAYS_ENABLED;

        // Stores the storage and value type
        private Builder(OptionStorage<S> storage, Class<T> type) {
            this.storage = storage;
            this.type = type;
        }

        // Sets the option id and rejects a null id
        public Builder<S, T> setId(OptionIdentifier<T> id) {
            Objects.requireNonNull(id, "Id must not be null");

            this.id = id;

            return this;
        }

        // Sets the label and rejects a null label
        public Builder<S, T> setName(TextComponent name) {
            Objects.requireNonNull(name, "Argument must not be null");

            this.name = name;

            return this;
        }

        // Sets the hover text and rejects a null tooltip
        public Builder<S, T> setTooltip(TextComponent tooltip) {
            Objects.requireNonNull(tooltip, "Argument must not be null");

            this.tooltip = tooltip;

            return this;
        }

        // Sets the binding from a setter and getter on the storage object
        public Builder<S, T> setBinding(BiConsumer<S, T> setter, Function<S, T> getter) {
            Objects.requireNonNull(setter, "Setter must not be null");
            Objects.requireNonNull(getter, "Getter must not be null");

            this.binding = new GenericBinding<>(setter, getter);

            return this;
        }


        // Sets the binding and rejects a null binding
        public Builder<S, T> setBinding(OptionBinding<S, T> binding) {
            Objects.requireNonNull(binding, "Argument must not be null");

            this.binding = binding;

            return this;
        }

        // Sets the factory that creates the control for the option
        public Builder<S, T> setControl(Function<OptionImpl<S, T>, Control<T>> control) {
            Objects.requireNonNull(control, "Argument must not be null");

            this.control = control;

            return this;
        }

        // Sets the performance impact or clears it with null
        public Builder<S, T> setImpact(OptionImpact impact) {
            this.impact = impact;

            return this;
        }

        // Sets the predicate that decides whether the option can be edited
        public Builder<S, T> setEnabledPredicate(BooleanSupplier value) {
            this.enabled = Objects.requireNonNull(value, "Enabled predicate must not be null");

            return this;
        }

        // Makes the option always enabled or always disabled
        public Builder<S, T> setEnabled(boolean value) {
            setEnabledPredicate(value ? ALWAYS_ENABLED : ALWAYS_DISABLED);

            return this;
        }

        // Adds the flags for the actions required after the change is applied and rejects any null flag
        public Builder<S, T> setFlags(OptionFlag... flags) {
            this.flags.addAll(List.of(flags));

            return this;
        }

        // Checks the required parts and creates the option with a name and tooltip derived from the id when not set
        public OptionImpl<S, T> build() {
            OptionIdentifier<T> id = this.id;
            TextComponent name = this.name;
            TextComponent tooltip = this.tooltip;
            if (id == null) {
                // FIXME enforce ids and stop using EMPTY as the id for any option type
                @SuppressWarnings("unchecked")
                OptionIdentifier<T> empty = (OptionIdentifier<T>) OptionIdentifier.EMPTY;
                id = empty;
                //Impetus.logger().warn("Id must be specified in option '{}', this might throw a exception on a future release", this.name.getString());
            } else if (name == null || tooltip == null) {
                // Builds the translation key prefix from the mod id and path
                String key = id.getModId() + ".options." + id.getPath();
                if (name == null) {
                    name = TextComponent.translatable(key + ".name");
                }
                if (tooltip == null) {
                    tooltip = TextComponent.translatable(key + ".tooltip");
                }
            }

            Objects.requireNonNull(name, "Name must be specified or inferred from a specified ID");
            Objects.requireNonNull(tooltip, "Tooltip must be specified or inferred from a specified ID");
            Objects.requireNonNull(this.binding, "Option binding must be specified");
            Objects.requireNonNull(this.control, "Control must be specified");

            // Passes the shared flag set matching the builder flags
            return new OptionImpl<>(this.storage, this.type, id, name, tooltip, this.binding, this.control, sharedFlags(this.flags), this.impact, this.enabled);
        }
    }
}