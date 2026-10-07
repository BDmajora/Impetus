package com.bdmajora.impetus.engine.impl.render.chunk.shader;

import com.bdmajora.impetus.engine.impl.gl.shader.ShaderBindingContext;

import java.util.Collection;
import java.util.List;

public interface ChunkShaderComponent {
    void setup();

    interface Factory<T extends ChunkShaderComponent> {
        T create(ShaderBindingContext context, ChunkShaderEnvironment environment);

        // Defaults to none
        default Collection<String> getDefines() {
            return List.of();
        }
    }
}
