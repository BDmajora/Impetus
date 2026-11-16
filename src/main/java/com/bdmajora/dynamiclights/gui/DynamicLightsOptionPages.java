package com.bdmajora.dynamiclights.gui;

import java.util.function.BiPredicate;
import com.bdmajora.dynamiclights.DynamicLights;
import com.bdmajora.dynamiclights.DynamicLightsConfig;
import com.bdmajora.dynamiclights.DynamicLightsMode;
import com.bdmajora.dynamiclights.ExplosiveLightingMode;
import com.bdmajora.dynamiclights.client.LightSourceSettings;
import com.bdmajora.impetus.api.options.OptionIdentifier;
import com.bdmajora.impetus.api.options.control.TickBoxControl;
import com.bdmajora.impetus.api.options.structure.OptionGroup;
import com.bdmajora.impetus.api.options.structure.OptionImpact;
import com.bdmajora.impetus.api.options.structure.OptionImpl;
import com.bdmajora.impetus.api.options.structure.OptionPage;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.impl.gui.ModuleOptions;
import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BooleanSupplier;

// Option pages of the dynamic lights.
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

    // Returns the settings page and the light source page.
    public static List<OptionPage> pages() {
        OptionImpl<DynamicLightsConfig, DynamicLightsMode> mode = mode();
        BooleanSupplier enabled = () -> mode.getValue().isEnabled();

        List<OptionGroup> settings = new ArrayList<>();
        settings.add(general(mode, enabled));
        settings.add(explosives(enabled));

        List<OptionGroup> sources = new ArrayList<>();
        addTypeGroups(sources, enabled);

        List<OptionPage> pages = new ArrayList<>();
        pages.add(new OptionPage(
                OptionIdentifier.create(MOD_ID, "dynamiclights"),
                TextComponent.translatable("impetus.options.pages.dynamiclights"),
                ImmutableList.copyOf(settings)));
        pages.add(new OptionPage(
                OptionIdentifier.create(MOD_ID, "dynamiclights_sources"),
                TextComponent.translatable("impetus.options.pages.dynamiclights_sources"),
                ImmutableList.copyOf(sources)));
        return pages;
    }

    // Returns the group of the general switches.
    private static OptionGroup general(OptionImpl<DynamicLightsConfig, DynamicLightsMode> mode,
                                       BooleanSupplier enabled) {
        return OptionGroup.createBuilder()
                .setId(OPTIONS.group("general"))
                .setName(TextComponent.translatable(LANG + "group.general"))
                .add(mode)
                .add(OPTIONS.toggle("self",
                        (config, value) -> config.selfLightSource = value,
                        config -> config.selfLightSource, null, null, enabled))
                .add(OPTIONS.toggle("entities",
                        (config, value) -> config.entitiesLightSource = value,
                        config -> config.entitiesLightSource, null, null, enabled))
                .add(OPTIONS.toggle("block_entities",
                        (config, value) -> config.blockEntitiesLightSource = value,
                        config -> config.blockEntitiesLightSource, null, null, enabled))
                .add(OPTIONS.toggle("water_sensitive",
                        (config, value) -> config.waterSensitiveCheck = value,
                        config -> config.waterSensitiveCheck, null, null, enabled))
                .add(OPTIONS.toggle("debug_info",
                        (config, value) -> config.showDebugInfo = value,
                        config -> config.showDebugInfo, null, null, null))
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

    // Returns the update rate option.
    private static OptionImpl<DynamicLightsConfig, DynamicLightsMode> mode() {
        return OPTIONS.cycling("mode", DynamicLightsMode.class, DynamicLightsMode.values(),
                (config, value) -> config.mode = value, config -> config.mode,
                OptionImpact.MEDIUM, null, null);
    }

    // Adds the entity and block entity groups and logs a failure.
    private static void addTypeGroups(List<OptionGroup> groups, BooleanSupplier enabled) {
        try {
            addTypeGroups(groups, "entity", LightSourceSettings.listEntityTypes(), enabled,
                    (settings, id) -> !settings.isEntityTypeDisabled(id),
                    LightSourceSettings::setEntityTypeEnabled);
        } catch (Throwable t) {
            DynamicLights.LOGGER.warn("Could not build the per-entity light source toggles", t);
        }

        try {
            addTypeGroups(groups, "block_entity", LightSourceSettings.listBlockEntityTypes(), enabled,
                    (settings, id) -> !settings.isBlockEntityTypeDisabled(id),
                    LightSourceSettings::setBlockEntityTypeEnabled);
        } catch (Throwable t) {
            DynamicLights.LOGGER.warn("Could not build the per-block-entity light source toggles", t);
        }
    }

    // Adds one group of toggles for each namespace.
    private static void addTypeGroups(List<OptionGroup> groups, String kind, Map<String, String> types,
                                      BooleanSupplier enabled,
                                      BiPredicate<LightSourceSettings, String> getter,
                                      TypeSetter setter) {
        if (types.isEmpty()) {
            return;
        }

        LightSourceSettings settings = LightSourceSettings.getInstance();

        Map<String, Map<String, String>> byNamespace = new TreeMap<>();
        for (Map.Entry<String, String> type : types.entrySet()) {
            String id = type.getKey();
            int colon = id.indexOf(':');
            String namespace = colon > 0 ? id.substring(0, colon) : "unknown";

            byNamespace.computeIfAbsent(namespace, key -> new TreeMap<>()).put(id, type.getValue());
        }

        for (Map.Entry<String, Map<String, String>> namespaceEntry : byNamespace.entrySet()) {
            String namespace = namespaceEntry.getKey();

            OptionGroup.Builder builder = OptionGroup.createBuilder()
                    .setId(OPTIONS.group(kind + "." + namespace))
                    .setName(TextComponent.translatable(LANG + "group." + kind, "minecraft".equals(namespace) ? "Minecraft" : namespace));

            boolean showNamespace = !"minecraft".equals(namespace);

            for (Map.Entry<String, String> type : namespaceEntry.getValue().entrySet()) {
                String id = type.getKey();
                String label = showNamespace ? type.getValue() + " (" + namespace + ")" : type.getValue();

                builder.add(OptionImpl.createBuilder(boolean.class, OPTIONS.storage())
                        .setId(OPTIONS.option(kind + ".type." + id, boolean.class))
                        .setName(TextComponent.literal(label))
                        .setTooltip(TextComponent.literal(id))
                        .setControl(TickBoxControl::new)
                        .setBinding(
                                (config, value) -> setter.set(settings, id, value),
                                config -> getter.test(settings, id))
                        .setEnabledPredicate(enabled)
                        .build());
            }

            groups.add(builder.build());
        }
    }

    // Setter of the enabled state of a type.
    @FunctionalInterface
    private interface TypeSetter {
        // Stores the enabled state of the type.
        void set(LightSourceSettings settings, String id, boolean enabled);
    }
}
