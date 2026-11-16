package com.bdmajora.impetus.engine.impl.model.compat;

// Model pipeline of baked vanilla and Forge models.
public enum VanillaForgeModelPipeline implements ModdedModelPipeline {
    // Only instance.
    INSTANCE;

    // Returns the backend of the pipeline.
    @Override
    public ModelPipelineBackend backend() {
        return ModelPipelineBackend.VANILLA_FORGE;
    }

    // Returns true for every call.
    @Override
    public boolean isAvailable() {
        return true;
    }

    // Returns true for every model.
    @Override
    public boolean canHandle(Object model) {
        return true;
    }
}
