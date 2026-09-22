package com.bdmajora.dynamiclights;

import com.bdmajora.dynamiclights.client.DynamicLightHandlers;
import com.bdmajora.dynamiclights.client.DynamicLightsEngine;
import com.bdmajora.dynamiclights.client.TileEntityLightTicker;
import com.bdmajora.dynamiclights.client.item.ItemLightSources;
import net.minecraftforge.common.MinecraftForge;
import com.bdmajora.impetus.booter.util.PropertiesConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

// Dynamic Lights subsystem, LambDynLights' glowing entities and items on 1.12.2; sources are tracked client-side and folded into the lightmap, and mixins read options() at call time so the mode switch is live
public final class DynamicLights {
    public static final Logger LOGGER = LogManager.getLogger("Impetus/DynamicLights");

    private static final String FILE_NAME = "impetus-dynamiclights.cfg";

    private static volatile DynamicLightsConfig config;

    private DynamicLights() {
    }

    // Live options loaded on first use; called from mixin bodies on client, render and chunk-builder threads, though ImpetusVintage warms it during construction well before any run
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

    // The tracked light sources and the lightmap maths over them.
    public static DynamicLightsEngine engine() {
        return DynamicLightsEngine.get();
    }

    // Loads the config now rather than on the first mixin that asks for it.
    public static void initialize() {
        options();
    }

    // Registers the default handlers and the item light reload listener at client init, not construction: registerReloadListener fires immediately and the item registry is empty until then, so every definition would be dropped
    public static void onClientInit() {
        DynamicLightHandlers.registerDefaultHandlers();
        ItemLightSources.registerReloadListener();
        MinecraftForge.EVENT_BUS.register(TileEntityLightTicker.instance());
    }

    // Persists the current options. Safe to call before #initialize().
    public static void save() {
        options().writeChanges();
    }

    // config/<FILE_NAME> under the game directory
    private static File configFile() {
        return PropertiesConfig.configFile(LOGGER, FILE_NAME);
    }
}
