package com.bdmajora.impetus.engine.impl.model.compat;

// Source of model quads for one backend.
public interface ModdedModelPipeline {
    // Returns the backend of the pipeline.
    ModelPipelineBackend backend();

    // Returns whether the pipeline can be used.
    boolean isAvailable();

    // Returns whether the pipeline handles the model.
    default boolean canHandle(Object model) {
        return false;
    }

    // Returns the reason the pipeline is missing or an empty text.
    default String getUnavailableReason() {
        return "";
    }
}
