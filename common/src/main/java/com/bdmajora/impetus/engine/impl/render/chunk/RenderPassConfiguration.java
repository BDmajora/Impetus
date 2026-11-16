package com.bdmajora.impetus.engine.impl.render.chunk;

import com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.material.Material;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

// Materials and passes of the render types.
public record RenderPassConfiguration<R>(Map<R, Material> chunkRenderTypeToMaterialMap,
                                      Map<R, Collection<TerrainRenderPass>> vanillaRenderStages,
                                      Material defaultSolidMaterial,
                                      Material defaultCutoutMippedMaterial,
                                      Material defaultTranslucentMaterial) {
    // Rejects null maps or materials.
    public RenderPassConfiguration {
        Objects.requireNonNull(chunkRenderTypeToMaterialMap, "chunkRenderTypeToMaterialMap");
        Objects.requireNonNull(vanillaRenderStages, "vanillaRenderStages");
        Objects.requireNonNull(defaultSolidMaterial, "defaultSolidMaterial");
        Objects.requireNonNull(defaultCutoutMippedMaterial, "defaultCutoutMippedMaterial");
        Objects.requireNonNull(defaultTranslucentMaterial, "defaultTranslucentMaterial");
    }

    // Returns the material of a render type and rejects an unknown type.
    public Material getMaterialForRenderType(Object type) {
        Objects.requireNonNull(type, "Null render type provided");
        var material = chunkRenderTypeToMaterialMap.get(type);
        if (material == null) {
            throw new IllegalArgumentException(type.toString());
        }
        return material;
    }

    // Returns every distinct render pass.
    public Stream<TerrainRenderPass> getAllKnownRenderPasses() {
        return vanillaRenderStages().values().stream().flatMap(Collection::stream).distinct();
    }
}
