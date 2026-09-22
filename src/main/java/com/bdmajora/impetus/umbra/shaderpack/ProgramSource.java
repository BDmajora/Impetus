package com.bdmajora.impetus.umbra.shaderpack;

import java.util.Optional;

// The flattened GLSL for one program: mandatory vertex + fragment plus optional geometry/tessellation; IncludeProcessor has resolved every #include, but #version normalisation and #define injection happen later at GL build since they depend on the driver and options
public final class ProgramSource {
    // Iris's compute-variant limit: the unsuffixed .csh plus _a through _z, so 1 + 26
    public static final int MAX_COMPUTE_VARIANTS = 27;

    private final String name;
    private final String vertexSource;
    private final String geometrySource;
    private final String tessControlSource;
    private final String tessEvalSource;
    private final String fragmentSource;
    // The program's compute stages: index 0 is <name>.csh, 1..26 are _a through _z (an Iris extension, Photon's deferred4_a.csh generates the skylight SH); empty when none, and entries may be null since a pack can ship _a and _c without _b
    private final String[] computeSources;

    public ProgramSource(String name,
                         String vertexSource,
                         String geometrySource,
                         String tessControlSource,
                         String tessEvalSource,
                         String fragmentSource,
                         String[] computeSources) {
        this.name = name;
        this.vertexSource = vertexSource;
        this.geometrySource = geometrySource;
        this.tessControlSource = tessControlSource;
        this.tessEvalSource = tessEvalSource;
        this.fragmentSource = fragmentSource;
        this.computeSources = computeSources == null ? new String[0] : computeSources.clone();
    }

    // The name a compute variant is compiled and logged under: variant 0 is unsuffixed, 1..26 append _a through _z
    public static String computeVariantName(String programName, int variant) {
        return variant == 0 ? programName : programName + "_" + (char) ('a' + variant - 1);
    }

    // Program id, e.g. gbuffers_terrain
    public String getName() {
        return this.name;
    }

    // .vsh, if present
    public Optional<String> getVertexSource() {
        return Optional.ofNullable(this.vertexSource);
    }

    // .gsh, if present
    public Optional<String> getGeometrySource() {
        return Optional.ofNullable(this.geometrySource);
    }

    // .tcs, if present
    public Optional<String> getTessControlSource() {
        return Optional.ofNullable(this.tessControlSource);
    }

    // .tes, if present
    public Optional<String> getTessEvalSource() {
        return Optional.ofNullable(this.tessEvalSource);
    }

    // .fsh, if present
    public Optional<String> getFragmentSource() {
        return Optional.ofNullable(this.fragmentSource);
    }

    // The unsuffixed compute stage alone, which is what the shadowcomp and composite compute passes run
    public Optional<String> getComputeSource() {
        return Optional.ofNullable(this.computeSources.length == 0 ? null : this.computeSources[0]);
    }

    // Every compute stage indexed by variant (0 unsuffixed, 1..26 for _a.._z); may be empty and contain nulls, so index defensively
    public String[] getComputeSources() {
        return this.computeSources.clone();
    }

    // True when at least one compute stage exists, suffixed or not
    public boolean hasComputeSource() {
        for (String source : this.computeSources) {
            if (source != null) {
                return true;
            }
        }
        return false;
    }

    // A program needs BOTH vertex and fragment stages; OptiFine treats one without the other as malformed and falls back to the parent, which ProgramSet.get reproduces
    public boolean isValid() {
        // A compute-only program (shadowcomp.csh, deferred4_a.csh) is valid without vertex/fragment stages.
        return (this.vertexSource != null && this.fragmentSource != null) || hasComputeSource();
    }

    // True when the vertex+fragment pair needed to raster is present; distinct from isValid in intent, since a compute-only program legitimately cannot draw
    public boolean hasRasterStages() {
        return this.vertexSource != null && this.fragmentSource != null;
    }
}
