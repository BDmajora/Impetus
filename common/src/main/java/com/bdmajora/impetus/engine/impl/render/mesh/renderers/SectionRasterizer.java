package com.bdmajora.impetus.engine.impl.render.mesh.renderers;

import com.bdmajora.impetus.engine.impl.gl.shader.ShaderConstants;
import com.bdmajora.impetus.engine.impl.gl.shader.ShaderType;
import com.bdmajora.impetus.engine.impl.render.mesh.gl.MeshProgram;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Draws a box per live section and writes the draw commands.
public class SectionRasterizer {
    // Program of the draw.
    private final MeshProgram program;

    // Creates the program.
    public SectionRasterizer(ShaderConstants constants) {
        this.program = MeshProgram.builder("mesh/section_raster")
                .constants(constants)
                .stage(ShaderType.TASK, "impetus:mesh/section_raster.task")
                .stage(ShaderType.MESH, "impetus:mesh/section_raster.mesh")
                .stage(ShaderType.FRAGMENT, "impetus:mesh/section_raster.frag")
                .link();
    }

    // Draws one workgroup per visible region and rejects a negative count.
    public void raster(int visibleRegionCount) {
        if (visibleRegionCount < 0) {
            throw new IllegalArgumentException("Negative region count " + visibleRegionCount);
        }
        this.program.bind();
        LWJGL.glDrawMeshTasksNV(0, visibleRegionCount);
    }

    // Deletes the program.
    public void delete() {
        this.program.delete();
    }
}
