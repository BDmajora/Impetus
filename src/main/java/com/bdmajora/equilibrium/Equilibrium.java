package com.bdmajora.equilibrium;

import com.bdmajora.equilibrium.config.EquilibriumConfig;
import com.bdmajora.equilibrium.config.EquilibriumOptions;
import com.bdmajora.equilibrium.config.Option;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

// Entry points of the general performance patches.
public final class Equilibrium {
    // Logger of the module.
    public static final Logger LOGGER = LogManager.getLogger("Equilibrium");

    // Loaded settings or null before first use.
    private static volatile EquilibriumConfig config;

    // Prevents instantiation
    private Equilibrium() {
    }

    // Returns the settings and loads them on first use.
    public static EquilibriumConfig config() {
        EquilibriumConfig loaded = config;

        if (loaded == null) {
            synchronized (Equilibrium.class) {
                loaded = config;

                if (loaded == null) {
                    loaded = EquilibriumConfig.load(EquilibriumConfig.defaultFile());
                    config = loaded;
                }
            }
        }

        return loaded;
    }

    // Replaces the settings.
    public static void setConfig(EquilibriumConfig loaded) {
        config = loaded;
    }

    // Returns whether the named switch and its parents are on.
    public static boolean isEnabled(String optionName) {
        return config().isOptionEnabled(optionName);
    }

    // Returns the active and disabled switches as text lines.
    public static List<String> statistics() {
        EquilibriumConfig config = config();

        List<String> disabledByUser = new ArrayList<>();
        List<String> disabledByMod = new ArrayList<>();
        List<String> disabledByDependency = new ArrayList<>();
        int enabled = 0;

        for (EquilibriumOptions.Entry entry : EquilibriumOptions.entries().values()) {
            Option option = config.getOption(entry.name());

            if (option == null) {
                continue;
            }

            if (option.isEnabledRecursive(config)) {
                enabled++;
            } else if (option.isUserDefined()) {
                disabledByUser.add(entry.name());
            } else if (option.isModDefined()) {
                disabledByMod.add(entry.name() + " (" + String.join(", ", option.getDefiningMods()) + ")");
            } else if (entry.enabledByDefault()) {
                disabledByDependency.add(entry.name());
            }
        }

        List<String> lines = new ArrayList<>();
        lines.add("Equilibrium: " + enabled + " of " + config.getOptionCount() + " optimizations active");

        appendGroup(lines, "Disabled in the config file", disabledByUser);
        appendGroup(lines, "Disabled for mod compatibility", disabledByMod);
        appendGroup(lines, "Disabled because a dependency is off", disabledByDependency);

        return lines;
    }

    // Adds a heading and its members when the list is not empty.
    private static void appendGroup(List<String> lines, String heading, List<String> members) {
        if (members.isEmpty()) {
            return;
        }

        lines.add("  " + heading + ":");

        for (String member : members) {
            lines.add("    " + member);
        }
    }

    // Returns the line shown in the debug overlay.
    public static String debugOverlayLine() {
        EquilibriumConfig config = config();

        int enabled = 0;

        for (EquilibriumOptions.Entry entry : EquilibriumOptions.entries().values()) {
            Option option = config.getOption(entry.name());

            if (option != null && option.isEnabledRecursive(config)) {
                enabled++;
            }
        }

        return String.format("Equilibrium: %d/%d active (/equilibrium for detail)",
                enabled, config.getOptionCount());
    }
}
