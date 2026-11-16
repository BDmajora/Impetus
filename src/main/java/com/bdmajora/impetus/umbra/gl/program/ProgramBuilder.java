package com.bdmajora.impetus.umbra.gl.program;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.bdmajora.impetus.umbra.gl.shader.GlShader;
import com.bdmajora.impetus.umbra.gl.shader.ShaderType;
import org.lwjgl.opengl.GL20;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Builder that attaches shaders to a new program and links it once.
public class ProgramBuilder {
    // Logger of link warnings.
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");

    // Name of the program.
    private final String name;
    // GL name of the program.
    private final int program;
    // Shaders attached so far.
    private final List<GlShader> attached = new ArrayList<>();
    // Linked state of the builder.
    private boolean linked;

    // Creates a builder for a program.
    private ProgramBuilder(String name, int program) {
        this.name = name;
        this.program = program;
    }

    // Creates a program and rejects a zero GL name.
    public static ProgramBuilder begin(String name) {
        int program = LWJGL.glCreateProgram();
        if (program == 0) {
            throw new ProgramCreationException("glCreateProgram returned 0 for program '" + name + "'");
        }
        return new ProgramBuilder(name, program);
    }

    // Attaches a shader.
    public ProgramBuilder attach(GlShader shader) {
        LWJGL.glAttachShader(this.program, shader.getGlId());
        this.attached.add(shader);
        return this;
    }

    // Binds an attribute to an index.
    public ProgramBuilder bindAttributeLocation(int index, CharSequence attributeName) {
        LWJGL.glBindAttribLocation(this.program, index, attributeName);
        return this;
    }

    // Binds a fragment output to a color number.
    public ProgramBuilder bindFragmentDataLocation(int colorNumber, CharSequence outputName) {
        LWJGL.glBindFragDataLocation(this.program, colorNumber, outputName);
        return this;
    }

    // Links the program once and deletes it when linking fails and detaches every shader.
    public GlProgram link() {
        if (this.linked) {
            throw new IllegalStateException("Program '" + this.name + "' was already linked");
        }
        this.linked = true;
        LWJGL.glLinkProgram(this.program);

        int linkStatus = LWJGL.glGetProgrami(this.program, GL20.GL_LINK_STATUS);
        String reported = LWJGL.glGetProgramInfoLog(this.program, 32768);
        String log = reported == null ? "" : reported;

        if (linkStatus != GL20.GL_TRUE) {
            detachAll();
            LWJGL.glDeleteProgram(this.program);
            throw new ProgramCreationException("Failed to link program '" + this.name + "': "
                    + (log.isEmpty() ? "(no info log)" : log.trim()));
        }

        if (!log.isEmpty() && log.toLowerCase(Locale.ROOT).contains("error")) {
            LOGGER.warn("Program link log for '{}': {}", this.name, log.trim());
        }

        boolean tessellated = false;
        for (GlShader shader : this.attached) {
            if (shader.getType() == ShaderType.TESS_EVALUATION) {
                tessellated = true;
                break;
            }
        }
        detachAll();
        return new GlProgram(this.program, this.name, tessellated);
    }

    // Detaches every shader that still exists.
    private void detachAll() {
        for (GlShader shader : this.attached) {
            if (!shader.isDestroyed()) {
                LWJGL.glDetachShader(this.program, shader.getGlId());
            }
        }
        this.attached.clear();
    }
}
