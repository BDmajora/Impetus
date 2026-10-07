package com.bdmajora.impetus.engine.impl.render.mesh;

import com.bdmajora.impetus.engine.impl.render.chunk.fog.FogService;
import com.bdmajora.impetus.engine.impl.render.chunk.shader.ChunkFogMode;

// The frame's fog as the mesh shaders read it from the scene block; mode 0 none, 1 linear, 2 exponential squared, 3 exponential, shape as include/fog.glsl numbers it
public record MeshFog(int mode, int shape, float start, float end, float density, float[] colour) {
    public static final MeshFog NONE = new MeshFog(0, 0, 0.0f, 0.0f, 0.0f, new float[4]);

    // Reads the live fixed-function fog the raster path's shaders use too
    public static MeshFog from(FogService service) {
        int mode = switch ((ChunkFogMode) service.getFogMode()) {
            case NONE -> 0;
            case SMOOTH -> 1;
            case EXP2 -> 2;
            case EXP -> 3;
        };

        return new MeshFog(mode, service.getFogShapeIndex(), service.getFogStart(), service.getFogEnd(),
                service.getFogDensity(), service.getFogColor());
    }
}
