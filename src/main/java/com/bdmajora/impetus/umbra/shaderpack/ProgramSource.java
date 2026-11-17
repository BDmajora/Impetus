package com.bdmajora.impetus.umbra.shaderpack;

import java.util.Optional;

// Stage sources of one program.
public final class ProgramSource {
    // Most compute variants of a program.
    public static final int MAX_COMPUTE_VARIANTS = 27;

    // Name of the program.
    private final String name;
    // Vertex source.
    private final String vertexSource;
    // Geometry source.
    private final String geometrySource;
    // Tessellation control source.
    private final String tessControlSource;
    // Tessellation evaluation source.
    private final String tessEvalSource;
    // Fragment source.
    private final String fragmentSource;
    // Compute sources.
    private final String[] computeSources;
    // Mesh source.
    private final String meshSource;
    // Mesh fragment source.
    private final String meshFragmentSource;

    // Creates a program source from its name.
    public ProgramSource(String name,
                         // Vertex source.
                         String vertexSource,
                         // Geometry source.
                         String geometrySource,
                         // Tess control source.
                         String tessControlSource,
                         // Tess eval source.
                         String tessEvalSource,
                         // Fragment source.
                         String fragmentSource,
                         String[] computeSources) {
        this(name, vertexSource, geometrySource, tessControlSource, tessEvalSource, fragmentSource, computeSources, null, null);
    }

    // Creates a program source from its name.
    public ProgramSource(String name,
                         // Vertex source.
                         String vertexSource,
                         // Geometry source.
                         String geometrySource,
                         // Tess control source.
                         String tessControlSource,
                         // Tess eval source.
                         String tessEvalSource,
                         // Fragment source.
                         String fragmentSource,
                         // Compute sources.
                         String[] computeSources,
                         // Mesh source.
                         String meshSource,
                         String meshFragmentSource) {
        this.name = name;
        this.vertexSource = vertexSource;
        this.geometrySource = geometrySource;
        this.tessControlSource = tessControlSource;
        this.tessEvalSource = tessEvalSource;
        this.fragmentSource = fragmentSource;
        this.computeSources = computeSources == null ? new String[0] : computeSources.clone();
        this.meshSource = meshSource;
        this.meshFragmentSource = meshFragmentSource;
    }

    // Returns the name of a compute variant.
    public static String computeVariantName(String programName, int variant) {
        return variant == 0 ? programName : programName + "_" + (char) ('a' + variant - 1);
    }

    // Returns the name.
    public String getName() {
        return this.name;
    }

    // Returns the vertex source.
    public Optional<String> getVertexSource() {
        return Optional.ofNullable(this.vertexSource);
    }

    // Returns the geometry source.
    public Optional<String> getGeometrySource() {
        return Optional.ofNullable(this.geometrySource);
    }

    // Returns the tess control source.
    public Optional<String> getTessControlSource() {
        return Optional.ofNullable(this.tessControlSource);
    }

    // Returns the tess eval source.
    public Optional<String> getTessEvalSource() {
        return Optional.ofNullable(this.tessEvalSource);
    }

    // Returns the fragment source.
    public Optional<String> getFragmentSource() {
        return Optional.ofNullable(this.fragmentSource);
    }

    // Returns the mesh source.
    public Optional<String> getMeshSource() {
        return Optional.ofNullable(this.meshSource);
    }

    // Returns the mesh fragment source.
    public Optional<String> getMeshFragmentSource() {
        return Optional.ofNullable(this.meshFragmentSource);
    }

    // Returns whether the program has mesh and fragment stages for Nvidium.
    public boolean hasMeshStages() {
        return this.meshSource != null && this.meshFragmentSource != null;
    }

    // Returns the compute source.
    public Optional<String> getComputeSource() {
        return Optional.ofNullable(this.computeSources.length == 0 ? null : this.computeSources[0]);
    }

    // Returns the compute sources.
    public String[] getComputeSources() {
        return this.computeSources.clone();
    }

    // Returns whether the program has any compute stage.
    public boolean hasComputeSource() {
        for (String source : this.computeSources) {
            if (source != null) {
                return true;
            }
        }
        return false;
    }

    // Returns whether the program has both vertex and fragment stages.
    public boolean isValid() {
        return (this.vertexSource != null && this.fragmentSource != null) || hasComputeSource();
    }

    // Returns whether the program has vertex and fragment stages.
    public boolean hasRasterStages() {
        return this.vertexSource != null && this.fragmentSource != null;
    }
}
