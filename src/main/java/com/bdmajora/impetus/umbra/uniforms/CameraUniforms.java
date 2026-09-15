package com.bdmajora.impetus.umbra.uniforms;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3i;

// Camera-position tracking matching Iris's precision contract: a BOUNDED float cameraPosition plus exact integer and fractional parts of the unshifted position, since a float loses sub-block precision tens of thousands of blocks out. The tracker follows the view entity's FEET (vanilla's render origin); every accessor adds CapturedRenderingState's camera offset so packs see the CAMERA point like Iris 1.17+, where the render origin is the camera itself (see CapturedRenderingState.cameraOffset)
public final class CameraUniforms {
    private static final CameraPositionTracker TRACKER = new CameraPositionTracker();

    private CameraUniforms() {
    }

    // Registers the per-frame position capture
    public static void attach(FrameUpdateNotifier notifier) {
        notifier.addListener(TRACKER::update);
    }

    // Shifted camera position, for cameraPosition
    public static Vector3d getCurrentCameraPosition() {
        return new Vector3d(TRACKER.getCurrentCameraPosition()).add(CapturedRenderingState.INSTANCE.getCameraOffset());
    }

    // Last frame's shifted camera position; the previous offset pairs with it since both roll once per frame
    public static Vector3d getPreviousCameraPosition() {
        return new Vector3d(TRACKER.getPreviousCameraPosition())
                .add(CapturedRenderingState.INSTANCE.getPreviousCameraOffset());
    }

    // Raw world camera position
    public static Vector3d getCurrentCameraPositionUnshifted() {
        return new Vector3d(TRACKER.getCurrentCameraPositionUnshifted())
                .add(CapturedRenderingState.INSTANCE.getCameraOffset());
    }

    // Last frame's raw camera position
    public static Vector3d getPreviousCameraPositionUnshifted() {
        return new Vector3d(TRACKER.getPreviousCameraPositionUnshifted())
                .add(CapturedRenderingState.INSTANCE.getPreviousCameraOffset());
    }

    // The feet point itself, for the few pipeline-side consumers that pair a position with feet-relative geometry rather than with the pack-facing matrices
    public static Vector3d getCurrentRenderOriginUnshifted() {
        return TRACKER.getCurrentCameraPositionUnshifted();
    }

    // Integer part, for the split-precision uniform
    public static Vector3i getCameraPositionInt(Vector3d originalPos) {
        return new Vector3i(
                (int) Math.floor(originalPos.x),
                (int) Math.floor(originalPos.y),
                (int) Math.floor(originalPos.z));
    }

    // Fractional part
    public static Vector3f getCameraPositionFract(Vector3d originalPos) {
        return new Vector3f(
                (float) (originalPos.x - Math.floor(originalPos.x)),
                (float) (originalPos.y - Math.floor(originalPos.y)),
                (float) (originalPos.z - Math.floor(originalPos.z)));
    }

    // Interpolated FEET position of the render view entity, vanilla's render origin; the camera offset is added by the accessors above
    private static Vector3d getUnshiftedCameraPosition() {
        Entity camera = Minecraft.getMinecraft().getRenderViewEntity();
        if (camera == null) {
            return new Vector3d();
        }
        float tickDelta = CapturedRenderingState.INSTANCE.getTickDelta();
        return new Vector3d(
                camera.lastTickPosX + (camera.posX - camera.lastTickPosX) * tickDelta,
                camera.lastTickPosY + (camera.posY - camera.lastTickPosY) * tickDelta,
                camera.lastTickPosZ + (camera.posZ - camera.lastTickPosZ) * tickDelta);
    }

    private static final class CameraPositionTracker {
        // Iris's camera shift policy: the shader-facing float stays inside this range so it never loses precision, and the shift applies to current AND previous position together so motion-vector deltas are unaffected
        private static final double WALK_RANGE = 30000.0;
        private static final double TP_RANGE = 1000.0;

        private final Vector3d shift = new Vector3d();
        private Vector3d previousCameraPosition = new Vector3d();
        private Vector3d currentCameraPosition = new Vector3d();
        private Vector3d previousCameraPositionUnshifted = new Vector3d();
        private Vector3d currentCameraPositionUnshifted = new Vector3d();

        // How much to re-centre by when the camera crosses a shift boundary
        private static double getShift(double value, double prevValue) {
            if (Math.abs(value) > WALK_RANGE || Math.abs(value - prevValue) > TP_RANGE) {
                return -(value - (value % WALK_RANGE));
            }
            return 0.0;
        }

        // Per-frame capture and shift maintenance
        private void update() {
            this.previousCameraPosition = this.currentCameraPosition;
            this.previousCameraPositionUnshifted = this.currentCameraPositionUnshifted;
            this.currentCameraPositionUnshifted = getUnshiftedCameraPosition();
            this.currentCameraPosition = new Vector3d(this.currentCameraPositionUnshifted).add(this.shift);
            updateShift();
        }

        // Re-centres far from origin, matching OptiFine, so float precision holds
        private void updateShift() {
            double dX = getShift(this.currentCameraPosition.x, this.previousCameraPosition.x);
            double dZ = getShift(this.currentCameraPosition.z, this.previousCameraPosition.z);
            if (dX != 0.0 || dZ != 0.0) {
                applyShift(dX, dZ);
            }
        }

        // Applies a shift to both current and previous
        private void applyShift(double dX, double dZ) {
            this.shift.x += dX;
            this.currentCameraPosition.x += dX;
            this.previousCameraPosition.x += dX;
            this.shift.z += dZ;
            this.currentCameraPosition.z += dZ;
            this.previousCameraPosition.z += dZ;
        }

        // Instance accessor
        private Vector3d getCurrentCameraPosition() {
            return this.currentCameraPosition;
        }

        // Instance accessor
        private Vector3d getPreviousCameraPosition() {
            return this.previousCameraPosition;
        }

        // Instance accessor
        private Vector3d getCurrentCameraPositionUnshifted() {
            return this.currentCameraPositionUnshifted;
        }

        // Instance accessor
        private Vector3d getPreviousCameraPositionUnshifted() {
            return this.previousCameraPositionUnshifted;
        }
    }
}
