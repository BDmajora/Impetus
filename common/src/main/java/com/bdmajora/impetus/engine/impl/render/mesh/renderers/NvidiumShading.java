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

// Terrain programs of the mesh backend that read everything from the scene block.
public class NvidiumShading implements MeshTerrainShading {
    // Program of the main draw.
    private final TerrainRasterizer terrain;
    // Program of the temporal draw or null.
    private final @Nullable TerrainRasterizer temporal;
    // Program of the translucent draw.
    private final TranslucentRasterizer translucent;

    // Creates the programs and frees the built ones when a program fails.
    public NvidiumShading(ShaderConstants constants, @Nullable ShaderConstants temporalConstants) {
        java.util.Objects.requireNonNull(constants, "constants");
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

    // Returns the bytes of one vertex.
    @Override
    public int vertexStride() {
        return MeshChunkVertex.STRIDE;
    }

    // Binds the temporal or the main program and returns true.
    @Override
    public boolean bindOpaque(MeshDrawPhase phase, @Nullable TerrainRenderPass pass, ChunkRenderMatrices matrices) {
        TerrainRasterizer program = phase == MeshDrawPhase.TEMPORAL && this.temporal != null ? this.temporal : this.terrain;
        program.bind();
        return true;
    }

    // Binds the translucent program and returns true.
    @Override
    public boolean bindTranslucent(MeshDrawPhase phase, @Nullable TerrainRenderPass pass, ChunkRenderMatrices matrices) {
        this.translucent.bind();
        return true;
    }

    // Does nothing because only the program was bound.
    @Override
    public void finish() {
    }

    // Returns false because there is no shadow program.
    @Override
    public boolean hasShadowPrograms() {
        return false;
    }

    // Deletes every program even when one delete throws.
    @Override
    public void delete() {
        try {
            this.terrain.delete();
        } finally {
            try {
                if (this.temporal != null) {
                    this.temporal.delete();
                }
            } finally {
                this.translucent.delete();
            }
        }
    }
}
