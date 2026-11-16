package com.bdmajora.impetus.engine.impl.render.viewport;

import com.bdmajora.impetus.engine.impl.render.viewport.frustum.Frustum;
import com.bdmajora.impetus.engine.impl.util.PositionUtil;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.RoundingMode;
import org.joml.Vector3d;
import org.joml.Vector3i;
import org.joml.Vector3ic;

// View of a camera with its frustum and position.
public final class Viewport {
    // Frustum of the view.
    private final Frustum frustum;
    // Camera position split into whole and fractional parts.
    private final CameraTransform transform;

    // Section of the camera.
    private final Vector3i chunkCoords;
    // Block of the camera.
    private final Vector3i blockCoords;

    // View projection matrix or null.
    private final @Nullable Matrix4fc vpMatrix;

    // Creates a viewport without a matrix.
    public Viewport(Frustum frustum, Vector3d position) {
        this(frustum, position, null);
    }

    // Creates a viewport and rejects a null frustum or position.
    public Viewport(Frustum frustum, Vector3d position, @Nullable Matrix4fc vpMatrix) {
        java.util.Objects.requireNonNull(position, "position");
        this.frustum = java.util.Objects.requireNonNull(frustum, "frustum");
        this.transform = new CameraTransform(position.x, position.y, position.z);

        this.vpMatrix = vpMatrix == null ? null : new Matrix4f(vpMatrix);

        this.chunkCoords = new Vector3i(
                PositionUtil.posToSectionCoord(position.x),
                PositionUtil.posToSectionCoord(position.y),
                PositionUtil.posToSectionCoord(position.z)
        );

        this.blockCoords = new Vector3i(position.x, position.y, position.z, RoundingMode.FLOOR);
    }

    // Returns whether a world box is inside the frustum.
    public boolean isBoxVisible(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return this.frustum.testAab(
                (float)(minX - this.transform.intX) - this.transform.fracX,
                (float)(minY - this.transform.intY) - this.transform.fracY,
                (float)(minZ - this.transform.intZ) - this.transform.fracZ,
                (float)(maxX - this.transform.intX) - this.transform.fracX,
                (float)(maxY - this.transform.intY) - this.transform.fracY,
                (float)(maxZ - this.transform.intZ) - this.transform.fracZ
        );
    }

    // Returns whether a cube around a point is inside the frustum.
    public boolean isBoxVisible(int intOriginX, int intOriginY, int intOriginZ, float floatSize) {
        return isBoxVisible(intOriginX, intOriginY, intOriginZ, floatSize, floatSize, floatSize);
    }

    // Returns whether a box around a point is inside the frustum.
    public boolean isBoxVisible(int intOriginX, int intOriginY, int intOriginZ, float floatSizeX, float floatSizeY, float floatSizeZ) {
        float floatOriginX = ((long) intOriginX - this.transform.intX) - this.transform.fracX;
        float floatOriginY = ((long) intOriginY - this.transform.intY) - this.transform.fracY;
        float floatOriginZ = ((long) intOriginZ - this.transform.intZ) - this.transform.fracZ;

        return this.frustum.testAab(
                floatOriginX - floatSizeX,
                floatOriginY - floatSizeY,
                floatOriginZ - floatSizeZ,

                floatOriginX + floatSizeX,
                floatOriginY + floatSizeY,
                floatOriginZ + floatSizeZ
        );
    }

    // Returns how a camera relative box relates to the frustum.
    public int intersectCameraRelativeBox(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        return this.frustum.intersectAab(minX, minY, minZ, maxX, maxY, maxZ);
    }

    // Returns the view projection matrix or null.
    public @Nullable Matrix4fc getVpMatrix() {
        return this.vpMatrix;
    }

    // Returns the frustum.
    public Frustum getFrustum() {
        return this.frustum;
    }

    // Returns the camera position.
    public CameraTransform getTransform() {
        return this.transform;
    }

    // Returns the section of the camera.
    public Vector3ic getChunkCoord() {
        return this.chunkCoords;
    }

    // Returns the block of the camera.
    public Vector3ic getBlockCoord() {
        return this.blockCoords;
    }
}
