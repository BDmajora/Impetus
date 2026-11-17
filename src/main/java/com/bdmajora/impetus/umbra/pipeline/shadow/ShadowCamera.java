package com.bdmajora.impetus.umbra.pipeline.shadow;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3d;

import java.util.Objects;

// View and projection of the shadow pass.
public final class ShadowCamera {
    // Rotation of a quarter turn about x.
    private static final Matrix4fc QUARTER_TURN = new Matrix4f().rotationX((float) Math.toRadians(90.0f));

    // Near plane.
    private final float nearPlane;
    // Far plane.
    private final float farPlane;
    // Interval size.
    private final float intervalSize;
    // Field of view of a perspective shadow or null.
    private final Float fov;
    // Sun path rotation in radians.
    private final float sunPathRadians;
    // Projection before snapping.
    private final Matrix4f projectionTemplate;
    // Projection matrix.
    private final Matrix4f projection = new Matrix4f();
    // Model view matrix.
    private final Matrix4f modelView = new Matrix4f();

    // Creates a shadow camera from its half plane length and near plane and far plane and interval size and fov.
    public ShadowCamera(float halfPlaneLength, float nearPlane, float farPlane, float intervalSize, Float fov,
                        float sunPathRotation) {
        this.nearPlane = nearPlane;
        this.farPlane = farPlane;
        this.intervalSize = intervalSize;
        this.fov = fov;
        this.sunPathRadians = (float) Math.toRadians(sunPathRotation);
        this.projectionTemplate = fov != null
                ? perspective(fov, nearPlane, farPlane)
                : new Matrix4f().setOrtho(-halfPlaneLength, halfPlaneLength, -halfPlaneLength, halfPlaneLength,
                        nearPlane, farPlane);
    }

    // Returns a perspective projection.
    static Matrix4f perspective(float fov, float nearPlane, float farPlane) {
        float yScale = (float) (1.0f / Math.tan(Math.toRadians(fov) * 0.5f));
        return new Matrix4f(
                yScale, 0.0f, 0.0f, 0.0f,
                0.0f, yScale, 0.0f, 0.0f,
                0.0f, 0.0f, (farPlane + nearPlane) / (nearPlane - farPlane), -1.0f,
                0.0f, 0.0f, 2.0f * farPlane * nearPlane / (nearPlane - farPlane), 1.0f);
    }

    // Returns the sky angle the light rotation uses for a shadow angle.
    static float skyAngle(float shadowAngle) {
        return shadowAngle < 0.25f ? shadowAngle + 0.75f : shadowAngle - 0.25f;
    }

    // Returns the offset that snaps a coordinate to the interval.
    static float snapOffset(double coordinate, float intervalSize) {
        return (float) coordinate % intervalSize - intervalSize / 2.0f;
    }

    // Rebuilds the matrices for a sun angle and camera position.
    public void update(float shadowAngle, Vector3d camera) {
        Objects.requireNonNull(camera, "camera");
        this.projection.set(this.projectionTemplate);
        this.modelView.set(QUARTER_TURN)
                .rotateZ((float) Math.toRadians(skyAngle(shadowAngle) * -360.0f))
                .rotateX(this.sunPathRadians);
        if (this.intervalSize != 0.0f) {
            this.modelView.translate(snapOffset(camera.x, this.intervalSize), snapOffset(camera.y, this.intervalSize),
                    snapOffset(camera.z, this.intervalSize));
        }
    }

    // Returns the projection matrix.
    public Matrix4f projection() {
        return this.projection;
    }

    // Returns the model view matrix.
    public Matrix4f modelView() {
        return this.modelView;
    }

    // Returns the near plane.
    public float nearPlane() {
        return this.nearPlane;
    }

    // Returns the far plane.
    public float farPlane() {
        return this.farPlane;
    }

    // Returns the field of view or null.
    public Float fov() {
        return this.fov;
    }
}
