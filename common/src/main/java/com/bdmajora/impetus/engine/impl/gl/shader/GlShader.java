package com.bdmajora.impetus.engine.impl.gl.shader;

import org.lwjgl.opengl.GL20;
import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

import com.bdmajora.impetus.engine.impl.gl.GlObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// One compiled shader stage, separate from the linked program because a stage is routinely attached to several programs with their own lifetimes
public class GlShader extends GlObject {
    private static final Logger LOGGER = LogManager.getLogger(GlShader.class);

    private final String name;

    public GlShader(ShaderType type, String name, String src) {
        this.name = name;

        int handle = LWJGL.glCreateShader(type.id);
        LWJGL.glShaderSourceSafe(handle, src);
        LWJGL.glCompileShader(handle);

        String log = LWJGL.glGetShaderInfoLog(handle, 4096);

        if (!log.isEmpty()) {
            LOGGER.warn("Shader compilation log for " + this.name + ": " + log);
        }

        int result = LWJGL.glGetShaderi(handle, GL20.GL_COMPILE_STATUS);

        if (result != GL20.GL_TRUE) {
            throw new RuntimeException("Shader compilation failed, see log for details");
        }

        this.setHandle(handle);
    }

    // For compile error messages
    public String getName() {
        return this.name;
    }

    // glDeleteShader
    @Override
    protected void destroyInternal() {
        LWJGL.glDeleteShader(this.handle());
    }
}
