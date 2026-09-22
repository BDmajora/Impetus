package com.bdmajora.impetus.engine.impl.render.chunk.compile.buffers;

import lombok.Getter;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;
import com.bdmajora.impetus.engine.impl.render.chunk.data.BuiltRenderSectionData;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.builder.ChunkMeshBufferBuilder;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.ChunkVertexEncoder;

import java.util.Objects;

public class BakedChunkModelBuilder implements ChunkModelBuilder {
    private final ChunkMeshBufferBuilder[] vertexBuffers;
    private final boolean splitBySide;
    @Getter
    private final ChunkVertexEncoder encoder;

    private BuiltRenderSectionData renderData;

    public BakedChunkModelBuilder(ChunkVertexEncoder encoder, int stride, TerrainRenderPass pass) {
        var vertexBuffers = new ChunkMeshBufferBuilder[ModelQuadFacing.COUNT];
        boolean sorted = pass.isSorted();

        // A sorted pass writes everything through the UNASSIGNED buffer, so the six per-facing buffers (64 KiB each, growing) are never allocated for it
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

    // The buffer for one facing, so faces stay grouped for culling
    @Override
    public ChunkMeshBufferBuilder getVertexBuffer(ModelQuadFacing facing) {
        Objects.requireNonNull(this.renderData, "Builder has not been started");
        return splitBySide ? this.vertexBuffers[facing.ordinal()] : this.vertexBuffers[ModelQuadFacing.UNASSIGNED.ordinal()];
    }

    // The section data being filled
    @Override
    public BuiltRenderSectionData getSectionContextBundle() {
        return this.renderData;
    }

    // Frees every facing buffer
    public void destroy() {
        for (ChunkMeshBufferBuilder builder : this.vertexBuffers) {
            if(builder != null) {
                builder.destroy();
            }
        }
    }

    // Resets the buffers for a new section
    public void begin(BuiltRenderSectionData renderData, int sectionIndex) {
        this.renderData = renderData;

        for (var vertexBuffer : this.vertexBuffers) {
            if(vertexBuffer != null) {
                vertexBuffer.start(sectionIndex);
            }
        }
    }

    // No vertices written on any facing
    public boolean isEmpty() {
        for (var vertexBuffer : this.vertexBuffers) {
            if (vertexBuffer != null && !vertexBuffer.isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
