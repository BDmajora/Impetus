package com.bdmajora.testing;

import com.bdmajora.impetus.engine.impl.render.chunk.fog.FogService;
import com.bdmajora.impetus.engine.impl.render.chunk.shader.ChunkFogMode;
import com.bdmajora.impetus.engine.impl.render.chunk.shader.ChunkShaderComponent;

// Fog state for tests.
public final class TestFogService implements FogService {
    public static float end = 100f, start = 10f, density = 0.05f, cutoff = 1f;
    public static int shape;
    public static float[] color = {0.5f, 0.6f, 0.7f, 1f};
    public static ChunkShaderComponent.Factory<?> mode = ChunkFogMode.SMOOTH;

    @Override
    public float getFogEnd() {
        return end;
    }

    @Override
    public float getFogStart() {
        return start;
    }

    @Override
    public float getFogDensity() {
        return density;
    }

    @Override
    public int getFogShapeIndex() {
        return shape;
    }

    @Override
    public float getFogCutoff() {
        return cutoff;
    }

    @Override
    public float[] getFogColor() {
        return color;
    }

    @Override
    public ChunkShaderComponent.Factory<?> getFogMode() {
        return mode;
    }
}
