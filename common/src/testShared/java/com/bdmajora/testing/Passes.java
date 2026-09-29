package com.bdmajora.testing;

import com.bdmajora.impetus.engine.impl.render.chunk.RenderPassConfiguration;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.sorting.QuadPrimitiveType;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.material.Material;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.material.parameters.AlphaCutoffParameter;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.ChunkMeshFormats;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.ChunkVertexType;

import java.util.List;
import java.util.Map;

// Ready-made terrain passes and materials in the shapes the engine uses
public final class Passes {
    public static final TerrainRenderPass SOLID = pass("solid", false, false, ChunkMeshFormats.COMPACT);
    public static final TerrainRenderPass CUTOUT = pass("cutout", true, false, ChunkMeshFormats.COMPACT);
    public static final TerrainRenderPass TRANSLUCENT = pass("translucent", false, true, ChunkMeshFormats.COMPACT);
    public static final TerrainRenderPass WIDE = pass("wide", true, false, ChunkMeshFormats.VANILLA_LIKE);

    public static final Material SOLID_MATERIAL = new Material(SOLID, AlphaCutoffParameter.ZERO, true);
    public static final Material CUTOUT_MATERIAL = new Material(CUTOUT, AlphaCutoffParameter.ONE_TENTH, false);
    public static final Material TRANSLUCENT_MATERIAL = new Material(TRANSLUCENT, AlphaCutoffParameter.ZERO, true);

    // The three-pass configuration most engine tests build against, keyed by plain strings in place of platform render types
    public static final RenderPassConfiguration<String> CONFIG = new RenderPassConfiguration<>(
            Map.of("solid", SOLID_MATERIAL, "cutout", CUTOUT_MATERIAL, "translucent", TRANSLUCENT_MATERIAL),
            Map.of("solid", List.of(SOLID), "cutout", List.of(CUTOUT), "translucent", List.of(TRANSLUCENT)),
            SOLID_MATERIAL, CUTOUT_MATERIAL, TRANSLUCENT_MATERIAL);

    private Passes() {}

    public static TerrainRenderPass pass(String name, boolean discard, boolean translucent, ChunkVertexType vertexType) {
        return TerrainRenderPass.builder()
                .name(name)
                .pipelineState(TerrainRenderPass.PipelineState.DEFAULT)
                .fragmentDiscard(discard)
                .useReverseOrder(translucent)
                .useTranslucencySorting(translucent)
                .vertexType(vertexType)
                .primitiveType(QuadPrimitiveType.TRIANGULATED)
                .build();
    }
}
