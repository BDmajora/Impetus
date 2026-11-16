package com.bdmajora.impetus.engine.impl.render.chunk.fog;

import com.bdmajora.impetus.engine.impl.render.chunk.shader.ChunkShaderComponent;

// Source of the fog settings.
public interface FogService {
    // Shape index of spherical fog.
    int FOG_SHAPE_SPHERICAL = 0;
    // Shape index of cylindrical fog.
    int FOG_SHAPE_CYLINDRICAL = 1;
    // Shape index of planar fog.
    int FOG_SHAPE_PLANAR = 2;

    // Returns the distance where fog is full.
    float getFogEnd();
    // Returns the distance where fog starts.
    float getFogStart();
    // Returns the density of the fog.
    float getFogDensity();
    // Returns the shape index of the fog.
    int getFogShapeIndex();
    // Returns the distance past which fog hides everything.
    float getFogCutoff();
    // Returns the fog color.
    float[] getFogColor();
    // Returns the factory of the fog shader component.
    ChunkShaderComponent.Factory<?> getFogMode();
}
