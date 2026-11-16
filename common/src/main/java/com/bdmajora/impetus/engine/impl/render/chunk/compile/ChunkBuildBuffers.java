package com.bdmajora.impetus.engine.impl.render.chunk.compile;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import lombok.Getter;
import com.bdmajora.impetus.engine.impl.gl.util.VertexRange;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;
import com.bdmajora.impetus.engine.impl.render.chunk.RenderPassConfiguration;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.buffers.BakedChunkModelBuilder;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.buffers.ChunkModelBuilder;
import com.bdmajora.impetus.engine.impl.render.chunk.data.BuiltRenderSectionData;
import com.bdmajora.impetus.engine.impl.render.chunk.data.BuiltSectionMeshParts;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.material.Material;
import com.bdmajora.impetus.engine.impl.common.util.NativeBuffer;
import com.bdmajora.impetus.engine.impl.render.chunk.sorting.SortState;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.ChunkVertexEncoder;

import java.util.*;

// Scratch buffers of a worker for building the meshes of a section per render pass.
public final class ChunkBuildBuffers {
    // Facings uploaded for a sorted pass.
    private static final ModelQuadFacing[] ONLY_UNASSIGNED = new ModelQuadFacing[] { ModelQuadFacing.UNASSIGNED };
    // Builders by render pass.
    private final Reference2ReferenceOpenHashMap<TerrainRenderPass, BakedChunkModelBuilder> builders = new Reference2ReferenceOpenHashMap<>();

    // Configuration of the render passes.
    @Getter
    private final RenderPassConfiguration<?> renderPassConfiguration;

    // Data of the section being built.
    private BuiltRenderSectionData renderData;
    // Index of the section being built.
    private int sectionIndex;

    // Creates buffers for a configuration.
    public ChunkBuildBuffers(RenderPassConfiguration<?> configuration) {
        this.renderPassConfiguration = configuration;
    }

    // Starts a new section in every pass builder.
    public void init(BuiltRenderSectionData renderData, int sectionIndex) {
        this.renderData = renderData;
        this.sectionIndex = sectionIndex;
        for (var builder : this.builders.values()) {
            builder.begin(renderData, sectionIndex);
        }
    }

    // Returns the data of the section being built.
    public BuiltRenderSectionData getSectionContextBundle() {
        return this.renderData;
    }

    // Returns the builder of the pass of a material.
    public ChunkModelBuilder get(Material material) {
        return this.get(material.pass);
    }

    // Creates the builder of a pass and rejects use before a section was started.
    private ChunkModelBuilder createBuilder(TerrainRenderPass pass) {
        Objects.requireNonNull(renderData, "Buffers have not been started");
        var vertexType = pass.vertexType();
        var builder = new BakedChunkModelBuilder(vertexType.createEncoder(), vertexType.getVertexFormat().getStride(), pass);
        builder.begin(renderData, sectionIndex);
        this.builders.put(pass, builder);
        return builder;
    }

    // Returns the builder of a pass and creates it when missing.
    public ChunkModelBuilder get(TerrainRenderPass pass) {
        var builder = this.builders.get(pass);
        return builder != null ? builder : createBuilder(pass);
    }

    // Returns the passes that have a builder.
    public Set<TerrainRenderPass> getBuilderPasses() {
        return this.builders.keySet();
    }

    // Creates the mesh of a pass from its builder or returns null when empty.
    public BuiltSectionMeshParts createMesh(TerrainRenderPass pass, float camX, float camY, float camZ) {
        var builder = this.builders.get(pass);

        if (builder == null || builder.isEmpty()) {
            return null;
        }

        var vertexRanges = new EnumMap<ModelQuadFacing, VertexRange>(ModelQuadFacing.class);
        int vertexCount = 0;

        ModelQuadFacing[] facingsToUpload = pass.isSorted() ? ONLY_UNASSIGNED : ModelQuadFacing.VALUES;
        SortState sortState = pass.isSorted() ? builder.getVertexBuffer(ModelQuadFacing.UNASSIGNED).getSortState() : null;

        // Lays out the ranges of every facing
        for (ModelQuadFacing facing : facingsToUpload) {
            var buffer = builder.getVertexBuffer(facing);

            if (buffer.isEmpty()) {
                continue;
            }

            vertexRanges.put(facing, new VertexRange(vertexCount, buffer.count()));
            vertexCount += buffer.count();
        }

        if (vertexCount == 0) {
            return null;
        }

        var vertexType = pass.vertexType();
        var primitiveType = pass.primitiveType();

        var mergedBuffer = new NativeBuffer(Math.multiplyExact(vertexCount, vertexType.getVertexFormat().getStride()));
        NativeBuffer mergedIndexBuffer = null;

        try {
            var mergedBufferBuilder = mergedBuffer.getDirectBuffer();

            // Copies the facings in layout order
            for (ModelQuadFacing facing : facingsToUpload) {
                var buffer = builder.getVertexBuffer(facing);

                if (!buffer.isEmpty()) {
                    mergedBufferBuilder.put(buffer.slice());
                }
            }

            mergedBufferBuilder.flip();

            if (pass.isSorted()) {
                int numPrimitives = vertexCount / primitiveType.getVerticesPerPrimitive();
                mergedIndexBuffer = new NativeBuffer(primitiveType.getIndexBufferSize(numPrimitives));

                // Sorts for the first camera position
                primitiveType.generateSortedIndexBuffer(mergedIndexBuffer.getDirectBuffer(), numPrimitives, sortState, camX, camY, camZ);
            }
        } catch (Throwable failure) {
            mergedBuffer.free();
            if (mergedIndexBuffer != null) {
                mergedIndexBuffer.free();
            }
            throw failure;
        }

        return new BuiltSectionMeshParts(mergedBuffer, mergedIndexBuffer, sortState, vertexRanges);
    }

    // Frees and forgets every builder.
    public void destroy() {
        this.sectionIndex = 0;
        this.renderData = null;
        for (var builder : this.builders.values()) {
            builder.destroy();
        }
        this.builders.clear();
    }
}
