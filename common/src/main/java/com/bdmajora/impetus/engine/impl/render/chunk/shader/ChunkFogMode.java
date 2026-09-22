package com.bdmajora.impetus.engine.impl.render.chunk.shader;

import com.bdmajora.impetus.lwjgl.GL20;

import com.bdmajora.impetus.engine.impl.gl.shader.ShaderBindingContext;

import java.util.List;
import java.util.function.Function;

public enum ChunkFogMode implements ChunkShaderComponent.Factory<ChunkShaderFogComponent> {
    NONE(ChunkShaderFogComponent.None::new, List.of()),
    EXP2(ChunkShaderFogComponent.Exp2::new, List.of("USE_FOG", "USE_FOG_EXP2")),
    SMOOTH(ChunkShaderFogComponent.Smooth::new, List.of("USE_FOG", "USE_FOG_SMOOTH"));

    private final Function<ShaderBindingContext, ChunkShaderFogComponent> factory;
    private final List<String> defines;

    ChunkFogMode(Function<ShaderBindingContext, ChunkShaderFogComponent> factory, List<String> defines) {
        this.factory = factory;
        this.defines = defines;
    }

    // The uniform component for this mode
    @Override
    public ChunkShaderFogComponent create(ShaderBindingContext context) {
        return factory.apply(context);
    }

    // Shader defines selecting the fog formula
    public List<String> getDefines() {
        return this.defines;
    }

    // GL fog mode constant to enum
    public static ChunkFogMode fromGLMode(int mode) {
        return switch (mode) {
            case 0 -> NONE;
            case GL20.GL_EXP2, GL20.GL_EXP -> EXP2;
            case GL20.GL_LINEAR -> SMOOTH;
            default -> throw new UnsupportedOperationException("Unknown fog mode: " + mode);
        };
    }
}
