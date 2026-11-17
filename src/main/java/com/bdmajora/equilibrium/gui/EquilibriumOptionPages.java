package com.bdmajora.equilibrium.gui;

import com.bdmajora.equilibrium.Equilibrium;
import com.bdmajora.equilibrium.config.EquilibriumConfig;
import com.bdmajora.impetus.api.options.OptionIdentifier;
import com.bdmajora.impetus.api.options.control.TickBoxControl;
import com.bdmajora.impetus.api.options.structure.OptionFlag;
import com.bdmajora.impetus.api.options.structure.OptionImpact;
import com.bdmajora.impetus.api.options.structure.OptionImpl;
import com.bdmajora.impetus.api.options.structure.OptionStorage;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;

// Builder of the single Equilibrium switch on the Performance page.
public final class EquilibriumOptionPages {
    // Identifier of the module.
    private static final String MOD_ID = "equilibrium";

    // Storage that reads and writes the settings.
    private static final OptionStorage<EquilibriumConfig> STORAGE = new OptionStorage<EquilibriumConfig>() {
        // Returns the loaded settings.
        @Override
        public EquilibriumConfig getData() {
            return Equilibrium.config();
        }

        // Writes the settings to the file.
        @Override
        public void save() {
            Equilibrium.config().save();
        }
    };

    // Prevents instantiation
    private EquilibriumOptionPages() {
    }

    // Returns the master switch of the game logic patches.
    public static OptionImpl<EquilibriumConfig, Boolean> gameLogic() {
        return OptionImpl.createBuilder(boolean.class, STORAGE)
                .setId(OptionIdentifier.create(MOD_ID, "enabled", boolean.class))
                .setName(TextComponent.translatable("impetus.options.equilibrium.enabled.name"))
                .setTooltip(TextComponent.translatable("impetus.options.equilibrium.enabled.tooltip"))
                .setControl(TickBoxControl::new)
                .setImpact(OptionImpact.MEDIUM)
                .setFlags(OptionFlag.REQUIRES_GAME_RESTART)
                .setBinding(EquilibriumConfig::setEnabled, EquilibriumConfig::isEnabled)
                .build();
    }
}
