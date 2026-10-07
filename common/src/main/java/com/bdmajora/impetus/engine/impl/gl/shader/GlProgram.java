package com.bdmajora.impetus.engine.impl.gl.shader;

import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

import com.bdmajora.impetus.engine.impl.gl.GlObject;
import com.bdmajora.impetus.engine.impl.gl.attribute.GlVertexFormat;
import com.bdmajora.impetus.engine.impl.gl.debug.GLDebug;
import com.bdmajora.impetus.engine.impl.gl.shader.uniform.GlUniform;
import com.bdmajora.impetus.engine.impl.gl.shader.uniform.GlUniformBlock;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;
import java.util.function.IntFunction;

// an OpenGL shader program: the linked object plus the user-defined interface bound over its uniforms
public class GlProgram<T> extends GlObject implements ShaderBindingContext {
    private static final Logger LOGGER = LogManager.getLogger(GlProgram.class);

    private final T shaderInterface;

    protected GlProgram(int program, Function<ShaderBindingContext, T> interfaceFactory) {
        this.setHandle(program);
        this.shaderInterface = interfaceFactory.apply(this);
    }

    // The typed uniform interface bound at link
    public T getInterface() {
        return this.shaderInterface;
    }

    // Starts a program
    public static Builder builder(String identifier) {
        return new Builder(identifier);
    }

    // glUseProgram
    public void bind() {
        LWJGL.glUseProgram(this.handle());
    }

    // glUseProgram(0)
    public void unbind() {
        LWJGL.glUseProgram(0);
    }

    // glDeleteProgram
    @Override
    protected void destroyInternal() {
        LWJGL.glDeleteProgram(this.handle());
    }

    @Override
    public <U extends GlUniform<?>> @Nullable U bindUniformIfPresent(String name, IntFunction<U> factory) {
        int index = LWJGL.glGetUniformLocation(this.handle(), name);

        if (index < 0) {
            return null;
        }

        return factory.apply(index);
    }

    @Override
    public @Nullable GlUniformBlock bindUniformBlockIfPresent(String name, int bindingPoint) {
        int index = LWJGL.glGetUniformBlockIndex(this.handle(), name);

        if (index < 0) {
            return null;
        }

        LWJGL.glUniformBlockBinding(this.handle(), index, bindingPoint);

        return new GlUniformBlock(bindingPoint);
    }

    public static class Builder {
        private final String name;
        private final int program;

        public Builder(String name) {
            this.name = name;
            this.program = LWJGL.glCreateProgram();
        }

        // glAttachShader
        public Builder attachShader(GlShader shader) {
            LWJGL.glAttachShader(this.program, shader.handle());

            return this;
        }

        // Links the attached shaders and hands the program to the factory, typically a container exposing typed setters for that shader set's uniforms
        public <U> GlProgram<U> link(Function<ShaderBindingContext, U> factory) {
            LWJGL.glLinkProgram(this.program);
            String log = LWJGL.glGetProgramInfoLog(this.program, 4096);

            if (!log.isEmpty()) {
                LOGGER.warn("Program link log for " + this.name + ": " + log);
            }

            int result = LWJGL.glGetProgrami(this.program, GL20.GL_LINK_STATUS);

            if (result != GL20.GL_TRUE) {
                throw new RuntimeException("Shader program linking failed, see log for details");
            }

            return new GlProgram<>(this.program, factory);
        }

        // Fixes an attribute location before link
        public Builder bindAttribute(String name, int index) {
            LWJGL.glBindAttribLocation(this.program, index, name);

            return this;
        }

        public Builder bindAttributes(GlVertexFormat format, int firstAttributeIndex) {
            int index = firstAttributeIndex;

            for (var attribute : format.getAttributes()) {
                this.bindAttribute(attribute.getName(), index++);
            }

            return this;
        }

        public Builder bindFragmentData(String name, int index) {
            LWJGL.glBindFragDataLocation(this.program, index, name);

            return this;
        }
    }
}
