package com.bdmajora.impetus.engine.impl.gl.shader;

import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.lwjgl.opengl.NVMeshShader;

// Shader stages the engine compiles with their GL type enums and file extensions.
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum ShaderType {
    VERTEX(GL20.GL_VERTEX_SHADER, "vsh"),
    FRAGMENT(GL20.GL_FRAGMENT_SHADER, "fsh"),
    GEOM(GL32.GL_GEOMETRY_SHADER, "gsh"),
    TESS_CTRL(GL42.GL_TESS_CONTROL_SHADER, "tcs"),
    TESS_EVALUATE(GL42.GL_TESS_EVALUATION_SHADER, "tes"),
    COMPUTE(GL43.GL_COMPUTE_SHADER, "csh"),
    // Task stage of the NV mesh shader extension.
    TASK(NVMeshShader.GL_TASK_SHADER_NV, "task"),
    // Mesh stage of the NV mesh shader extension.
    MESH(NVMeshShader.GL_MESH_SHADER_NV, "mesh");

    // GL enum passed to glCreateShader.
    public final int id;
    // File extension of sources for this stage.
    public final String fileExtension;
}
