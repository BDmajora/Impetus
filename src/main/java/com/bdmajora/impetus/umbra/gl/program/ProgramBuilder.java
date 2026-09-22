package com.bdmajora.impetus.umbra.gl.program;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.bdmajora.impetus.umbra.gl.shader.GlShader;
import com.bdmajora.impetus.lwjgl.GL20;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Assembles a GlProgram from compiled stages: attach, bind OptiFine attribute slots (mc_Entity 10, mc_midTexCoord 11, at_tangent 12), link, validate, detach (OptiFine's setupProgram); a builder because attribute binding must precede linking
public class ProgramBuilder {
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");

    private final String name;
    private final int program;
    private final List<GlShader> attached = new ArrayList<>();

    private ProgramBuilder(String name, int program) {
        this.name = name;
        this.program = program;
    }

    // Creates the program object
    public static ProgramBuilder begin(String name) {
        int program = LWJGL.glCreateProgram();
        if (program == 0) {
            throw new ProgramCreationException("glCreateProgram returned 0 for program '" + name + "'");
        }
        return new ProgramBuilder(name, program);
    }

    // glAttachShader
    public ProgramBuilder attach(GlShader shader) {
        LWJGL.glAttachShader(this.program, shader.getGlId());
        this.attached.add(shader);
        return this;
    }

    // Binds a vertex attribute name to a fixed location; must precede link(), since glBindAttribLocation only takes effect at the next link
    public ProgramBuilder bindAttributeLocation(int index, CharSequence attributeName) {
        LWJGL.glBindAttribLocation(this.program, index, attributeName);
        return this;
    }

    // Binds a fragment output to a draw buffer index (GL 3.0+). Before link(), for the same reason
    public ProgramBuilder bindFragmentDataLocation(int colorNumber, CharSequence outputName) {
        LWJGL.glBindFragDataLocation(this.program, colorNumber, outputName);
        return this;
    }

    // Links and validates; on success the stages are DETACHED but not deleted (the caller owns them, one stage often serves several programs), on failure the program is deleted before throwing
    public GlProgram link() {
        LWJGL.glLinkProgram(this.program);

        int linkStatus = LWJGL.glGetProgrami(this.program, GL20.GL_LINK_STATUS);
        String log = LWJGL.glGetProgramInfoLog(this.program, 32768);

        if (linkStatus != GL20.GL_TRUE) {
            detachAll();
            LWJGL.glDeleteProgram(this.program);
            throw new ProgramCreationException("Failed to link program '" + this.name + "': "
                    + (log.isEmpty() ? "(no info log)" : log.trim()));
        }

        // NVIDIA returns a wall of deprecation warnings for legacy #version 120 packs; only surface the log when it actually reports an error
        if (!log.isEmpty() && log.toLowerCase(Locale.ROOT).contains("error")) {
            LOGGER.warn("Program link log for '{}': {}", this.name, log.trim());
        }

        detachAll();
        return new GlProgram(this.program, this.name);
    }

    // Detaches every stage after linking, so the shader objects can be deleted
    private void detachAll() {
        for (GlShader shader : this.attached) {
            if (!shader.isDestroyed()) {
                LWJGL.glDetachShader(this.program, shader.getGlId());
            }
        }
        this.attached.clear();
    }
}
