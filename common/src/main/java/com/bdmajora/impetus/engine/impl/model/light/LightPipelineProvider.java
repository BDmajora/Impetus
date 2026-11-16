package com.bdmajora.impetus.engine.impl.model.light;

import com.bdmajora.impetus.engine.impl.model.light.data.LightDataAccess;
import com.bdmajora.impetus.engine.impl.model.light.flat.FlatLightPipeline;
import com.bdmajora.impetus.engine.impl.model.light.smooth.SmoothLightPipeline;

import java.util.Objects;

// Holds the smooth and flat quad lighters.
public class LightPipelineProvider {
    // Lighter that blends the corners.
    private final LightPipeline smooth;
    // Lighter that uses one value per face.
    private final LightPipeline flat;
    // Light cache shared by both lighters.
    private final LightDataAccess lightData;

    // Creates both lighters and rejects a null cache or diffuse provider.
    public LightPipelineProvider(LightDataAccess cache, DiffuseProvider diffuseProvider, boolean useQuadNormalsForShading) {
        Objects.requireNonNull(cache, "cache");
        Objects.requireNonNull(diffuseProvider, "diffuseProvider");
        this.lightData = cache;
        this.smooth = new SmoothLightPipeline(cache, diffuseProvider, useQuadNormalsForShading);
        this.flat = new FlatLightPipeline(cache, diffuseProvider, useQuadNormalsForShading);
    }

    // Returns the smooth lighter for the smooth mode and the flat lighter otherwise.
    public LightPipeline getLighter(LightMode type) {
        return type == LightMode.SMOOTH ? this.smooth : this.flat;
    }

    // Returns the light cache shared by both lighters.
    public LightDataAccess getLightData() {
        return this.lightData;
    }

    // Resets both lighters.
    public void reset() {
        this.smooth.reset();
        this.flat.reset();
    }
}
