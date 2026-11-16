package com.bdmajora.impetus.impl.render.terrain;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import com.bdmajora.impetus.mixin.core.terrain.ActiveRenderInfoAccessor;

// Helpers for the camera position.
public class CameraHelper {
    // Inverse model view matrix.
    private static final Matrix4f INVERSE_MODEL_VIEW = new Matrix4f();

    // Returns the third person offset.
    public static Vector3f getThirdPersonOffset() {
        Vector3f offset = new Vector3f();
        INVERSE_MODEL_VIEW.set(ActiveRenderInfoAccessor.getModelViewMatrix()).invert().transformPosition(offset);
        return offset;
    }
}
