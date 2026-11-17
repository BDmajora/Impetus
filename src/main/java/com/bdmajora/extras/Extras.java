package com.bdmajora.extras;

import com.bdmajora.impetus.core.PropertiesConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

// Entry point of the Extras module.
public final class Extras {
    // Logger of the Extras module.
    public static final Logger LOGGER = LogManager.getLogger("Impetus/Extras");

    // Name of the Extras config file in the config folder.
    private static final String FILE_NAME = "impetus-extras.cfg";

    // Loaded config or null before the first use.
    private static volatile ExtrasConfig config;

    // Prevents instantiation
    private Extras() {
    }

    // Returns the loaded config and loads it on first use.
    public static ExtrasConfig options() {
        ExtrasConfig loaded = config;
        if (loaded == null) {
            synchronized (Extras.class) {
                loaded = config;
                if (loaded == null) {
                    loaded = ExtrasConfig.load(configFile());
                    config = loaded;
                }
            }
        }
        return loaded;
    }

    // Initializes the value.
    public static void initialize() {
        options();
    }

    // Saves the value.
    public static void save() {
        options().writeChanges();
    }

    // Returns the Extras config file in the config folder.
    private static File configFile() {
        return PropertiesConfig.configFile(LOGGER, FILE_NAME);
    }
}
