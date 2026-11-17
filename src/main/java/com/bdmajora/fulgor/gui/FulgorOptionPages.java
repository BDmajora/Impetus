package com.bdmajora.fulgor.gui;

import com.bdmajora.fulgor.FulgorConfig;
import com.bdmajora.impetus.api.options.structure.OptionFlag;
import com.bdmajora.impetus.api.options.structure.OptionImpact;
import com.bdmajora.impetus.api.options.structure.OptionImpl;
import com.bdmajora.impetus.api.options.structure.OptionStorage;
import com.bdmajora.impetus.impl.gui.ModuleOptions;

// Builder of the Fulgor lighting engine switch on the Performance page.
public final class FulgorOptionPages {
    // Mod id of the option.
    private static final String MOD_ID = "fulgor";

    // Storage that saves the Fulgor config.
    private static final OptionStorage<FulgorConfig> STORAGE = new OptionStorage<FulgorConfig>() {
        // Returns the loaded config.
        @Override
        public FulgorConfig getData() {
            return FulgorConfig.get();
        }

        // Writes the config to its file.
        @Override
        public void save() {
            FulgorConfig.get().save();
        }
    };
    // Options of the Fulgor module.
    private static final ModuleOptions<FulgorConfig> OPTIONS = new ModuleOptions<>(MOD_ID, "", "", STORAGE);

    // Prevents instantiation
    private FulgorOptionPages() {
    }

    // Returns the switch of the whole lighting engine.
    public static OptionImpl<FulgorConfig, Boolean> engine() {
        return OPTIONS.toggle("enabled", "impetus.options.fulgor.enabled",
                (config, value) -> config.enabled = value, config -> config.enabled,
                OptionImpact.HIGH, OptionFlag.REQUIRES_GAME_RESTART, null);
    }
}
