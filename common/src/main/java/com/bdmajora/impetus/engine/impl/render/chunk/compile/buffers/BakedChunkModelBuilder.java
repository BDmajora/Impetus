package com.bdmajora.impetus.engine.impl.render.chunk.compile.buffers;

import lombok.Getter;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;
import com.bdmajora.impetus.engine.impl.render.chunk.data.BuiltRenderSectionData;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.builder.ChunkMeshBufferBuilder;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.ChunkVertexEncoder;

import java.util.Objects;

// Model builder that writes vertices into one buffer per facing.
public class BakedChunkModelBuilder implements ChunkModelBuilder {
    // Buffers by facing ordinal.
    private final ChunkMeshBufferBuilder[] vertexBuffers;
    // Whether vertices go to the buffer of their facing.
    private final boolean splitBySide;
    // Encoder of the vertices.
    @Getter
    private final ChunkVertexEncoder encoder;

    // Data of the section being built.
    private BuiltRenderSectionData renderData;

    // Creates the buffers for a pass and rejects a null encoder or pass.
    public BakedChunkModelBuilder(ChunkVertexEncoder encoder, int stride, TerrainRenderPass pass) {
        Objects.requireNonNull(encoder, "encoder");
        Objects.requireNonNull(pass, "pass");
        var vertexBuffers = new ChunkMeshBufferBuilder[ModelQuadFacing.COUNT];
        boolean sorted = pass.isSorted();

        // Allocates only the unassigned buffer for a sorted pass
        for (int facing = 0; facing < ModelQuadFacing.COUNT; facing++) {
            boolean unassigned = facing == ModelQuadFacing.UNASSIGNED.ordinal();
            if (!sorted || unassigned) {
                vertexBuffers[facing] = new ChunkMeshBufferBuilder(encoder, stride, 64 * 1024, sorted && unassigned);
            }
        }

        this.encoder = encoder;
        this.vertexBuffers = vertexBuffers;
        this.splitBySide = !sorted;
    }

    // Returns the buffer of a facing or the unassigned buffer for a sorted pass.
    @Override
    public ChunkMeshBufferBuilder getVertexBuffer(ModelQuadFacing facing) {
        Objects.requireNonNull(this.renderData, "Builder has not been started");
        return splitBySide ? this.vertexBuffers[facing.ordinal()] : this.vertexBuffers[ModelQuadFacing.UNASSIGNED.ordinal()];
    }

    // Returns the data of the section being built.
    @Override
    public BuiltRenderSectionData getSectionContextBundle() {
        return this.renderData;
    }

    // Frees every buffer.
    public void destroy() {
        for (ChunkMeshBufferBuilder builder : this.vertexBuffers) {
            if(builder != null) {
                builder.destroy();
            }
        }
    }

    // Starts a new section in every buffer and rejects null data.
    public void begin(BuiltRenderSectionData renderData, int sectionIndex) {
        this.renderData = Objects.requireNonNull(renderData, "renderData");

        for (var vertexBuffer : this.vertexBuffers) {
            if(vertexBuffer != null) {
                vertexBuffer.start(sectionIndex);
            }
        }
    }

    // Returns whether no vertices were written.
    public boolean isEmpty() {
        for (var vertexBuffer : this.vertexBuffers) {
            if (vertexBuffer != null && !vertexBuffer.isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
