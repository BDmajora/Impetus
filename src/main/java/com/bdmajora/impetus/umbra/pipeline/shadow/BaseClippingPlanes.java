package com.bdmajora.impetus.umbra.pipeline.shadow;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

// Six clipping planes of a view frustum where a point is inside a plane when its dot product with the point and 1 is positive
public final class BaseClippingPlanes {
    // Planes in the order right left top bottom far near
    private final Vector4f[] planes = new Vector4f[6];

    // Extracts the six planes from a model view projection matrix
    public BaseClippingPlanes(Matrix4fc modelViewProjection) {
        Matrix4f transform = new Matrix4f(modelViewProjection);
        transform.transpose();
        this.planes[0] = transform(transform, -1, 0, 0);
        this.planes[1] = transform(transform, 1, 0, 0);
        this.planes[2] = transform(transform, 0, -1, 0);
        this.planes[3] = transform(transform, 0, 1, 0);
        this.planes[4] = transform(transform, 0, 0, -1);
        this.planes[5] = transform(transform, 0, 0, 1);
    }

    // Returns the plane of one clip space axis direction scaled to unit length over all four components
    private static Vector4f transform(Matrix4fc transform, float x, float y, float z) {
        Vector4f vector = new Vector4f(x, y, z, 1.0f);
        vector.mul(transform);
        vector.normalize();
        return vector;
    }

    // Returns the live array of the six planes
    public Vector4f[] getPlanes() {
        return this.planes;
    }
}
