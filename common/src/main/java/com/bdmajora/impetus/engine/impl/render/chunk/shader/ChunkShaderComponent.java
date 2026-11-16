package com.bdmajora.impetus.engine.impl.render.chunk.shader;

import com.bdmajora.impetus.engine.impl.gl.shader.ShaderBindingContext;

import java.util.Collection;
import java.util.List;

// Part of the chunk shader that owns uniforms.
public interface ChunkShaderComponent {
    // Uploads the uniforms of the component.
    void setup();

    // Creates a component.
    interface Factory<T extends ChunkShaderComponent> {
        // Creates the component.
        T create(ShaderBindingContext context, ChunkShaderEnvironment environment);

        // Returns the shader definitions of the component.
        default Collection<String> getDefines() {
            return List.of();
        }
    }
}
