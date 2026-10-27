package com.bdmajora.impetus.umbra.pipeline.shadow;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3d;

import java.util.Objects;

// Shadow projection and model view matrices of the sun or moon camera snapped to a world grid
public final class ShadowCamera {
    // Rotation by a quarter turn around the X axis that tips the camera to look straight down
    private static final Matrix4fc QUARTER_TURN = new Matrix4f().rotationX((float) Math.toRadians(90.0f));

    // Near plane distance in blocks
    private final float nearPlane;
    // Far plane distance in blocks
    private final float farPlane;
    // Grid step in blocks that the camera position snaps to or 0 for no snapping
    private final float intervalSize;
    // Field of view in degrees for a perspective projection or null for an orthographic one
    private final Float fov;
    // Tilt of the daily sun path in radians
    private final float sunPathRadians;
    // Projection built once from the settings
    private final Matrix4f projectionTemplate;
    // Projection of the current pass
    private final Matrix4f projection = new Matrix4f();
    // Model view of the current pass
    private final Matrix4f modelView = new Matrix4f();

    // Creates the camera and builds its projection
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

    // Returns the perspective projection with the field of view in degrees and the given planes
    static Matrix4f perspective(float fov, float nearPlane, float farPlane) {
        float yScale = (float) (1.0f / Math.tan(Math.toRadians(fov) * 0.5f));
        return new Matrix4f(
                yScale, 0.0f, 0.0f, 0.0f,
                0.0f, yScale, 0.0f, 0.0f,
                0.0f, 0.0f, (farPlane + nearPlane) / (nearPlane - farPlane), -1.0f,
                0.0f, 0.0f, 2.0f * farPlane * nearPlane / (nearPlane - farPlane), 1.0f);
    }

    // Returns the sky angle that the light rotation uses for a shadow angle
    static float skyAngle(float shadowAngle) {
        return shadowAngle < 0.25f ? shadowAngle + 0.75f : shadowAngle - 0.25f;
    }

    // Returns the translation along one axis that snaps the camera to the grid cell centre
    static float snapOffset(double coordinate, float intervalSize) {
        return (float) coordinate % intervalSize - intervalSize / 2.0f;
    }

    // Rebuilds both matrices for a shadow angle and a camera position
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

    // Returns the live projection matrix which stays identity until the first update
    public Matrix4f projection() {
        return this.projection;
    }

    // Returns the live model view matrix which stays identity until the first update
    public Matrix4f modelView() {
        return this.modelView;
    }

    // Returns the near plane distance in blocks
    public float nearPlane() {
        return this.nearPlane;
    }

    // Returns the far plane distance in blocks
    public float farPlane() {
        return this.farPlane;
    }

    // Returns the field of view in degrees or null for an orthographic projection
    public Float fov() {
        return this.fov;
    }
}
