package com.bdmajora.dynamiclights;

import com.bdmajora.dynamiclights.client.LightSourceSettings;
import com.bdmajora.impetus.impl.config.ConfigProperty;
import net.minecraftforge.common.config.Configuration;

import java.io.File;
import java.util.Arrays;
import java.util.List;

// Settings of the dynamic lights loaded from the configuration file.
public final class DynamicLightsConfig {
    // Category of the general settings.
    private static final String CAT_GENERAL = "general";
    // Category of the light source switches.
    private static final String CAT_SOURCES = "light_sources";

    // Update rate of the lights.
    public DynamicLightsMode mode = DynamicLightsMode.REALTIME;
    // Lighting mode of creepers.
    public ExplosiveLightingMode creeperLighting = ExplosiveLightingMode.FANCY;
    // Lighting mode of primed explosives.
    public ExplosiveLightingMode tntLighting = ExplosiveLightingMode.FANCY;

    // Whether the player lights the world.
    public boolean selfLightSource = true;
    // Whether other entities light the world.
    public boolean entitiesLightSource = true;
    // Whether block entities light the world.
    public boolean blockEntitiesLightSource = true;
    // Whether water sensitive sources go out when submerged.
    public boolean waterSensitiveCheck = true;
    // Whether the debug overlay shows the source count.
    public boolean showDebugInfo = false;

    // Properties that are read from and written to the file.
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

    // File backed configuration or null before loading.
    private Configuration config;

    // Returns the settings read from the file or the defaults when it cannot be read.
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

    // Reads every property and the disabled source lists.
    private void loadFrom(Configuration config) {
        properties.forEach(property -> property.load(config));

        LightSourceSettings settings = LightSourceSettings.getInstance();
        settings.loadDisabledEntities(config.getStringList("disabledEntities", CAT_SOURCES,
                new String[0], "Entity types the user has switched off"));
        settings.loadDisabledBlockEntities(config.getStringList("disabledBlockEntities", CAT_SOURCES,
                new String[0], "Block entity types the user has switched off"));
    }

    // Writes every property and the disabled source lists to the file.
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
