package com.bdmajora.impetus.engine.impl.render.mesh;

// Draws of a mesh frame.
public enum MeshDrawPhase {
    // Draw of the sections that were visible last frame.
    MAIN,
    // Draw of the sections that became visible this frame.
    TEMPORAL,
    // Draw into the shadow map.
    SHADOW
}
