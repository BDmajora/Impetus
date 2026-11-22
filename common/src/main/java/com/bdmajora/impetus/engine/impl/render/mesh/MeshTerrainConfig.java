package com.bdmajora.impetus.engine.impl.render.mesh;

import com.bdmajora.impetus.engine.impl.gl.shader.ShaderConstants;
import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions;
import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions.MeshStatistics;
import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions.MeshTranslucencySorting;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.impl.MeshChunkVertex;

// Settings of the mesh backend that stay fixed for a section manager.
public record MeshTerrainConfig(boolean temporalCoherence,
                                MeshTranslucencySorting sorting,
                                MeshStatistics statistics,
                                boolean automaticMemory,
                                int maxGeometryMemoryMB,
                                int regionKeepDistance,
                                int renderDistance) {
    // Rejects a null sorting or statistics level.
    public MeshTerrainConfig {
        java.util.Objects.requireNonNull(sorting, "sorting");
        java.util.Objects.requireNonNull(statistics, "statistics");
    }

    // Keep distance at which nothing is evicted for distance.
    public static final int MAX_KEEP_DISTANCE = 256;

    // Copies the settings and limits negative numbers to zero.
    public static MeshTerrainConfig from(ImpetusGameOptions.MeshTerrainSettings settings, int renderDistance) {
        return from(settings, renderDistance, true);
    }

    public static MeshTerrainConfig from(ImpetusGameOptions.MeshTerrainSettings settings, int renderDistance, boolean sortTranslucentFaces) {
        java.util.Objects.requireNonNull(settings, "settings");
        var sorting = settings.translucencySorting;
        if (!sortTranslucentFaces && sorting == MeshTranslucencySorting.QUADS) {
            sorting = MeshTranslucencySorting.SECTIONS;
        }
        return new MeshTerrainConfig(settings.temporalCoherence, sorting, settings.statistics,
                settings.automaticMemory, Math.max(0, settings.maxGeometryMemory), Math.max(0, settings.regionKeepDistance), Math.max(0, renderDistance));
    }

    // Returns whether geometry stays drawn past the render distance.
    public boolean keepsBeyondRenderDistance() {
        return this.regionKeepDistance > this.renderDistance;
    }

    // Returns whether regions are only evicted for memory.
    public boolean keepsEverything() {
        return this.regionKeepDistance >= MAX_KEEP_DISTANCE;
    }

    // Returns the definitions that every mesh program shares.
    public ShaderConstants.Builder shaderConstants() {
        ShaderConstants.Builder builder = ShaderConstants.builder()
                .add("TEXTURE_MAX_SCALE", String.valueOf(MeshChunkVertex.TEXTURE_MAX_VALUE));

        if (this.statistics.includes(MeshStatistics.REGIONS)) {
            builder.add("STATISTICS_REGIONS");
        }
        if (this.statistics.includes(MeshStatistics.SECTIONS)) {
            builder.add("STATISTICS_SECTIONS");
        }
        if (this.statistics.includes(MeshStatistics.QUADS)) {
            builder.add("STATISTICS_QUADS");
        }
        if (this.sorting != MeshTranslucencySorting.NONE) {
            builder.add("SORT_SECTIONS");
        }
        if (this.sorting == MeshTranslucencySorting.QUADS) {
            builder.add("SORT_QUADS");
        }

        return builder;
    }
}
