package com.bdmajora.impetus.engine.impl.render.mesh.renderers;

import com.bdmajora.impetus.engine.impl.gl.shader.ShaderConstants;
import com.bdmajora.impetus.engine.impl.gl.shader.ShaderType;
import com.bdmajora.impetus.engine.impl.render.mesh.gl.MeshProgram;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Phase 2, the piece that removes the CPU from the loop: one task workgroup per visible region dispatches a box per live section and writes the indirect commands the terrain and translucent rasterizers consume
public class SectionRasterizer {
    private final MeshProgram program;

    public SectionRasterizer(ShaderConstants constants) {
        this.program = MeshProgram.builder("mesh/section_raster")
                .constants(constants)
                .stage(ShaderType.TASK, "impetus:mesh/section_raster.task")
                .stage(ShaderType.MESH, "impetus:mesh/section_raster.mesh")
                .stage(ShaderType.FRAGMENT, "impetus:mesh/section_raster.frag")
                .link();
    }

    // One task workgroup per visible region
    public void raster(int visibleRegionCount) {
        this.program.bind();
        LWJGL.glDrawMeshTasksNV(0, visibleRegionCount);
    }

    // Frees the program
    public void delete() {
        this.program.delete();
    }
}
