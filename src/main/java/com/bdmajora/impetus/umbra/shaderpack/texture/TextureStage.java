package com.bdmajora.impetus.umbra.shaderpack.texture;

import java.util.Optional;

// Pipeline stages a custom texture.<stage>.<name> directive can bind into.
public enum TextureStage {
    // The setup programs.
    SETUP,
    // The begin programs at the start of each frame.
    BEGIN,
    // The shadow composite programs after the shadow pass.
    SHADOWCOMP,
    // The prepare programs before the gbuffers.
    PREPARE,
    // The gbuffer and shadow programs.
    GBUFFERS_AND_SHADOW,
    // The deferred programs between opaque and translucent geometry.
    DEFERRED,
    // The composite programs and final.
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
