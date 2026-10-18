package com.bdmajora.impetus.engine.impl.render.mesh.renderers;

import com.bdmajora.impetus.engine.impl.gl.shader.ShaderConstants;
import com.bdmajora.impetus.engine.impl.render.chunk.ChunkRenderMatrices;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.impl.MeshChunkVertex;
import com.bdmajora.impetus.engine.impl.render.mesh.MeshDrawPhase;
import com.bdmajora.impetus.engine.impl.render.mesh.MeshTerrainShading;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

// Nvidium's own terrain programs, which read everything from the scene block and so need no per-draw state
public class NvidiumShading implements MeshTerrainShading {
    private final TerrainRasterizer terrain;
    private final @Nullable TerrainRasterizer temporal;
    private final TranslucentRasterizer translucent;

    // Temporal is only built when the temporal pass is on; a program the driver rejects frees the ones already built
    public NvidiumShading(ShaderConstants constants, @Nullable ShaderConstants temporalConstants) {
        List<Runnable> built = new ArrayList<>();
        try {
            this.terrain = new TerrainRasterizer("mesh/terrain", constants);
            built.add(this.terrain::delete);
            this.temporal = temporalConstants != null ? new TerrainRasterizer("mesh/terrain_temporal", temporalConstants) : null;
            if (this.temporal != null) {
                built.add(this.temporal::delete);
            }
            this.translucent = new TranslucentRasterizer(constants);
        } catch (RuntimeException e) {
            built.forEach(Runnable::run);
            throw e;
        }
    }

    // The packed sixteen-byte terrain vertex
    @Override
    public int vertexStride() {
        return MeshChunkVertex.STRIDE;
    }

    // The main or temporal program; the shadow phase never reaches here, as there is no shadow program
    @Override
    public boolean bindOpaque(MeshDrawPhase phase, @Nullable TerrainRenderPass pass, ChunkRenderMatrices matrices) {
        TerrainRasterizer program = phase == MeshDrawPhase.TEMPORAL && this.temporal != null ? this.temporal : this.terrain;
        program.bind();
        return true;
    }

    // The translucent program
    @Override
    public boolean bindTranslucent(MeshDrawPhase phase, @Nullable TerrainRenderPass pass, ChunkRenderMatrices matrices) {
        this.translucent.bind();
        return true;
    }

    // Nothing beyond the program to undo
    @Override
    public void finish() {
    }

    // Only a shader pack has a shadow pass
    @Override
    public boolean hasShadowPrograms() {
        return false;
    }

    // Frees every program
    @Override
    public void delete() {
        this.terrain.delete();
        if (this.temporal != null) {
            this.temporal.delete();
        }
        this.translucent.delete();
    }
}
