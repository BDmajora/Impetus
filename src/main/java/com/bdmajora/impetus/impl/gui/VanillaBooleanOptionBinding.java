package com.bdmajora.impetus.impl.gui;

import net.minecraft.client.settings.GameSettings;
import com.bdmajora.impetus.api.options.binding.OptionBinding;

import java.util.Objects;

public class VanillaBooleanOptionBinding implements OptionBinding<GameSettings, Boolean> {
    private final GameSettings.Options option;

    // Only on/off options: vanilla reads any other option as false and takes the written int as a step through its values
    public VanillaBooleanOptionBinding(GameSettings.Options option) {
        if (!Objects.requireNonNull(option, "Option must not be null").isBoolean()) {
            throw new IllegalArgumentException("Not an on/off option: " + option);
        }
        this.option = option;
    }

    // Through GameSettings.setOptionValue so vanilla's own side effects run; it flips an on/off option whatever int it is
    // given, so it only runs when the value differs, which also skips the reloads and options.txt rewrite on an unchanged write
    @Override
    public void setValue(GameSettings settings, Boolean value) {
        if (settings.getOptionOrdinalValue(this.option) != value) {
            settings.setOptionValue(this.option, 1);
        }
    }

    // Through GameSettings.getOptionOrdinalValue, boxed to the shared Boolean constants
    @Override
    public Boolean getValue(GameSettings settings) {
        return settings.getOptionOrdinalValue(this.option);
    }
}
