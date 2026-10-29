package com.bdmajora.impetus.engine.impl.gl.shader;

import com.bdmajora.impetus.engine.impl.gl.shader.uniform.GlUniform;
import com.bdmajora.impetus.engine.impl.gl.shader.uniform.GlUniformBlock;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.IntFunction;

// Lookup of the uniforms and uniform blocks of one linked program by name
public interface ShaderBindingContext {
    // Returns the uniform that the factory creates for the name and rejects a null name or factory or a missing uniform
    default <U extends GlUniform<?>> U bindUniform(String name, IntFunction<U> factory) {
        Objects.requireNonNull(name, "Name must not be null");
        Objects.requireNonNull(factory, "Factory must not be null");
        U uniform = this.bindUniformIfPresent(name, factory);

        if (uniform == null) {
            throw new NullPointerException("No uniform exists with name: " + name);
        }

        return uniform;
    }

    // Returns the uniform that the factory creates at the location of the name or null when the program has no such uniform
    <U extends GlUniform<?>> @Nullable U bindUniformIfPresent(String name, IntFunction<U> factory);

    // Returns the block of the name at the binding point and rejects a null name or a negative binding point or a missing block
    default GlUniformBlock bindUniformBlock(String name, int bindingPoint) {
        Objects.requireNonNull(name, "Name must not be null");

        if (bindingPoint < 0) {
            throw new IllegalArgumentException("Uniform block binding must not be negative");
        }

        GlUniformBlock block = this.bindUniformBlockIfPresent(name, bindingPoint);

        if (block == null) {
            throw new NullPointerException("No uniform block exists with name: " + name);
        }

        return block;
    }

    // Assigns the binding point to the named block and returns the block or null when the program has no such block
    @Nullable GlUniformBlock bindUniformBlockIfPresent(String name, int bindingPoint);
}
