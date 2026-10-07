package com.bdmajora.impetus.engine.impl.render.mesh.renderers;

import com.bdmajora.impetus.engine.impl.gl.shader.ShaderConstants;
import com.bdmajora.impetus.engine.impl.gl.shader.ShaderType;
import com.bdmajora.impetus.engine.impl.render.mesh.gl.MeshProgram;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Reorders the sections of each queued region farthest first for the translucent pass, one compute workgroup per region (Nvidium's region section sorter)
public class SectionSorter {
    private final MeshProgram program;

    public SectionSorter(ShaderConstants constants) {
        this.program = MeshProgram.builder("mesh/section_sort")
                .constants(constants)
                .stage(ShaderType.COMPUTE, "impetus:mesh/section_sort.comp")
                .link();
    }

    // Sorts the regions listed at the front of the scene block's sorting list
    public void dispatch(int regionCount) {
        this.program.bind();
        LWJGL.glDispatchCompute(regionCount, 1, 1);
    }

    // Frees the program
    public void delete() {
        this.program.delete();
    }
}
