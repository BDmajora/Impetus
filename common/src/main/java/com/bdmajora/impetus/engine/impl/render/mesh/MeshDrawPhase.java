package com.bdmajora.impetus.engine.impl.render.mesh;

// Which draw a shading program is bound for: the main draw of last frame's visible set, the temporal draw of what turned visible this frame, or the shadow map
public enum MeshDrawPhase {
    MAIN,
    TEMPORAL,
    SHADOW
}
