package com.bdmajora.impetus.engine.impl.gl.shader;

import org.lwjgl.opengl.GL20;
import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

import com.bdmajora.impetus.engine.impl.gl.GlObject;
import com.bdmajora.impetus.engine.impl.gl.attribute.GlVertexAttribute;
import com.bdmajora.impetus.engine.impl.gl.attribute.GlVertexFormat;
import com.bdmajora.impetus.engine.impl.gl.shader.uniform.GlUniform;
import com.bdmajora.impetus.engine.impl.gl.shader.uniform.GlUniformBlock;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.IntFunction;

// Linked OpenGL shader program with the typed uniform interface bound over it
public class GlProgram<T> extends GlObject implements ShaderBindingContext {
    // Logger of the link log
    private static final Logger LOGGER = LogManager.getLogger(GlProgram.class);
    // Program name that GL never assigns to a program
    private static final int NO_PROGRAM = 0;

    // Typed uniform interface that the factory built over the program
    private final T shaderInterface;

    // Takes ownership of the program and builds the interface over it and rejects a null factory
    protected GlProgram(int program, Function<ShaderBindingContext, T> interfaceFactory) {
        Objects.requireNonNull(interfaceFactory, "Factory must not be null");
        this.setHandle(program);

        try {
            this.shaderInterface = interfaceFactory.apply(this);
        } catch (Throwable t) {
            // Deletes the program and rethrows when the factory throws
            this.delete();
            throw t;
        }
    }

    // Returns the typed uniform interface
    public T getInterface() {
        return this.shaderInterface;
    }

    // Creates a builder over a new program and rejects a null name
    public static Builder builder(String identifier) {
        return new Builder(identifier);
    }

    // Makes the program current and rejects a deleted program
    public void bind() {
        LWJGL.glUseProgram(this.handle());
    }

    // Makes no program current
    public void unbind() {
        LWJGL.glUseProgram(NO_PROGRAM);
    }

    // Deletes the program
    @Override
    protected void destroyInternal() {
        LWJGL.glDeleteProgram(this.handle());
    }

    // Returns the uniform that the factory creates at the location of the name or null when the program has no such uniform
    @Override
    public <U extends GlUniform<?>> @Nullable U bindUniformIfPresent(String name, IntFunction<U> factory) {
        Objects.requireNonNull(name, "Name must not be null");
        Objects.requireNonNull(factory, "Factory must not be null");
        int location = LWJGL.glGetUniformLocation(this.handle(), name);

        if (location < 0) {
            return null;
        }

        return factory.apply(location);
    }

    // Assigns the binding point to the named block and returns the block or null when the program has no such block
    @Override
    public @Nullable GlUniformBlock bindUniformBlockIfPresent(String name, int bindingPoint) {
        Objects.requireNonNull(name, "Name must not be null");
        // Rejects a negative binding point before any GL call
        GlUniformBlock block = new GlUniformBlock(bindingPoint);
        int program = this.handle();
        int index = LWJGL.glGetUniformBlockIndex(program, name);

        if (index < 0) {
            return null;
        }

        LWJGL.glUniformBlockBinding(program, index, bindingPoint);

        return block;
    }

    // Collects the shaders and locations of one program and links it once
    public static class Builder {
        // Maximum length of the link log in characters
        private static final int INFO_LOG_LENGTH = 4096;

        // Name of the program in the link log
        private final String name;
        // Program under construction or zero once linked
        private int program;

        // Creates a program and rejects a null name or a zero program name from the driver
        public Builder(String name) {
            this.name = Objects.requireNonNull(name, "Name must not be null");
            int program = LWJGL.glCreateProgram();

            if (program == NO_PROGRAM) {
                throw new IllegalStateException("Failed to create a program");
            }

            this.program = program;
        }

        // Returns the program under construction and rejects a builder that already linked
        private int program() {
            int program = this.program;

            if (program == NO_PROGRAM) {
                throw new IllegalStateException("Program builder is already linked");
            }

            return program;
        }

        // Attaches the shader and rejects a null or deleted shader
        public Builder attachShader(GlShader shader) {
            int handle = Objects.requireNonNull(shader, "Shader must not be null").handle();
            LWJGL.glAttachShader(this.program(), handle);

            return this;
        }

        // Links the program and returns it with the interface that the factory builds over it and rejects a null factory
        public <U> GlProgram<U> link(Function<ShaderBindingContext, U> factory) {
            Objects.requireNonNull(factory, "Factory must not be null");
            int program = this.program();
            this.program = NO_PROGRAM;

            try {
                LWJGL.glLinkProgram(program);
                String log = LWJGL.glGetProgramInfoLog(program, INFO_LOG_LENGTH);

                if (!log.isEmpty()) {
                    LOGGER.warn("Program link log for {}: {}", this.name, log);
                }

                if (LWJGL.glGetProgrami(program, GL20.GL_LINK_STATUS) != GL20.GL_TRUE) {
                    throw new RuntimeException("Shader program linking failed, see log for details");
                }
            } catch (Throwable t) {
                // Deletes the program and rethrows when linking fails
                LWJGL.glDeleteProgram(program);
                throw t;
            }

            return new GlProgram<>(program, factory);
        }

        // Binds the attribute name to the location before link and rejects a null name or a negative location
        public Builder bindAttribute(String name, int index) {
            Objects.requireNonNull(name, "Name must not be null");

            if (index < 0) {
                throw new IllegalArgumentException("Attribute index must not be negative");
            }

            LWJGL.glBindAttribLocation(this.program(), index, name);

            return this;
        }

        // Binds the attributes of the format to consecutive locations from the first location in declaration order
        public Builder bindAttributes(GlVertexFormat format, int firstAttributeIndex) {
            Collection<GlVertexAttribute> attributes = Objects.requireNonNull(format, "Format must not be null").getAttributes();

            if (firstAttributeIndex < 0) {
                throw new IllegalArgumentException("Attribute index must not be negative");
            }

            // Rejects a last location past the int range
            if (attributes.size() - 1 > Integer.MAX_VALUE - firstAttributeIndex) {
                throw new IllegalArgumentException("Attribute index must not exceed " + Integer.MAX_VALUE);
            }

            int program = this.program();
            int index = firstAttributeIndex;

            for (GlVertexAttribute attribute : attributes) {
                LWJGL.glBindAttribLocation(program, index++, attribute.getName());
            }

            return this;
        }

        // Binds the fragment output name to the color number before link and rejects a null name or a negative color number
        public Builder bindFragmentData(String name, int index) {
            Objects.requireNonNull(name, "Name must not be null");

            if (index < 0) {
                throw new IllegalArgumentException("Fragment output index must not be negative");
            }

            LWJGL.glBindFragDataLocation(this.program(), index, name);

            return this;
        }
    }
}
