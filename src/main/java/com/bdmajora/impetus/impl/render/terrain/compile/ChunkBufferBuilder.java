package com.bdmajora.impetus.impl.render.terrain.compile;

import lombok.Getter;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.util.BlockRenderLayer;

// The vanilla buffer handed to BlockRendererDispatcher while meshing, so the dispatcher hook can tell a chunk build's call apart and find its context
@Getter
public class ChunkBufferBuilder extends BufferBuilder {
    private final VintageChunkBuildContext context;
    private final BlockRenderLayer layer;

    public ChunkBufferBuilder(int bufferSize, VintageChunkBuildContext context, BlockRenderLayer layer) {
        super(bufferSize);
        this.context = context;
        this.layer = layer;
    }
}
