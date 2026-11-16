package com.bdmajora.impetus.engine.impl.render.chunk.shader;

import com.bdmajora.impetus.engine.impl.gl.shader.ShaderConstants;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass;

import java.util.List;

// Components and pass that make up a chunk shader.
public record ChunkShaderOptions(List<ChunkShaderComponent.Factory<?>> components, TerrainRenderPass pass) {
    // Rejects null components or pass.
    public ChunkShaderOptions {
        java.util.Objects.requireNonNull(components, "components");
        java.util.Objects.requireNonNull(pass, "pass");
    }

    // Returns the shader definitions of the components and the pass.
    public ShaderConstants constants() {
        ShaderConstants.Builder constants = ShaderConstants.builder();
        for (var component : components) {
            constants.addAll(component.getDefines());
        }

        if (this.pass.supportsFragmentDiscard()) {
            constants.add("USE_FRAGMENT_DISCARD");
        }

        if (this.pass.hasNoLightmap()) {
            constants.add("IMPETUS_NO_LIGHTMAP");
        }

        constants.addAll(pass.extraDefines());

        var vertexType = pass.vertexType();
        var primitiveType = pass.primitiveType();

        vertexType.getDefines().forEach(constants::add);
        constants.addAll(primitiveType.getDefines());

        return constants.build();
    }
}
