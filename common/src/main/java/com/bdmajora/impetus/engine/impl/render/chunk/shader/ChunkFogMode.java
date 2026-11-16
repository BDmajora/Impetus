package com.bdmajora.impetus.engine.impl.render.chunk.shader;

import org.lwjgl.opengl.GL20;

import com.bdmajora.impetus.engine.impl.gl.shader.ShaderBindingContext;

import java.util.List;
import java.util.function.BiFunction;

// Fog formulas of the chunk shader.
public enum ChunkFogMode implements ChunkShaderComponent.Factory<ChunkShaderFogComponent> {
    // Fog is off.
    NONE(ChunkShaderFogComponent.None::new, List.of()),
    // Exponential fog.
    EXP(ChunkShaderFogComponent.Exp::new, List.of("USE_FOG", "USE_FOG_EXP")),
    // Squared exponential fog.
    EXP2(ChunkShaderFogComponent.Exp2::new, List.of("USE_FOG", "USE_FOG_EXP2")),
    // Linear fog.
    SMOOTH(ChunkShaderFogComponent.Smooth::new, List.of("USE_FOG", "USE_FOG_SMOOTH"));

    // Factory of the fog component.
    private final BiFunction<ShaderBindingContext, ChunkShaderEnvironment, ChunkShaderFogComponent> factory;
    // Shader definitions of the mode.
    private final List<String> defines;

    // Creates a fog mode.
    ChunkFogMode(BiFunction<ShaderBindingContext, ChunkShaderEnvironment, ChunkShaderFogComponent> factory, List<String> defines) {
        this.factory = factory;
        this.defines = defines;
    }

    // Creates the fog component.
    @Override
    public ChunkShaderFogComponent create(ShaderBindingContext context, ChunkShaderEnvironment environment) {
        return factory.apply(context, environment);
    }

    // Returns the shader definitions of the mode.
    public List<String> getDefines() {
        return this.defines;
    }

    // Returns the mode of a GL fog constant and rejects an unknown constant.
    public static ChunkFogMode fromGLMode(int mode) {
        switch (mode) {
            case 0:
                return ChunkFogMode.NONE;
            case GL20.GL_EXP2:
                return ChunkFogMode.EXP2;
            case GL20.GL_EXP:
                return ChunkFogMode.EXP;
            case GL20.GL_LINEAR:
                return ChunkFogMode.SMOOTH;
            default:
                throw new UnsupportedOperationException("Unknown fog mode: " + mode);
        }
    }
}
