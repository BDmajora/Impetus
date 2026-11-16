package com.bdmajora.impetus.umbra.gl.shader;

import com.bdmajora.impetus.umbra.gl.GlResource;
import com.bdmajora.impetus.umbra.shaderpack.preprocessor.GlslPreprocessor;
import org.lwjgl.opengl.GL20;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Compiled shader stage.
public class GlShader extends GlResource {
    // Name of the source file.
    private final String name;
    // Stage of the shader.
    private final ShaderType type;

    // Creates a gl shader from its type and name and source.
    public GlShader(ShaderType type, String name, String source) {
        this.name = name;
        this.type = type;

        source = GlslPreprocessor.finalizeForDriver(name, source);

        int handle = LWJGL.glCreateShader(type.id);
        if (handle == 0) {
            throw new ShaderCompileException(name, "glCreateShader returned 0 (driver could not allocate a shader object)");
        }

        LWJGL.glShaderSourceSafe(handle, source);
        LWJGL.glCompileShader(handle);

        String log = LWJGL.glGetShaderInfoLog(handle, 32768);
        int status = LWJGL.glGetShaderi(handle, GL20.GL_COMPILE_STATUS);

        if (status != GL20.GL_TRUE) {
            LWJGL.glDeleteShader(handle);
            throw new ShaderCompileException(name, log.isEmpty() ? "(no info log)" : log.trim());
        }

        setHandle(handle);
    }

    // Returns the name.
    public String getName() {
        return this.name;
    }

    // Returns the type.
    public ShaderType getType() {
        return this.type;
    }

    // Deletes the shader.
    @Override
    protected void destroyInternal() {
        LWJGL.glDeleteShader(getGlId());
    }
}
