package com.bdmajora.impetus.umbra.uniforms;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3i;

// Camera position uniforms with a shifted origin that keeps the values small.
public final class CameraUniforms {
    // Tracker of the camera positions.
    private static final CameraPositionTracker TRACKER = new CameraPositionTracker();

    // Prevents instantiation
    private CameraUniforms() {
    }

    // Updates the tracker on every frame of a notifier.
    public static void attach(FrameUpdateNotifier notifier) {
        notifier.addListener(TRACKER::update);
    }

    // Scratch of the current position.
    private static final Vector3d CURRENT = new Vector3d();
    // Scratch of the previous position.
    private static final Vector3d PREVIOUS = new Vector3d();
    // Scratch of the current unshifted position.
    private static final Vector3d CURRENT_UNSHIFTED = new Vector3d();
    // Scratch of the previous unshifted position.
    private static final Vector3d PREVIOUS_UNSHIFTED = new Vector3d();
    // Scratch of the integer part of a position.
    private static final Vector3i INT_PART = new Vector3i();
    // Scratch of the fraction part of a position.
    private static final Vector3f FRACT_PART = new Vector3f();

    // Returns the shifted camera position of this frame with the camera offset.
    public static Vector3d getCurrentCameraPosition() {
        return CURRENT.set(TRACKER.getCurrentCameraPosition()).add(CapturedRenderingState.INSTANCE.getCameraOffset());
    }

    // Returns the shifted camera position of the last frame with its offset.
    public static Vector3d getPreviousCameraPosition() {
        return PREVIOUS.set(TRACKER.getPreviousCameraPosition())
                .add(CapturedRenderingState.INSTANCE.getPreviousCameraOffset());
    }

    // Returns the world camera position of this frame with the camera offset.
    public static Vector3d getCurrentCameraPositionUnshifted() {
        return CURRENT_UNSHIFTED.set(TRACKER.getCurrentCameraPositionUnshifted())
                .add(CapturedRenderingState.INSTANCE.getCameraOffset());
    }

    // Returns the world camera position of the last frame with its offset.
    public static Vector3d getPreviousCameraPositionUnshifted() {
        return PREVIOUS_UNSHIFTED.set(TRACKER.getPreviousCameraPositionUnshifted())
                .add(CapturedRenderingState.INSTANCE.getPreviousCameraOffset());
    }

    // Returns the world camera position of this frame without an offset.
    public static Vector3d getCurrentRenderOriginUnshifted() {
        return TRACKER.getCurrentCameraPositionUnshifted();
    }

    // Returns the floor of each component of a position.
    public static Vector3i getCameraPositionInt(Vector3d originalPos) {
        return INT_PART.set(
                (int) Math.floor(originalPos.x),
                (int) Math.floor(originalPos.y),
                (int) Math.floor(originalPos.z));
    }

    // Returns the fraction of each component of a position.
    public static Vector3f getCameraPositionFract(Vector3d originalPos) {
        return FRACT_PART.set(
                (float) (originalPos.x - Math.floor(originalPos.x)),
                (float) (originalPos.y - Math.floor(originalPos.y)),
                (float) (originalPos.z - Math.floor(originalPos.z)));
    }

    // Writes the interpolated position of the view entity or zero into a vector and returns it.
    private static Vector3d readUnshiftedCameraPosition(Vector3d dest) {
        Entity camera = Minecraft.getMinecraft().getRenderViewEntity();
        if (camera == null) {
            return dest.zero();
        }
        float tickDelta = CapturedRenderingState.INSTANCE.getTickDelta();
        return dest.set(
                camera.lastTickPosX + (camera.posX - camera.lastTickPosX) * tickDelta,
                camera.lastTickPosY + (camera.posY - camera.lastTickPosY) * tickDelta,
                camera.lastTickPosZ + (camera.posZ - camera.lastTickPosZ) * tickDelta);
    }

    // Current and previous camera positions with the origin shift.
    private static final class CameraPositionTracker {
        // Distance from the origin that triggers a shift.
        private static final double WALK_RANGE = 30000.0;
        // Jump between frames that triggers a shift.
        private static final double TP_RANGE = 1000.0;

        // Offset added to the world position.
        private final Vector3d shift = new Vector3d();
        // Shifted position of the last frame.
        private Vector3d previousCameraPosition = new Vector3d();
        // Shifted position of this frame.
        private Vector3d currentCameraPosition = new Vector3d();
        // World position of the last frame.
        private Vector3d previousCameraPositionUnshifted = new Vector3d();
        // World position of this frame.
        private Vector3d currentCameraPositionUnshifted = new Vector3d();

        // Returns the shift a component needs or zero.
        private static double getShift(double value, double prevValue) {
            if (Math.abs(value) > WALK_RANGE || Math.abs(value - prevValue) > TP_RANGE) {
                return -(value - (value % WALK_RANGE));
            }
            return 0.0;
        }

        // Moves the current positions to the previous ones and reads the new camera position and updates the shift.
        private void update() {
            // Swaps the vectors so the frame update allocates nothing
            Vector3d recycled = this.previousCameraPosition;
            this.previousCameraPosition = this.currentCameraPosition;
            this.currentCameraPosition = recycled;
            Vector3d recycledUnshifted = this.previousCameraPositionUnshifted;
            this.previousCameraPositionUnshifted = this.currentCameraPositionUnshifted;
            this.currentCameraPositionUnshifted = recycledUnshifted;
            readUnshiftedCameraPosition(this.currentCameraPositionUnshifted);
            this.currentCameraPosition.set(this.currentCameraPositionUnshifted).add(this.shift);
            updateShift();
        }

        // Shifts the horizontal axes that left the walk range or jumped.
        private void updateShift() {
            double dX = getShift(this.currentCameraPosition.x, this.previousCameraPosition.x);
            double dZ = getShift(this.currentCameraPosition.z, this.previousCameraPosition.z);
            if (dX != 0.0 || dZ != 0.0) {
                applyShift(dX, dZ);
            }
        }

        // Adds a shift to the offset and to both shifted positions.
        private void applyShift(double dX, double dZ) {
            this.shift.x += dX;
            this.currentCameraPosition.x += dX;
            this.previousCameraPosition.x += dX;
            this.shift.z += dZ;
            this.currentCameraPosition.z += dZ;
            this.previousCameraPosition.z += dZ;
        }

        // Returns the shifted position of this frame.
        private Vector3d getCurrentCameraPosition() {
            return this.currentCameraPosition;
        }

        // Returns the shifted position of the last frame.
        private Vector3d getPreviousCameraPosition() {
            return this.previousCameraPosition;
        }

        // Returns the world position of this frame.
        private Vector3d getCurrentCameraPositionUnshifted() {
            return this.currentCameraPositionUnshifted;
        }

        // Returns the world position of the last frame.
        private Vector3d getPreviousCameraPositionUnshifted() {
            return this.previousCameraPositionUnshifted;
        }
    }
}
