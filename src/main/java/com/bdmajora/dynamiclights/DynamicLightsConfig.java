package com.bdmajora.dynamiclights;

import com.bdmajora.dynamiclights.client.LightSourceSettings;
import com.bdmajora.impetus.impl.config.ConfigProperty;
import net.minecraftforge.common.config.Configuration;

import java.io.File;
import java.util.Arrays;
import java.util.List;

// Every Dynamic Lights setting, persisted to config/impetus-dynamiclights.cfg; everything lives in one declarative table so load/save cannot drift, enums are stored by ordinal so never reorder them
public final class DynamicLightsConfig {
    private static final String CAT_GENERAL = "general";
    private static final String CAT_SOURCES = "light_sources";

    public DynamicLightsMode mode = DynamicLightsMode.REALTIME;
    public ExplosiveLightingMode creeperLighting = ExplosiveLightingMode.FANCY;
    public ExplosiveLightingMode tntLighting = ExplosiveLightingMode.FANCY;

    public boolean selfLightSource = true;
    public boolean entitiesLightSource = true;
    public boolean blockEntitiesLightSource = true;
    public boolean waterSensitiveCheck = true;
    public boolean showDebugInfo = false;

    private final List<ConfigProperty> properties = Arrays.asList(
            ConfigProperty.bool(CAT_GENERAL, "selfLightSource", true,
                    "Light the world from what the player themselves is holding or wearing",
                    v -> selfLightSource = v, () -> selfLightSource),
            ConfigProperty.bool(CAT_GENERAL, "entitiesLightSource", true,
                    "Light the world from other entities: mobs, dropped items, item frames",
                    v -> entitiesLightSource = v, () -> entitiesLightSource),
            ConfigProperty.bool(CAT_GENERAL, "blockEntitiesLightSource", true,
                    "Light the world from block entities that register a handler through the API",
                    v -> blockEntitiesLightSource = v, () -> blockEntitiesLightSource),
            ConfigProperty.bool(CAT_GENERAL, "waterSensitiveCheck", true,
                    "Extinguish water-sensitive sources (torches, lava buckets) while submerged",
                    v -> waterSensitiveCheck = v, () -> waterSensitiveCheck),
            ConfigProperty.bool(CAT_GENERAL, "showDebugInfo", false,
                    "Add a tracked-source count line to the F3 overlay",
                    v -> showDebugInfo = v, () -> showDebugInfo),
            ConfigProperty.enumeration(CAT_GENERAL, "mode", DynamicLightsMode.values(), DynamicLightsMode.REALTIME,
                    "Update rate (0 = Off, 1 = Slow, 2 = Fast, 3 = Realtime)",
                    v -> mode = v, () -> mode),
            ConfigProperty.enumeration(CAT_GENERAL, "creeperLighting", ExplosiveLightingMode.values(), ExplosiveLightingMode.FANCY,
                    "Creeper flash lighting (0 = Off, 1 = Simple, 2 = Fancy)",
                    v -> creeperLighting = v, () -> creeperLighting),
            ConfigProperty.enumeration(CAT_GENERAL, "tntLighting", ExplosiveLightingMode.values(), ExplosiveLightingMode.FANCY,
                    "Primed TNT lighting (0 = Off, 1 = Simple, 2 = Fancy)",
                    v -> tntLighting = v, () -> tntLighting)
    );

    private Configuration config;

    // Reads the file, adding missing keys; a read failure yields defaults that are NOT written back, since overwriting an unparseable config would destroy the user's settings
    public static DynamicLightsConfig load(File file) {
        DynamicLightsConfig options = new DynamicLightsConfig();
        Configuration config = new Configuration(file);

        try {
            config.load();
            options.config = config;
            options.loadFrom(config);
            if (config.hasChanged()) {
                config.save();
            }
            return options;
        } catch (Exception e) {
            DynamicLights.LOGGER.error("Could not read {}, falling back to defaults", file, e);
            DynamicLightsConfig defaults = new DynamicLightsConfig();
            defaults.config = config;
            return defaults;
        }
    }

    // Reads every property, falling back to defaults for anything missing or out of range
    private void loadFrom(Configuration config) {
        properties.forEach(property -> property.load(config));

        LightSourceSettings settings = LightSourceSettings.getInstance();
        settings.loadDisabledEntities(config.getStringList("disabledEntities", CAT_SOURCES,
                new String[0], "Entity types the user has switched off"));
        settings.loadDisabledBlockEntities(config.getStringList("disabledBlockEntities", CAT_SOURCES,
                new String[0], "Block entity types the user has switched off"));
    }

    // Flushes every setting back to disk.
    public void writeChanges() {
        if (config == null) {
            return;
        }

        properties.forEach(property -> property.save(config));

        LightSourceSettings settings = LightSourceSettings.getInstance();
        config.get(CAT_SOURCES, "disabledEntities", new String[0]).set(settings.getDisabledEntitiesArray());
        config.get(CAT_SOURCES, "disabledBlockEntities", new String[0])
                .set(settings.getDisabledBlockEntitiesArray());

        config.save();
    }
}
