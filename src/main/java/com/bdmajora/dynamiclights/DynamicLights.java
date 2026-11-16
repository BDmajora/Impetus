package com.bdmajora.dynamiclights;

import com.bdmajora.dynamiclights.client.DynamicLightHandlers;
import com.bdmajora.dynamiclights.client.DynamicLightsEngine;
import com.bdmajora.dynamiclights.client.TileEntityLightTicker;
import com.bdmajora.dynamiclights.client.item.ItemLightSources;
import net.minecraftforge.common.MinecraftForge;
import com.bdmajora.impetus.core.PropertiesConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

// Entry points of the dynamic lights module.
public final class DynamicLights {
    // Logger of the module.
    public static final Logger LOGGER = LogManager.getLogger("Impetus/DynamicLights");

    // Name of the configuration file.
    private static final String FILE_NAME = "impetus-dynamiclights.cfg";

    // Loaded settings or null before first use.
    private static volatile DynamicLightsConfig config;

    // Prevents instantiation
    private DynamicLights() {
    }

    // Returns the settings and loads them on first use.
    public static DynamicLightsConfig options() {
        DynamicLightsConfig loaded = config;
        if (loaded == null) {
            synchronized (DynamicLights.class) {
                loaded = config;
                if (loaded == null) {
                    loaded = DynamicLightsConfig.load(configFile());
                    config = loaded;
                }
            }
        }
        return loaded;
    }

    // Returns the engine that tracks light sources.
    public static DynamicLightsEngine engine() {
        return DynamicLightsEngine.get();
    }

    // Loads the settings.
    public static void initialize() {
        options();
    }

    // Registers the handlers and listeners of the client.
    public static void onClientInit() {
        DynamicLightHandlers.registerDefaultHandlers();
        ItemLightSources.registerReloadListener();
        MinecraftForge.EVENT_BUS.register(TileEntityLightTicker.instance());
    }

    // Writes the settings to the file.
    public static void save() {
        options().writeChanges();
    }

    // Returns the configuration file.
    private static File configFile() {
        return PropertiesConfig.configFile(LOGGER, FILE_NAME);
    }
}
