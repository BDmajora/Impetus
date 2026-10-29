package com.bdmajora.impetus.engine.impl.gl.shader;

import org.lwjgl.opengl.GL20;
import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

import com.bdmajora.impetus.engine.impl.gl.GlObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Objects;

// Compiled OpenGL shader stage that programs attach by name
public class GlShader extends GlObject {
    // Logger of the compile log
    private static final Logger LOGGER = LogManager.getLogger(GlShader.class);
    // Shader name that GL never assigns to a shader
    private static final int NO_SHADER = 0;
    // Maximum length of the compile log in characters
    private static final int INFO_LOG_LENGTH = 4096;

    // Name of the shader in the compile log and in error messages
    private final String name;

    // Compiles the source as a shader of the type and rejects a null argument or a zero shader name from the driver
    public GlShader(ShaderType type, String name, String src) {
        int id = Objects.requireNonNull(type, "Type must not be null").id;
        this.name = Objects.requireNonNull(name, "Name must not be null");
        Objects.requireNonNull(src, "Source must not be null");
        int handle = LWJGL.glCreateShader(id);

        if (handle == NO_SHADER) {
            throw new IllegalStateException("Failed to create a shader");
        }

        try {
            LWJGL.glShaderSourceSafe(handle, src);
            LWJGL.glCompileShader(handle);
            String log = LWJGL.glGetShaderInfoLog(handle, INFO_LOG_LENGTH);

            if (!log.isEmpty()) {
                LOGGER.warn("Shader compilation log for {}: {}", name, log);
            }

            if (LWJGL.glGetShaderi(handle, GL20.GL_COMPILE_STATUS) != GL20.GL_TRUE) {
                throw new RuntimeException("Shader compilation failed, see log for details");
            }
        } catch (Throwable t) {
            // Deletes the shader and rethrows when compilation fails
            try {
                LWJGL.glDeleteShader(handle);
            } catch (Throwable deleteFailure) {
                // Attaches a failed delete to the compile failure
                if (deleteFailure != t) {
                    t.addSuppressed(deleteFailure);
                }
            }

            throw t;
        }

        this.setHandle(handle);
    }

    // Returns the name of the shader
    public String getName() {
        return this.name;
    }

    // Deletes the shader
    @Override
    protected void destroyInternal() {
        LWJGL.glDeleteShader(this.handle());
    }
}
