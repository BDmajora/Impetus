package com.bdmajora.testing;

import com.bdmajora.impetus.engine.impl.render.chunk.ChunkRenderMatrices;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass;
import com.bdmajora.impetus.engine.impl.render.mesh.MeshDrawPhase;
import com.bdmajora.impetus.engine.impl.render.mesh.MeshTerrainShading;

import java.util.ArrayList;
import java.util.List;

// Mesh shading that records what the pipeline asks of it, standing in for a shader pack's translated programs
public final class RecordingShading implements MeshTerrainShading {
    public final List<String> calls = new ArrayList<>();
    // What the binds answer, and whether there are shadow programs
    public boolean bound = true;
    public boolean shadows = true;

    @Override
    public int vertexStride() {
        return 32;
    }

    @Override
    public boolean bindOpaque(MeshDrawPhase phase, TerrainRenderPass pass, ChunkRenderMatrices matrices) {
        this.calls.add("opaque " + phase);
        return this.bound;
    }

    @Override
    public boolean bindTranslucent(MeshDrawPhase phase, TerrainRenderPass pass, ChunkRenderMatrices matrices) {
        this.calls.add("translucent " + phase);
        return this.bound;
    }

    @Override
    public void finish() {
        this.calls.add("finish");
    }

    @Override
    public boolean hasShadowPrograms() {
        return this.shadows;
    }

    @Override
    public void delete() {
        this.calls.add("delete");
    }
}
