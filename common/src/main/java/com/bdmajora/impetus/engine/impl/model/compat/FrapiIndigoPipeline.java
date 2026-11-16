package com.bdmajora.impetus.engine.impl.model.compat;

// Model pipeline of the Fabric renderer API that is never present here.
public enum FrapiIndigoPipeline implements ModdedModelPipeline {
    // Only instance.
    INSTANCE;

    // Reason text of the missing pipeline.
    private static final String UNAVAILABLE_REASON = "Fabric Renderer API/Indigo is not present on this Minecraft target.";

    // Returns the backend of the pipeline.
    @Override
    public ModelPipelineBackend backend() {
        return ModelPipelineBackend.FRAPI_INDIGO;
    }

    // Returns false for every call.
    @Override
    public boolean isAvailable() {
        return false;
    }

    // Returns the reason the pipeline is missing.
    @Override
    public String getUnavailableReason() {
        return UNAVAILABLE_REASON;
    }
}
