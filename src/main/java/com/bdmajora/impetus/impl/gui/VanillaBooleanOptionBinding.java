package com.bdmajora.impetus.impl.gui;

import net.minecraft.client.settings.GameSettings;
import com.bdmajora.impetus.api.options.binding.OptionBinding;

import java.util.Objects;

// Binding of a vanilla boolean setting.
public class VanillaBooleanOptionBinding implements OptionBinding<GameSettings, Boolean> {
    // Vanilla setting of the binding.
    private final GameSettings.Options option;

    // Creates a vanilla boolean option binding from its option.
    public VanillaBooleanOptionBinding(GameSettings.Options option) {
        if (!Objects.requireNonNull(option, "Option must not be null").isBoolean()) {
            throw new IllegalArgumentException("Not an on/off option: " + option);
        }
        this.option = option;
    }

    // Stores the value.
    @Override
    public void setValue(GameSettings settings, Boolean value) {
        if (settings.getOptionOrdinalValue(this.option) != value) {
            settings.setOptionValue(this.option, 1);
        }
    }

    // Returns the value.
    @Override
    public Boolean getValue(GameSettings settings) {
        return settings.getOptionOrdinalValue(this.option);
    }
}
