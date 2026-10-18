package com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.impl;

import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.ChunkVertexType;

// A vertex the mesh-shader backend can store: positions packed in MeshChunkVertex's first six bytes, so the packer and section bounds read every layout alike, and a stride of whole uvec4s the shaders fetch directly
public interface MeshVertexLayout extends ChunkVertexType {
}
