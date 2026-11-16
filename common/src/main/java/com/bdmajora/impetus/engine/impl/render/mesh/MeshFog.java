package com.bdmajora.impetus.engine.impl.render.mesh;

import com.bdmajora.impetus.engine.impl.render.chunk.fog.FogService;
import com.bdmajora.impetus.engine.impl.render.chunk.shader.ChunkFogMode;

// Fog settings that the mesh shaders read.
public record MeshFog(int mode, int shape, float start, float end, float density, float[] colour) {
    // Rejects a color without four components.
    public MeshFog {
        if (colour == null || colour.length != 4) {
            throw new IllegalArgumentException("Fog colour needs four components");
        }
    }

    // Fog that is off.
    public static final MeshFog NONE = new MeshFog(0, 0, 0.0f, 0.0f, 0.0f, new float[4]);

    // Reads the fog of a service and uses no fog for an unknown mode.
    public static MeshFog from(FogService service) {
        int mode = !(service.getFogMode() instanceof ChunkFogMode fogMode) ? 0 : switch (fogMode) {
            case NONE -> 0;
            case SMOOTH -> 1;
            case EXP2 -> 2;
            case EXP -> 3;
        };

        return new MeshFog(mode, service.getFogShapeIndex(), service.getFogStart(), service.getFogEnd(),
                service.getFogDensity(), service.getFogColor());
    }
}
