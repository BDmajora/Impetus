package com.bdmajora.impetus.engine.impl.render.mesh;

import com.bdmajora.impetus.engine.impl.render.chunk.ChunkRenderMatrices;
import org.joml.Matrix4f;
import org.joml.Vector3f;

// Where a mesh frame is drawn from: the origin the model-view matrix is relative to (the render entity's feet on 1.12.2), and the eye itself, which culling and fog measure from
public record MeshCamera(double renderX, double renderY, double renderZ, double eyeX, double eyeY, double eyeZ) {
    // The eye is where the inverted model-view puts the view origin, so eye height, third person and view bobbing all count; a singular matrix leaves it on the render origin
    public static MeshCamera of(ChunkRenderMatrices matrices, double renderX, double renderY, double renderZ) {
        Vector3f eye = new Matrix4f(matrices.modelView()).invert().getTranslation(new Vector3f());

        if (!Float.isFinite(eye.x) || !Float.isFinite(eye.y) || !Float.isFinite(eye.z)) {
            return at(renderX, renderY, renderZ);
        }

        return new MeshCamera(renderX, renderY, renderZ, renderX + eye.x, renderY + eye.y, renderZ + eye.z);
    }

    // A camera whose eye sits on the render origin, as the shadow pass culls from no eye at all
    public static MeshCamera at(double x, double y, double z) {
        return new MeshCamera(x, y, z, x, y, z);
    }

    // Section of the eye on each axis
    public int sectionX() {
        return (int) Math.floor(this.eyeX) >> 4;
    }

    public int sectionY() {
        return (int) Math.floor(this.eyeY) >> 4;
    }

    public int sectionZ() {
        return (int) Math.floor(this.eyeZ) >> 4;
    }
}
