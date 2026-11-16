package com.bdmajora.impetus.engine.impl.render.chunk.compile.buffers;

import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;
import com.bdmajora.impetus.engine.impl.render.chunk.data.BuiltRenderSectionData;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.builder.ChunkMeshBufferBuilder;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.ChunkVertexEncoder;

// Target of the vertices of a section.
public interface ChunkModelBuilder {
    // Returns the buffer for a facing.
    ChunkMeshBufferBuilder getVertexBuffer(ModelQuadFacing facing);

    // Returns the data of the section being built.
    BuiltRenderSectionData getSectionContextBundle();

    // Returns the vertex encoder.
    ChunkVertexEncoder getEncoder();
}
