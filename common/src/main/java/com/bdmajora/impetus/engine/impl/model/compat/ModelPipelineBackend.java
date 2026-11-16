package com.bdmajora.impetus.engine.impl.model.compat;

// Model backends the engine knows.
public enum ModelPipelineBackend {
    VANILLA_FORGE("Vanilla/Forge baked model pipeline"),
    FRAPI_INDIGO("FRAPI/Indigo");

    // Name shown to the user.
    private final String displayName;

    // Creates a backend with a display name.
    ModelPipelineBackend(String displayName) {
        this.displayName = displayName;
    }

    // Returns the name shown to the user.
    public String getDisplayName() {
        return this.displayName;
    }
}
