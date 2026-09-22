package com.bdmajora.impetus.engine.impl.render.chunk;

import com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.material.Material;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

public record RenderPassConfiguration<R>(Map<R, Material> chunkRenderTypeToMaterialMap,
                                      Map<R, Collection<TerrainRenderPass>> vanillaRenderStages,
                                      Material defaultSolidMaterial,
                                      Material defaultCutoutMippedMaterial,
                                      Material defaultTranslucentMaterial) {
    // Platform render type to material
    public Material getMaterialForRenderType(Object type) {
        Objects.requireNonNull(type, "Null render type provided");
        var material = chunkRenderTypeToMaterialMap.get(type);
        if (material == null) {
            throw new IllegalArgumentException(type.toString());
        }
        return material;
    }

    // Every pass in draw order
    public Stream<TerrainRenderPass> getAllKnownRenderPasses() {
        return vanillaRenderStages().values().stream().flatMap(Collection::stream).distinct();
    }
}
