package com.bdmajora.impetus.engine.impl.render.chunk.shader;

import com.bdmajora.impetus.engine.impl.render.chunk.fog.FogService;

// Services that the chunk shader components read.
public record ChunkShaderEnvironment(FogService fogService) {
}
