package com.bdmajora.extras.client.particle;

// Particle that caches its light.
public interface LightCachedParticle {
    // Reads the light at the position of the particle.
    void impetus$sampleLight();
}
