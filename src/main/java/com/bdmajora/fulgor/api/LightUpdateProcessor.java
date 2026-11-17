package com.bdmajora.fulgor.api;

// Contract of the light update processor.
public interface LightUpdateProcessor {
    // Marks the sections of the queued light changes for a rerender.
    void fulgor$processLightUpdates();
}
