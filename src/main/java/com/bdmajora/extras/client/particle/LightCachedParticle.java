package com.bdmajora.extras.client.particle;

// Implemented on Particle by the light-cache mixin; the tick samples the light the render pass would have looked up
public interface LightCachedParticle {
    void impetus$sampleLight();
}
