package com.bdmajora.impetus.engine.impl.render.chunk.terrain;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Singular;
import lombok.experimental.Accessors;
import com.bdmajora.impetus.engine.impl.gl.attribute.GlVertexFormat;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.sorting.ChunkPrimitiveType;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.ChunkVertexType;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;

// One draw call over a subset of terrain geometry.
@Accessors(fluent = true)
@EqualsAndHashCode
public class TerrainRenderPass {
    // the friendly name of this render pass
    @Getter
    @EqualsAndHashCode.Exclude
    private final String name;

    // callback that sets up and clears the GPU pipeline state
    private final PipelineState pipelineState;

    // whether sections on this pass render farthest-to-nearest rather than nearest-to-farthest
    private final boolean useReverseOrder;
    // whether fragment alpha testing should be enabled for this render pass
    private final boolean fragmentDiscard;
    // Whether this pass uses translucency sorting.
    private final boolean useTranslucencySorting;
    // whether this render pass has no lightmap texture
    private final boolean hasNoLightmap;

    private final @NotNull ChunkPrimitiveType primitiveType;
    private final @NotNull ChunkVertexType vertexType;

    public record TessellationKey(GlVertexFormat vertexFormat, ChunkPrimitiveType primitiveType, boolean sorted) {}

    @Getter
    @EqualsAndHashCode.Exclude
    private final TessellationKey tessellationKey;

    private final Map<String, String> extraDefines;

    @Builder
    public TerrainRenderPass(String name,
                             PipelineState pipelineState,
                             boolean useReverseOrder,
                             boolean fragmentDiscard,
                             boolean useTranslucencySorting,
                             boolean hasNoLightmap,
                             @NotNull ChunkVertexType vertexType,
                             @NotNull ChunkPrimitiveType primitiveType,
                             @Singular Map<String, String> extraDefines) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Name not specified for terrain pass");
        }
        Objects.requireNonNull(vertexType);
        Objects.requireNonNull(primitiveType);

        this.name = name;
        this.pipelineState = pipelineState;
        this.useReverseOrder = useReverseOrder;
        this.fragmentDiscard = fragmentDiscard;
        this.useTranslucencySorting = useTranslucencySorting;
        this.hasNoLightmap = hasNoLightmap;
        this.primitiveType = primitiveType;
        this.vertexType = vertexType;
        this.tessellationKey = new TessellationKey(vertexType.getVertexFormat(), primitiveType, useTranslucencySorting);
        this.extraDefines = Map.copyOf(extraDefines);
    }

    // Returns whether the pass draws back to front.
    public boolean isReverseOrder() {
        return this.useReverseOrder;
    }

    // Whether sections in this pass keep sorted index buffers
    public boolean isSorted() {
        return this.useTranslucencySorting;
    }

    // Returns whether the pass draws without the lightmap.
    public boolean hasNoLightmap() {
        return this.hasNoLightmap;
    }

    // Applies the pass's GL state
    public void startDrawing() {
        this.pipelineState.setup();
    }

    // Restores GL state
    public void endDrawing() {
        this.pipelineState.clear();
    }

    // Returns whether the shader may discard fragments.
    public boolean supportsFragmentDiscard() {
        return this.fragmentDiscard;
    }

    // Quads or triangles
    public ChunkPrimitiveType primitiveType() {
        return this.primitiveType;
    }

    // The vertex format this pass builds
    public ChunkVertexType vertexType() {
        return this.vertexType;
    }

    // Pass-specific shader defines
    public Map<String, String> extraDefines() {
        return this.extraDefines;
    }

    // The pass name
    @Override
    public String toString() {
        return "TerrainRenderPass[name=" + this.name + "]";
    }

    public interface PipelineState {
        PipelineState DEFAULT = new PipelineState() {
            // Builder hook run at startDrawing
            @Override
            public void setup() {

            }

            // Builder hook run at endDrawing
            @Override
            public void clear() {

            }
        };

        void setup();
        void clear();
    }
}
