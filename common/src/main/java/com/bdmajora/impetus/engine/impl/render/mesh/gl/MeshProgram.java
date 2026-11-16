package com.bdmajora.impetus.engine.impl.render.mesh.gl;

import com.bdmajora.impetus.engine.impl.gl.shader.GlShader;
import com.bdmajora.impetus.engine.impl.gl.shader.ShaderConstants;
import com.bdmajora.impetus.engine.impl.gl.shader.ShaderType;
import com.bdmajora.impetus.engine.impl.render.shader.ShaderLoader;
import org.lwjgl.opengl.GL20;

import java.util.ArrayList;
import java.util.List;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Linked task and mesh and fragment program.
public class MeshProgram {
    // Name for log lines.
    private final String name;
    // GL name.
    private final int handle;
    // Whether the program was deleted.
    private boolean deleted;

    // Creates a program wrapper.
    private MeshProgram(String name, int handle) {
        this.name = name;
        this.handle = handle;
    }

    // Creates a builder for a program.
    public static Builder builder(String name) {
        return new Builder(name);
    }

    // Uses the program and rejects a deleted program.
    public void bind() {
        if (this.deleted) {
            throw new IllegalStateException("Program was deleted");
        }
        LWJGL.glUseProgram(this.handle);
    }

    // Returns the name.
    public String getName() {
        return this.name;
    }

    // Deletes the program once.
    public void delete() {
        if (this.deleted) {
            return;
        }
        this.deleted = true;
        LWJGL.glDeleteProgram(this.handle);
    }

    // Collects the stages of a program.
    public static class Builder {
        // Name of the program.
        private final String name;
        // Compiled stages.
        private final List<GlShader> stages = new ArrayList<>();
        // Definitions of the stages that follow.
        private ShaderConstants constants = ShaderConstants.EMPTY;

        // Creates a builder and rejects a null name.
        private Builder(String name) {
            this.name = java.util.Objects.requireNonNull(name, "name");
        }

        // Stores the definitions of the stages that follow and rejects null.
        public Builder constants(ShaderConstants constants) {
            this.constants = java.util.Objects.requireNonNull(constants, "constants");
            return this;
        }

        // Loads and compiles a stage and rejects a null type or path.
        public Builder stage(ShaderType type, String path) {
            java.util.Objects.requireNonNull(type, "type");
            java.util.Objects.requireNonNull(path, "path");
            this.stages.add(ShaderLoader.loadShader(type, path, this.constants));
            return this;
        }

        // Links the stages and deletes them and rejects a program without stages or a failed link.
        public MeshProgram link() {
            if (this.stages.isEmpty()) {
                throw new IllegalStateException("Mesh program " + this.name + " has no stages");
            }
            int program = LWJGL.glCreateProgram();

            for (GlShader stage : this.stages) {
                LWJGL.glAttachShader(program, stage.handle());
            }

            LWJGL.glLinkProgram(program);

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
