package com.bdmajora.extras.gui;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.ExtrasConfig;
import com.bdmajora.impetus.api.options.structure.OptionStorage;

// Storage that saves the Extras config when an option changes.
public final class ExtrasOptionsStorage implements OptionStorage<ExtrasConfig> {
    // Returns the data.
    @Override
    public ExtrasConfig getData() {
        return Extras.options();
    }

    // Saves the value.
    @Override
    public void save() {
        Extras.save();
    }
}
