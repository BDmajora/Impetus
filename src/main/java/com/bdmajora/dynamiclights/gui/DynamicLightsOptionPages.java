package com.bdmajora.dynamiclights.gui;

import com.bdmajora.dynamiclights.DynamicLightsConfig;
import com.bdmajora.dynamiclights.DynamicLightsMode;
import com.bdmajora.dynamiclights.ExplosiveLightingMode;
import com.bdmajora.impetus.api.options.OptionIdentifier;
import com.bdmajora.impetus.api.options.structure.OptionGroup;
import com.bdmajora.impetus.api.options.structure.OptionImpact;
import com.bdmajora.impetus.api.options.structure.OptionImpl;
import com.bdmajora.impetus.api.options.structure.OptionPage;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.impl.gui.ModuleOptions;
import com.google.common.collect.ImmutableList;

import java.util.function.BooleanSupplier;

// Builder of the Dynamic Lights page.
public final class DynamicLightsOptionPages {
    // Identifier of the module.
    private static final String MOD_ID = "umbra";
    // Prefix of the translation keys.
    private static final String LANG = "impetus.options.dynamiclights.";

    // Factory of the options of the module.
    private static final ModuleOptions<DynamicLightsConfig> OPTIONS = new ModuleOptions<>(MOD_ID, "dynamiclights/", LANG, new DynamicLightsOptionsStorage());

    // Prevents instantiation
    private DynamicLightsOptionPages() {
    }

    // Returns the Dynamic Lights page.
    public static OptionPage page() {
        OptionImpl<DynamicLightsConfig, DynamicLightsMode> mode = OPTIONS.cycling("mode", DynamicLightsMode.class, DynamicLightsMode.values(),
                (config, value) -> config.mode = value, config -> config.mode,
                OptionImpact.MEDIUM, null, null);
        BooleanSupplier enabled = () -> mode.getValue().isEnabled();

        return new OptionPage(
                OptionIdentifier.create("impetus", "dynamic_lights"),
                TextComponent.translatable("impetus.options.pages.dynamiclights"),
                ImmutableList.of(sources(mode, enabled), explosives(enabled)));
    }

    // Returns the group of the update rate and light source switches.
    private static OptionGroup sources(OptionImpl<DynamicLightsConfig, DynamicLightsMode> mode, BooleanSupplier enabled) {
        return OptionGroup.createBuilder()
                .setId(OPTIONS.group("general"))
                .setName(TextComponent.translatable(LANG + "group.general"))
                .add(mode)
                .add(OPTIONS.toggle("self", (config, value) -> config.selfLightSource = value,
                        config -> config.selfLightSource, null, null, enabled))
                .add(OPTIONS.toggle("entities", (config, value) -> config.entitiesLightSource = value,
                        config -> config.entitiesLightSource, null, null, enabled))
                .add(OPTIONS.toggle("block_entities", (config, value) -> config.blockEntitiesLightSource = value,
                        config -> config.blockEntitiesLightSource, null, null, enabled))
                .add(OPTIONS.toggle("water_sensitive", (config, value) -> config.waterSensitiveCheck = value,
                        config -> config.waterSensitiveCheck, null, null, enabled))
                .build();
    }

    // Returns the group of the explosive modes.
    private static OptionGroup explosives(BooleanSupplier enabled) {
        return OptionGroup.createBuilder()
                .setId(OPTIONS.group("explosives"))
                .setName(TextComponent.translatable(LANG + "group.explosives"))
                .add(OPTIONS.cycling("tnt", ExplosiveLightingMode.class, ExplosiveLightingMode.values(),
                        (config, value) -> config.tntLighting = value,
                        config -> config.tntLighting, enabled))
                .add(OPTIONS.cycling("creeper", ExplosiveLightingMode.class, ExplosiveLightingMode.values(),
                        (config, value) -> config.creeperLighting = value,
                        config -> config.creeperLighting, enabled))
                .build();
    }
}
