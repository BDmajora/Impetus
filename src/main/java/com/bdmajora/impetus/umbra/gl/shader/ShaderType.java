package com.bdmajora.impetus.umbra.gl.shader;

import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL40;
import org.lwjgl.opengl.GL43;

// Shader stages with the GL enum of each.
public enum ShaderType {
    // Vertex stage.
    VERTEX(GL20.GL_VERTEX_SHADER),
    // Geometry stage.
    GEOMETRY(GL32.GL_GEOMETRY_SHADER),
    // Tessellation control stage.
    TESS_CONTROL(GL40.GL_TESS_CONTROL_SHADER),
    // Tessellation evaluation stage.
    TESS_EVALUATION(GL40.GL_TESS_EVALUATION_SHADER),
    // Fragment stage.
    FRAGMENT(GL20.GL_FRAGMENT_SHADER),
    // Compute stage.
    COMPUTE(GL43.GL_COMPUTE_SHADER);

    // GL enum of the stage.
    public final int id;

    // Creates a stage from its GL enum.
    ShaderType(int id) {
        this.id = id;
    }
}
