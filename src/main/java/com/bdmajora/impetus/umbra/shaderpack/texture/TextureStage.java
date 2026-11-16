package com.bdmajora.impetus.umbra.shaderpack.texture;

import java.util.Optional;

// Choices of the texture stage setting.
public enum TextureStage {
    // Value that selects setup.
    SETUP,
    // Value that selects begin.
    BEGIN,
    // Value that selects shadowcomp.
    SHADOWCOMP,
    // Value that selects prepare.
    PREPARE,
    // Value that selects gbuffers and shadow.
    GBUFFERS_AND_SHADOW,
    // Value that selects deferred.
    DEFERRED,
    // Value that selects composite and final.
    COMPOSITE_AND_FINAL;

    // Returns the stage of a name or empty.
    public static Optional<TextureStage> parse(String name) {
        switch (name) {
            case "setup":
                return Optional.of(SETUP);
            case "begin":
                return Optional.of(BEGIN);
            case "shadowcomp":
                return Optional.of(SHADOWCOMP);
            case "prepare":
                return Optional.of(PREPARE);
            case "gbuffers":
                return Optional.of(GBUFFERS_AND_SHADOW);
            case "deferred":
                return Optional.of(DEFERRED);
            case "composite":
                return Optional.of(COMPOSITE_AND_FINAL);
            default:
                return Optional.empty();
        }
    }
}
