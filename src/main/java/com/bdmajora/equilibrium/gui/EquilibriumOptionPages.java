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
import com.google.common.collect.ImmutableMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

// Option pages of the general performance patches.
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

    // Page of each category.
    private static final Map<String, String> PAGE_BY_CATEGORY = ImmutableMap.<String, String>builder()
            .put("world", "world").put("chunk", "world").put("worldgen", "world")
            .put("entity", "entities").put("ai", "entities").put("collections", "entities").put("advancements", "entities")
            .put("block", "internals").put("alloc", "internals").put("math", "internals").put("util", "internals")
            .build();
    // Order of the pages.
    private static final List<String> PAGE_ORDER = ImmutableList.of("world", "entities", "internals");

    // Returns the pages with one group for each category.
    public static List<OptionPage> pages() {
        Map<String, List<OptionGroup>> byPage = new LinkedHashMap<>();
        for (String pageId : PAGE_ORDER) {
            byPage.put(pageId, new ArrayList<>());
        }

        for (String category : EquilibriumOptions.categories()) {
            OptionGroup.Builder group = OptionGroup.createBuilder()
                    .setId(OptionIdentifier.create(MOD_ID, "group." + category))
                    .setName(TextComponent.translatable("impetus.options.equilibrium.category." + category));

            for (EquilibriumOptions.Entry entry : EquilibriumOptions.inCategory(category)) {
                group.add(toggle(entry));
            }

            byPage.get(PAGE_BY_CATEGORY.getOrDefault(category, "internals")).add(group.build());
        }

        List<OptionPage> pages = new ArrayList<>();
        for (Map.Entry<String, List<OptionGroup>> entry : byPage.entrySet()) {
            pages.add(new OptionPage(
                    OptionIdentifier.create(MOD_ID, "page." + entry.getKey()),
                    TextComponent.translatable("impetus.options.pages.equilibrium." + entry.getKey()),
                    ImmutableList.copyOf(entry.getValue())));
        }
        return pages;
    }

    // Returns the toggle of the entry.
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

    // Returns the impact of the entry category or null.
    private static OptionImpact impactOf(EquilibriumOptions.Entry entry) {
        switch (entry.category()) {
            case "world":
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

    // Returns the path of the entry as an identifier.
    private static String key(EquilibriumOptions.Entry entry) {
        return entry.path().replace('.', '_');
    }

    // Returns the translation key prefix of the entry.
    private static String langKey(EquilibriumOptions.Entry entry) {
        return "impetus.options.equilibrium." + key(entry);
    }
}
