package com.bdmajora.impetus.umbra.gl.shader;

import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL40;
import org.lwjgl.opengl.GL43;

// Choices of the shader type setting.
public enum ShaderType {
    // Value that selects vertex.
    VERTEX(GL20.GL_VERTEX_SHADER),
    // Value that selects geometry.
    GEOMETRY(GL32.GL_GEOMETRY_SHADER),
    // Value that selects tess control.
    TESS_CONTROL(GL40.GL_TESS_CONTROL_SHADER),
    // Value that selects tess evaluation.
    TESS_EVALUATION(GL40.GL_TESS_EVALUATION_SHADER),
    // Value that selects fragment.
    FRAGMENT(GL20.GL_FRAGMENT_SHADER),
    // Value that selects compute.
    COMPUTE(GL43.GL_COMPUTE_SHADER);

    // GL enum of the stage.
    public final int id;

    // Creates a shader type from its id.
    ShaderType(int id) {
        this.id = id;
    }
}
