package com.bdmajora.impetus.engine.impl.render.mesh.renderers;

import com.bdmajora.impetus.engine.impl.gl.shader.ShaderConstants;
import com.bdmajora.impetus.engine.impl.gl.shader.ShaderType;
import com.bdmajora.impetus.engine.impl.render.mesh.gl.MeshProgram;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Orders the sections of each queued region from the farthest.
public class SectionSorter {
    // Program of the sort.
    private final MeshProgram program;

    // Creates the program.
    public SectionSorter(ShaderConstants constants) {
        this.program = MeshProgram.builder("mesh/section_sort")
                .constants(constants)
                .stage(ShaderType.COMPUTE, "impetus:mesh/section_sort.comp")
                .link();
    }

    // Sorts the regions at the front of the sorting list and rejects a negative count.
    public void dispatch(int regionCount) {
        if (regionCount < 0) {
            throw new IllegalArgumentException("Negative region count " + regionCount);
        }
        this.program.bind();
        LWJGL.glDispatchCompute(regionCount, 1, 1);
    }

    // Deletes the program.
    public void delete() {
        this.program.delete();
    }
}
