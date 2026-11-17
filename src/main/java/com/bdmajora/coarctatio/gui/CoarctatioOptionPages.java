package com.bdmajora.coarctatio.gui;

import com.bdmajora.coarctatio.CoarctatioConfig;
import com.bdmajora.impetus.api.options.structure.OptionFlag;
import com.bdmajora.impetus.api.options.structure.OptionImpact;
import com.bdmajora.impetus.api.options.structure.OptionImpl;
import com.bdmajora.impetus.api.options.structure.OptionStorage;
import com.bdmajora.impetus.impl.gui.ModuleOptions;

// Builder of the single Coarctatio switch on the Performance page.
public final class CoarctatioOptionPages {
    // Identifier of the module.
    private static final String MOD_ID = "coarctatio";

    // Storage that reads and writes the configuration.
    private static final OptionStorage<CoarctatioConfig> STORAGE = new OptionStorage<CoarctatioConfig>() {
        // Returns the loaded configuration.
        @Override
        public CoarctatioConfig getData() {
            return CoarctatioConfig.get();
        }

        // Writes the configuration to its file.
        @Override
        public void save() {
            CoarctatioConfig.get().save();
        }
    };
    // Factory of the options of the module.
    private static final ModuleOptions<CoarctatioConfig> OPTIONS = new ModuleOptions<>(MOD_ID, "", "", STORAGE);

    // Prevents instantiation
    private CoarctatioOptionPages() {
    }

    // Returns the master switch of the memory features.
    public static OptionImpl<CoarctatioConfig, Boolean> memory() {
        return OPTIONS.toggle("enabled", "impetus.options.coarctatio.enabled",
                (config, value) -> config.enabled = value, config -> config.enabled,
                OptionImpact.MEDIUM, OptionFlag.REQUIRES_GAME_RESTART, null);
    }
}
