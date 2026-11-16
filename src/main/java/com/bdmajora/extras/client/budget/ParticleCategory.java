package com.bdmajora.extras.client.budget;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleDigging;
import net.minecraft.client.particle.ParticleRain;

import java.util.Locale;

// Importance of a particle type.
public enum ParticleCategory {
    // Particle that may be skipped.
    COSMETIC,
    // Weather particle.
    WEATHER,
    // Particle that must stay.
    CRITICAL;

    // Returns the category of the particle class.
    static ParticleCategory classify(Class<? extends Particle> type) {
        if (ParticleRain.class.isAssignableFrom(type)) {
            return WEATHER;
        }
        if (ParticleDigging.class.isAssignableFrom(type)) {
            return CRITICAL;
        }

        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            String name = current.getName().toLowerCase(Locale.ROOT);
            if (name.startsWith("com.leclowndu93150.particlerain.particle.")) {
                return WEATHER;
            }
            if (name.startsWith("com.tominocz.fbp.") || name.startsWith("hantonik.fbp.") || hasBlockToken(name)) {
                return CRITICAL;
            }
        }

        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            String simple = current.getSimpleName().toLowerCase(Locale.ROOT);
            if (simple.contains("rain") || simple.contains("snow") || simple.contains("weather")) {
                return WEATHER;
            }
        }

        return COSMETIC;
    }

    // Returns whether the class name belongs to block particles.
    private static boolean hasBlockToken(String name) {
        return name.contains("terrainparticle") || name.contains("blockparticle")
                || name.contains("blockdust") || name.contains("blockstate")
                || name.contains("blockbreak") || name.contains("fancyblock") || name.contains("digging");
    }
}
