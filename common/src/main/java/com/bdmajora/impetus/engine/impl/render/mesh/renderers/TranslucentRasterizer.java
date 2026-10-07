package com.bdmajora.impetus.engine.impl.render.mesh.renderers;

import com.bdmajora.impetus.engine.impl.gl.shader.ShaderConstants;
import com.bdmajora.impetus.engine.impl.gl.shader.ShaderType;
import com.bdmajora.impetus.engine.impl.render.mesh.gl.MeshProgram;

// Translucent terrain from the mirrored command buffer the section rasterizer wrote this frame, so regions arrive farthest first; blending state is the caller's, as vanilla sets it up for the translucent layer
public class TranslucentRasterizer {
    private final MeshProgram program;

    public TranslucentRasterizer(ShaderConstants constants) {
        this.program = MeshProgram.builder("mesh/translucent")
                .constants(constants)
                .stage(ShaderType.TASK, "impetus:mesh/translucent.task")
                .stage(ShaderType.MESH, "impetus:mesh/translucent.mesh")
                .stage(ShaderType.FRAGMENT, "impetus:mesh/translucent.frag")
                .link();
    }

    // One multi-draw over every visible region's translucent sections
    public void raster(int regionCount, long commandBufferAddress) {
        this.program.bind();
        TerrainRasterizer.drawIndirect(regionCount, commandBufferAddress);
    }

    // Frees the program
    public void delete() {
        this.program.delete();
    }
}
