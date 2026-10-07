package com.bdmajora.impetus.engine.impl.render.mesh.renderers;

import com.bdmajora.impetus.engine.impl.gl.shader.ShaderConstants;
import com.bdmajora.impetus.engine.impl.gl.shader.ShaderType;
import com.bdmajora.impetus.engine.impl.render.mesh.gl.MeshProgram;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Phase 1: one box per frustum-visible region against last frame's depth, recording which survive; no task shader since the workload is fixed, colour/depth writes off, representative fragment test on
public class RegionRasterizer {
    private final MeshProgram program;

    public RegionRasterizer(ShaderConstants constants) {
        this.program = MeshProgram.builder("mesh/region_raster")
                .constants(constants)
                .stage(ShaderType.MESH, "impetus:mesh/region_raster.mesh")
                .stage(ShaderType.FRAGMENT, "impetus:mesh/region_raster.frag")
                .link();
    }

    // One mesh workgroup per visible region
    public void raster(int visibleRegionCount) {
        this.program.bind();
        LWJGL.glDrawMeshTasksNV(0, visibleRegionCount);
    }

    // Frees the program
    public void delete() {
        this.program.delete();
    }
}
