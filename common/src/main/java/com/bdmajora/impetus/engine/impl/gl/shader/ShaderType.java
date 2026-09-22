package com.bdmajora.impetus.engine.impl.gl.shader;

import com.bdmajora.impetus.lwjgl.GL20;
import com.bdmajora.impetus.lwjgl.GL32;
import com.bdmajora.impetus.lwjgl.GL42;
import com.bdmajora.impetus.lwjgl.GL43;
import com.bdmajora.impetus.lwjgl.GLNv;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;

// The shader stages this engine compiles, each carrying its GL type enum
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum ShaderType {
    VERTEX(GL20.GL_VERTEX_SHADER, "vsh"),
    FRAGMENT(GL20.GL_FRAGMENT_SHADER, "fsh"),
    GEOM(GL32.GL_GEOMETRY_SHADER, "gsh"),
    TESS_CTRL(GL42.GL_TESS_CONTROL_SHADER, "tcs"),
    TESS_EVALUATE(GL42.GL_TESS_EVALUATION_SHADER, "tes"),
    COMPUTE(GL43.GL_COMPUTE_SHADER, "csh"),
    // NV_mesh_shader stages for the mesh-shader terrain backend; a driver without the extension rejects them at glCreateShader, so MeshShaderSupport is checked first
    TASK(GLNv.GL_TASK_SHADER_NV, "task"),
    MESH(GLNv.GL_MESH_SHADER_NV, "mesh");

    public final int id;
    public final String fileExtension;
}
