package com.bdmajora.impetus.impl.render.terrain.compile;

import lombok.Getter;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.util.BlockRenderLayer;

// Vertex builder of one layer of a section.
@Getter
public class ChunkBufferBuilder extends BufferBuilder {
    // Build context of the section.
    private final VintageChunkBuildContext context;
    // Layer of the builder.
    private final BlockRenderLayer layer;

    // Creates a chunk buffer builder from its buffer size and context and layer.
    public ChunkBufferBuilder(int bufferSize, VintageChunkBuildContext context, BlockRenderLayer layer) {
        super(bufferSize);
        this.context = context;
        this.layer = layer;
    }
}
