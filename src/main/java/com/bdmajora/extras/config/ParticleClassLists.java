package com.bdmajora.extras.config;

import com.bdmajora.extras.client.particle.ParticleClassRegistry;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;

// Disabled and discovered particle class lists of the registry stored under one Forge category
final class ParticleClassLists {
    // Name of the Forge category
    static final String CATEGORY = "particle_classes";
    // Empty default of both lists
    private static final String[] NONE = new String[0];
    // Classes switched off in the options screen
    private static final ListSetting<ParticleClassRegistry> DISABLED = new ListSetting<>("disabledClasses", NONE,
            "Particle classes the user has switched off",
            ParticleClassRegistry::getDisabledClassesArray, ParticleClassRegistry::loadDisabledClasses);
    // Cache of every particle class seen so far
    private static final ListSetting<ParticleClassRegistry> DISCOVERED = new ListSetting<>("discoveredClasses", NONE,
            "Cache of discovered particle classes; rebuilt automatically",
            ParticleClassRegistry::getDiscoveredClassesArray, ParticleClassRegistry::loadDiscoveredClasses);

    // Prevents instantiation
    private ParticleClassLists() {
    }

    // Reads both lists into the registry
    static void load(Configuration config) {
        ParticleClassRegistry registry = ParticleClassRegistry.getInstance();
        DISABLED.load(config, CATEGORY, registry);
        DISCOVERED.load(config, CATEGORY, registry);
    }

    // Clears the dirty flag of the registry before it writes both lists
    static void save(Configuration config) {
        ConfigCategory section = config.getCategory(CATEGORY);
        ParticleClassRegistry registry = ParticleClassRegistry.getInstance();
        registry.markClean();
        DISABLED.save(config, section, registry);
        DISCOVERED.save(config, section, registry);
    }
}
