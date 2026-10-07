package com.bdmajora.impetus.engine.impl.render.mesh;

import com.bdmajora.impetus.engine.impl.gl.shader.ShaderConstants;
import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions;
import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions.MeshStatistics;
import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions.MeshTranslucencySorting;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.impl.MeshChunkVertex;

// The mesh backend's settings frozen for one section manager's lifetime, since several of them are compiled into the shaders
public record MeshTerrainConfig(boolean temporalCoherence,
                                MeshTranslucencySorting sorting,
                                MeshStatistics statistics,
                                boolean automaticMemory,
                                int maxGeometryMemoryMB,
                                int regionKeepDistance,
                                int renderDistance) {
    // The keep distance at which nothing is evicted for distance at all; kept here because the options class serialises every non-private field
    public static final int MAX_KEEP_DISTANCE = 256;

    // Snapshots the options for a renderer built at the given render distance
    public static MeshTerrainConfig from(ImpetusGameOptions.MeshTerrainSettings settings, int renderDistance) {
        return new MeshTerrainConfig(settings.temporalCoherence, settings.translucencySorting, settings.statistics,
                settings.automaticMemory, settings.maxGeometryMemory, settings.regionKeepDistance, renderDistance);
    }

    // Whether geometry outlives its CPU section, i.e. sections removed for distance stay drawn until the keep distance or memory evicts them
    public boolean keepsBeyondRenderDistance() {
        return this.regionKeepDistance > this.renderDistance;
    }

    // Whether regions are never evicted for distance at all, only for memory
    public boolean keepsEverything() {
        return this.regionKeepDistance >= MAX_KEEP_DISTANCE;
    }

    // Defines every mesh program shares: the vertex decode scale, the statistics counters and the sorting variants
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
