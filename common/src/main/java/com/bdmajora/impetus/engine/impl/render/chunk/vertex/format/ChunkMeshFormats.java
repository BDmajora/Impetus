package com.bdmajora.impetus.engine.impl.render.chunk.vertex.format;

import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.impl.CompactChunkVertex;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.impl.VanillaLikeChunkVertex;

// Vertex formats of the terrain.
public class ChunkMeshFormats {
    // Format with packed vertices.
    public static final ChunkVertexType COMPACT = new CompactChunkVertex();
    // Format close to the vanilla layout.
    public static final ChunkVertexType VANILLA_LIKE = new VanillaLikeChunkVertex();
}
