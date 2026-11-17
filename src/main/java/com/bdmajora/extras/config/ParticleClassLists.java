package com.bdmajora.extras.config;

import com.bdmajora.extras.client.particle.ParticleClassRegistry;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;

// Lists of particle classes named in the config.
final class ParticleClassLists {
    // Config category of the particle class lists.
    static final String CATEGORY = "particle_classes";
    // Empty default list.
    private static final String[] NONE = new String[0];
    // Particle classes the player turned off.
    private static final ListSetting<ParticleClassRegistry> DISABLED = new ListSetting<>("disabledClasses", NONE,
            "Particle classes the user has switched off",
            ParticleClassRegistry::getDisabledClassesArray, ParticleClassRegistry::loadDisabledClasses);
    // Particle classes seen so far.
    private static final ListSetting<ParticleClassRegistry> DISCOVERED = new ListSetting<>("discoveredClasses", NONE,
            "Cache of discovered particle classes; rebuilt automatically",
            ParticleClassRegistry::getDiscoveredClassesArray, ParticleClassRegistry::loadDiscoveredClasses);

    // Prevents instantiation
    private ParticleClassLists() {
    }

    // Loads the value.
    static void load(Configuration config) {
        ParticleClassRegistry registry = ParticleClassRegistry.getInstance();
        DISABLED.load(config, CATEGORY, registry);
        DISCOVERED.load(config, CATEGORY, registry);
    }

    // Saves the value.
    static void save(Configuration config) {
        ConfigCategory section = config.getCategory(CATEGORY);
        ParticleClassRegistry registry = ParticleClassRegistry.getInstance();
        registry.markClean();
        DISABLED.save(config, section, registry);
        DISCOVERED.save(config, section, registry);
    }
}
