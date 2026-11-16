package com.bdmajora.impetus.engine.impl.render.mesh;

import com.bdmajora.impetus.engine.impl.render.chunk.ChunkRenderMatrices;
import org.joml.Matrix4f;
import org.joml.Vector3f;

// Position that a mesh frame is drawn from.
public record MeshCamera(double renderX, double renderY, double renderZ, double eyeX, double eyeY, double eyeZ) {
    // Creates a camera from the matrices and keeps the render origin for a matrix that cannot be inverted.
    public static MeshCamera of(ChunkRenderMatrices matrices, double renderX, double renderY, double renderZ) {
        java.util.Objects.requireNonNull(matrices, "matrices");
        Vector3f eye = new Matrix4f(matrices.modelView()).invert().getTranslation(new Vector3f());

        if (!Float.isFinite(eye.x) || !Float.isFinite(eye.y) || !Float.isFinite(eye.z)) {
            return at(renderX, renderY, renderZ);
        }

        return new MeshCamera(renderX, renderY, renderZ, renderX + eye.x, renderY + eye.y, renderZ + eye.z);
    }

    // Creates a camera whose eye is on the render origin.
    public static MeshCamera at(double x, double y, double z) {
        return new MeshCamera(x, y, z, x, y, z);
    }

    // Returns the section x of the eye.
    public int sectionX() {
        return (int) Math.floor(this.eyeX) >> 4;
    }

    // Returns the section y of the eye.
    public int sectionY() {
        return (int) Math.floor(this.eyeY) >> 4;
    }

    // Returns the section z of the eye.
    public int sectionZ() {
        return (int) Math.floor(this.eyeZ) >> 4;
    }
}
