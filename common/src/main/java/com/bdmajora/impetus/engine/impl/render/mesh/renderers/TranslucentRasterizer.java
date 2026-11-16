package com.bdmajora.impetus.engine.impl.render.mesh.renderers;

import com.bdmajora.impetus.engine.impl.gl.shader.ShaderConstants;
import com.bdmajora.impetus.engine.impl.gl.shader.ShaderType;
import com.bdmajora.impetus.engine.impl.render.mesh.gl.MeshProgram;

// Draws the translucent terrain from the commands of the section rasterizer.
public class TranslucentRasterizer {
    // Program of the draw.
    private final MeshProgram program;

    // Creates the program.
    public TranslucentRasterizer(ShaderConstants constants) {
        this.program = MeshProgram.builder("mesh/translucent")
                .constants(constants)
                .stage(ShaderType.TASK, "impetus:mesh/translucent.task")
                .stage(ShaderType.MESH, "impetus:mesh/translucent.mesh")
                .stage(ShaderType.FRAGMENT, "impetus:mesh/translucent.frag")
                .link();
    }

    // Binds the program and draws the commands of every region.
    public void raster(int regionCount, long commandBufferAddress) {
        this.program.bind();
        TerrainRasterizer.drawIndirect(regionCount, commandBufferAddress);
    }

    // Binds the program.
    public void bind() {
        this.program.bind();
    }

    // Deletes the program.
    public void delete() {
        this.program.delete();
    }
}
