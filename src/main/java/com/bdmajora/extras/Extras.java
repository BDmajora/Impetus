package com.bdmajora.extras;

import com.bdmajora.impetus.core.PropertiesConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

// Entry point of the Extras module.
public final class Extras {
    // Constant logger.
    public static final Logger LOGGER = LogManager.getLogger("Impetus/Extras");

    // Constant file name.
    private static final String FILE_NAME = "impetus-extras.cfg";

    // Choice of the config option.
    private static volatile ExtrasConfig config;

    // Prevents instantiation
    private Extras() {
    }

    // Handles the options.
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

    // Handles the config file.
    private static File configFile() {
        return PropertiesConfig.configFile(LOGGER, FILE_NAME);
    }
}
