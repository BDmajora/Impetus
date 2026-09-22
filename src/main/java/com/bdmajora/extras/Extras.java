package com.bdmajora.extras;

import com.bdmajora.impetus.booter.util.PropertiesConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

// Sodium Extra's option set on 1.12.2; owns no rendering, so none of its mixins need coremod gating and all read options() live
public final class Extras {
    public static final Logger LOGGER = LogManager.getLogger("Impetus/Extras");

    private static final String FILE_NAME = "impetus-extras.cfg";

    private static volatile ExtrasConfig config;

    private Extras() {
    }

    // Called from mixin bodies on render/client threads, so first-call safety matters; ImpetusVintage warms it during construction well before that
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

    // Loads the config now rather than on the first mixin that asks for it
    public static void initialize() {
        options();
    }

    // Persists the current options; safe to call before initialize()
    public static void save() {
        options().writeChanges();
    }

    // config/<FILE_NAME> under the game directory
    private static File configFile() {
        return PropertiesConfig.configFile(LOGGER, FILE_NAME);
    }
}
