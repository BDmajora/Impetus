package com.bdmajora.impetus.api.options.structure;

import com.bdmajora.impetus.api.options.control.Control;
import com.bdmajora.impetus.api.options.OptionIdentifier;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodType;
import java.util.Collection;

// Single setting on the options screen with a label and a pending value and the storage it is written to
public interface Option<T> {
    // Returns the option id or null by default
    @Nullable
    default OptionIdentifier<T> getId() {
        return null;
    }

    // Returns the class of the values this option holds or Object when the id is missing or untyped
    default Class<?> getValueType() {
        OptionIdentifier<T> id = this.getId();
        Class<?> type = OptionIdentifier.isPresent(id) ? id.getType() : null;
        if (type == null || type == void.class || type == Void.class) {
            return Object.class;
        }
        // Returns the wrapper class for a primitive type
        return type.isPrimitive() ? MethodType.methodType(type).wrap().returnType() : type;
    }

    // Returns the label of the row and never returns null
    TextComponent getName();

    // Returns the hover text or null when the option has none
    @Nullable
    TextComponent getTooltip();

    // Returns the performance impact shown under the tooltip or null when none is given
    @Nullable
    OptionImpact getImpact();

    // Returns the control that edits this option
    Control<T> getControl();

    // Returns the pending value or the stored value when nothing is pending
    @Nullable
    T getValue();

    // Sets the pending value without storing it
    void setValue(T value);

    // Discards the pending value
    void reset();

    // Returns the storage that applyChanges writes the value to and never returns null
    OptionStorage<?> getStorage();

    // Returns whether the option can currently be edited
    boolean isAvailable();

    // Returns whether the pending value differs from the stored value
    boolean hasChanged();

    // Writes the pending value into the storage and clears it
    void applyChanges();

    // Returns the actions required after the change is applied and never returns null
    Collection<OptionFlag> getFlags();
}