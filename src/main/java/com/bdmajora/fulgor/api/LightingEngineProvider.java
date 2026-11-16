package com.bdmajora.fulgor.api;

import com.bdmajora.fulgor.lighting.LightingEngine;

// Contract of the lighting engine provider.
public interface LightingEngineProvider {
    // Returns the lighting engine.
    LightingEngine fulgor$getLightingEngine();
}
