package com.bdmajora.equilibrium.gui;

import com.bdmajora.equilibrium.Equilibrium;
import com.bdmajora.equilibrium.config.EquilibriumConfig;
import com.bdmajora.equilibrium.config.EquilibriumOptions;
import com.bdmajora.impetus.api.options.OptionIdentifier;
import com.bdmajora.impetus.api.options.control.TickBoxControl;
import com.bdmajora.impetus.api.options.structure.OptionFlag;
import com.bdmajora.impetus.api.options.structure.OptionGroup;
import com.bdmajora.impetus.api.options.structure.OptionImpact;
import com.bdmajora.impetus.api.options.structure.OptionImpl;
import com.bdmajora.impetus.api.options.structure.OptionPage;
import com.bdmajora.impetus.api.options.structure.OptionStorage;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;

// Optimizations page in Impetus' video options, built by walking EquilibriumOptions so new entries appear for free; every toggle is REQUIRES_GAME_RESTART since the mixin plugin reads options before the window exists
public final class EquilibriumOptionPages {
    private static final String MOD_ID = "equilibrium";

    private static final OptionStorage<EquilibriumConfig> STORAGE = new OptionStorage<EquilibriumConfig>() {
        // The options screen edits the live config directly
        @Override
        public EquilibriumConfig getData() {
            return Equilibrium.config();
        }

        // Writes the file once the screen is dismissed
        @Override
        public void save() {
            Equilibrium.config().save();
        }
    };

    private EquilibriumOptionPages() {
    }

    // Builds the page, one group per declared category, in declaration order
    public static OptionPage optimizations() {
        List<OptionGroup> groups = new ArrayList<>();

        for (String category : EquilibriumOptions.categories()) {
            // FIX: Prefix the category string with "group." so it doesn't collide with boolean toggles
            OptionGroup.Builder group = OptionGroup.createBuilder()
                    .setId(OptionIdentifier.create(MOD_ID, "group." + category));

            for (EquilibriumOptions.Entry entry : EquilibriumOptions.inCategory(category)) {
                group.add(toggle(entry));
            }

            groups.add(group.build());
        }

        return new OptionPage(
                OptionIdentifier.create(MOD_ID, "optimizations"),
                TextComponent.translatable("impetus.options.pages.optimizations"),
                ImmutableList.copyOf(groups));
        }

    // One checkbox per rule, wired to the config and labelled from the lang keys
    private static OptionImpl<EquilibriumConfig, Boolean> toggle(EquilibriumOptions.Entry entry) {
        String key = langKey(entry);

        OptionImpl.Builder<EquilibriumConfig, Boolean> builder =
                OptionImpl.createBuilder(boolean.class, STORAGE)
                        .setId(OptionIdentifier.create(MOD_ID, key(entry), boolean.class))
                        .setName(TextComponent.translatable(key + ".name"))
                        .setTooltip(TextComponent.translatable(key + ".tooltip"))
                        .setControl(TickBoxControl::new)
                        .setFlags(OptionFlag.REQUIRES_GAME_RESTART)
                        .setBinding(
                                (config, value) -> config.setOptionEnabled(entry.name(), value),
                                config -> config.isOptionEnabled(entry.name()));

        OptionImpact impact = impactOf(entry);

        if (impact != null) {
            builder.setImpact(impact);
        }

        return builder.build();
    }

    // Impact assigned per category, not per option; utility categories get no badge since they enable other options rather than removing work
    private static OptionImpact impactOf(EquilibriumOptions.Entry entry) {
        switch (entry.category()) {
            case "world":
                // Explosions, ray casting and block access. The largest single wins on this version.
                return OptionImpact.HIGH;
            case "entity":
            case "math":
                return OptionImpact.MEDIUM;
            case "worldgen":
                return OptionImpact.MEDIUM;
            case "advancements":
                return OptionImpact.MEDIUM;
            case "ai":
            case "alloc":
            case "block":
            case "chunk":
                return OptionImpact.LOW;
            default:
                return null;
        }
    }

    // e.g. mixin.alloc.enum_values.piston_block -> alloc_enum_values_piston_block
    private static String key(EquilibriumOptions.Entry entry) {
        return entry.path().replace('.', '_');
    }

    // Lang key for a rule, derived from its path so a new option needs no GUI change
    private static String langKey(EquilibriumOptions.Entry entry) {
        return "impetus.options.equilibrium." + key(entry);
    }
}
