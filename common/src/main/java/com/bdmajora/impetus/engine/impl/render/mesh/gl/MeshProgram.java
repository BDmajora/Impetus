package com.bdmajora.impetus.engine.impl.render.mesh.gl;

import com.bdmajora.impetus.engine.impl.gl.shader.GlShader;
import com.bdmajora.impetus.engine.impl.gl.shader.ShaderConstants;
import com.bdmajora.impetus.engine.impl.gl.shader.ShaderType;
import com.bdmajora.impetus.engine.impl.render.shader.ShaderLoader;
import org.lwjgl.opengl.GL20;

import java.util.ArrayList;
import java.util.List;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// A linked task/mesh/fragment program, not a GlProgram since these shaders have no uniforms at all: every input is the address-bound scene UBO or a pointer read from it
public class MeshProgram {
    private final String name;
    private final int handle;
    private boolean deleted;

    private MeshProgram(String name, int handle) {
        this.name = name;
        this.handle = handle;
    }

    // Starts a mesh shader program
    public static Builder builder(String name) {
        return new Builder(name);
    }

    // glUseProgram
    public void bind() {
        LWJGL.glUseProgram(this.handle);
    }

    // For log lines
    public String getName() {
        return this.name;
    }

    // glDeleteProgram
    public void delete() {
        if (this.deleted) {
            return;
        }
        this.deleted = true;
        LWJGL.glDeleteProgram(this.handle);
    }

    public static class Builder {
        private final String name;
        private final List<GlShader> stages = new ArrayList<>();
        private ShaderConstants constants = ShaderConstants.EMPTY;

        private Builder(String name) {
            this.name = name;
        }

        // Specialisation defines, applied to every stage added after this call
        public Builder constants(ShaderConstants constants) {
            this.constants = constants;
            return this;
        }

        // Loads and compiles one stage from /assets/{namespace}/shaders/{path} through the engine's ShaderParser so #import and define injection match the chunk shaders
        public Builder stage(ShaderType type, String path) {
            this.stages.add(ShaderLoader.loadShader(type, path, this.constants));
            return this;
        }

        // Links the attached task, mesh and fragment stages; throws with the log on failure
        public MeshProgram link() {
            int program = LWJGL.glCreateProgram();

            for (GlShader stage : this.stages) {
                LWJGL.glAttachShader(program, stage.handle());
            }

            LWJGL.glLinkProgram(program);

            // Detach before deleting, or the driver keeps the compiled stages alive attached to this program
            for (GlShader stage : this.stages) {
                LWJGL.glDetachShader(program, stage.handle());
                stage.delete();
            }
            this.stages.clear();

            String log = LWJGL.glGetProgramInfoLog(program, 4096);

            if (LWJGL.glGetProgrami(program, GL20.GL_LINK_STATUS) != GL20.GL_TRUE) {
                LWJGL.glDeleteProgram(program);
                throw new RuntimeException("Failed to link mesh program " + this.name + ": " + log);
            }

            return new MeshProgram(this.name, program);
        }
    }
}
