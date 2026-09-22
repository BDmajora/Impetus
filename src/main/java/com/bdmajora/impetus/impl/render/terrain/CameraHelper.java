package com.bdmajora.impetus.impl.render.terrain;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import com.bdmajora.impetus.mixin.core.terrain.ActiveRenderInfoAccessor;

public class CameraHelper {
    // Render-thread scratch, since the viewport is built once per pass there
    private static final Matrix4f INVERSE_MODEL_VIEW = new Matrix4f();

    // Camera offset for third person, so culling uses the real eye position; the returned vector is fresh, the matrix is reused
    public static Vector3f getThirdPersonOffset() {
        Vector3f offset = new Vector3f();
        INVERSE_MODEL_VIEW.set(ActiveRenderInfoAccessor.getModelViewMatrix()).invert().transformPosition(offset);
        return offset;
    }
}
