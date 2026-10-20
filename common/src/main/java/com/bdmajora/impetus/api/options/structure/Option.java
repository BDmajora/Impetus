package com.bdmajora.impetus.api.options.structure;

import com.bdmajora.impetus.api.options.control.Control;
import com.bdmajora.impetus.api.options.OptionIdentifier;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public interface Option<T> {
    // Defaults to the empty placeholder
    @Nullable
    default OptionIdentifier<T> getId() {
        return null;
    }

    // Class of the values this option holds, from its id; Object when the id is missing or untyped, which matches any control
    default Class<?> getValueType() {
        OptionIdentifier<T> id = this.getId();
        Class<?> type = OptionIdentifier.isPresent(id) ? id.getType() : null;
        return type == null || type == void.class || type == Void.class ? Object.class : type;
    }

    TextComponent getName();

    TextComponent getTooltip();

    OptionImpact getImpact();

    Control<T> getControl();

    T getValue();

    void setValue(T value);

    void reset();

    OptionStorage<?> getStorage();

    boolean isAvailable();

    boolean hasChanged();

    void applyChanges();

    Collection<OptionFlag> getFlags();
}
