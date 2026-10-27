package com.bdmajora.extras.config;

import com.bdmajora.extras.client.particle.ParticleClassRegistry;
import com.bdmajora.testing.Mixins;

import java.util.Collection;
import java.util.Map;

// Test helper that empties the shared particle class registry
public final class RegistryState {
    // Prevents instantiation
    private RegistryState() {
    }

    // Clears every disabled and discovered and seen class and the dirty flag
    public static void clear() {
        ParticleClassRegistry registry = ParticleClassRegistry.getInstance();
        registry.loadDisabledClasses(new String[0]);
        Mixins.<Map<?, ?>>get(registry, "discoveredClasses").clear();
        Mixins.<Map<?, ?>>get(registry, "classModIds").clear();
        Mixins.<Collection<?>>get(registry, "seenClasses").clear();
        registry.markClean();
    }
}
