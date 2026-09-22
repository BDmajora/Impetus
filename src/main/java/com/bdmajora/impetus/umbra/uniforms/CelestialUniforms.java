package com.bdmajora.impetus.umbra.uniforms;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3d;
import org.joml.Vector4f;
import com.bdmajora.impetus.umbra.gl.uniform.UniformCollector;
import com.bdmajora.impetus.umbra.gl.uniform.UniformUpdateFrequency;

// Sun and moon uniforms; the angles are transcribed from OptiFine's setCamera, the directional ones derive from the captured gbufferModelView and are stale until the EntityRenderer mixin captures this frame
public final class CelestialUniforms {
    // The pack's sunPathRotation in degrees tilting the daily arc, applied in two places that must agree (celestial positions here AND the shadow model-view in UmbraShadowRenderer) or shadows point away from the lit side; set once per pack load, volatile for the render thread
    private static volatile float sunPathRotation = 0.0f;

    private CelestialUniforms() {
    }

    // Pack's sunPathRotation constant
    public static void setSunPathRotation(float degrees) {
        sunPathRotation = degrees;
    }

    // Current value
    public static float getSunPathRotation() {
        return sunPathRotation;
    }

    // sunPosition, moonPosition, shadowLightPosition and the angles
    public static void addCelestialUniforms(UniformCollector uniforms) {
        uniforms
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "celestialAngle", CelestialUniforms::getCelestialAngle)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "sunAngle", CelestialUniforms::getSunAngle)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "shadowAngle", CelestialUniforms::getShadowAngle)
                .uniform3f(UniformUpdateFrequency.PER_FRAME, "sunPosition", CelestialUniforms::getSunPosition)
                .uniform3f(UniformUpdateFrequency.PER_FRAME, "moonPosition", CelestialUniforms::getMoonPosition)
                .uniform3f(UniformUpdateFrequency.PER_FRAME, "shadowLightPosition", CelestialUniforms::getShadowLightPosition)
                .uniform3f(UniformUpdateFrequency.PER_FRAME, "endFlashPosition", CelestialUniforms::getEndFlashPosition)
                .uniform3f(UniformUpdateFrequency.PER_FRAME, "upPosition", CelestialUniforms::getUpPosition);
    }

    // Eye-space sun direction the modern-Iris way: captured gbufferModelView with vanilla's celestial rotation (rotate(-90, Y) then rotate(celestialAngle * 360, X), what renderSky pushes) applied to (0, 100, 0); identical to OptiFine's postCelestialRotate readback but needs no hook inside sky rendering
    public static Vector3f getSunPosition() {
        return getCelestialPosition(100.0f);
    }

    // Moon direction in view space
    public static Vector3f getMoonPosition() {
        return getCelestialPosition(-100.0f);
    }

    // The light that actually casts shadows: the sun while it is up (sunAngle <= 0.5), the moon after that
    private static Vector3f getShadowLightPosition() {
        return getSunAngle() <= 0.5f ? getSunPosition() : getMoonPosition();
    }

    // The shadow light direction in WORLD space matching Iris, same construction without gbufferModelView since the shadow frustum reasons about world-space plane normals and an eye-space vector would rotate the culling volume with the camera
    public static Vector3f getShadowLightPositionInWorldSpace() {
        Vector4f position = new Vector4f(0.0f, getSunAngle() <= 0.5f ? 100.0f : -100.0f, 0.0f, 0.0f);
        Matrix4f celestial = new Matrix4f();
        celestial.rotateY((float) Math.toRadians(-90.0));
        celestial.rotateZ((float) Math.toRadians(sunPathRotation));
        celestial.rotateX((float) Math.toRadians(getCelestialAngle() * 360.0f));
        celestial.transform(position);
        return new Vector3f(position.x, position.y, position.z);
    }

    // Render-thread scratch for the per-frame uniform suppliers below, whose callers copy the answer out before the next call; the world-space shadow direction above keeps its own allocation since the shadow frustum retains it
    private static final Matrix4f SCRATCH_MATRIX = new Matrix4f();
    private static final Vector4f SCRATCH_4 = new Vector4f();
    private static final Vector3f SCRATCH_3 = new Vector3f();

    // Rotates a body by the celestial angle and sun path into view space
    private static Vector3f getCelestialPosition(float y) {
        Vector4f position = SCRATCH_4.set(0.0f, y, 0.0f, 0.0f);
        Matrix4f celestial = SCRATCH_MATRIX.set(CapturedRenderingState.INSTANCE.getGbufferModelView());
        // renderSky's transform plus the pack's sunPathRotation, which Umbra applies as a Z-rotation between the fixed -90 Y-rotation and the time-of-day X-rotation
        celestial.rotateY((float) Math.toRadians(-90.0));
        celestial.rotateZ((float) Math.toRadians(sunPathRotation));
        celestial.rotateX((float) Math.toRadians(getCelestialAngle() * 360.0f));
        celestial.transform(position);
        return SCRATCH_3.set(position.x, position.y, position.z);
    }

    // Eye-space world-up, gbufferModelView * (0, 100, 0, 0) (OptiFine's setUpPosition); w = 0 makes it a direction so translation is ignored
    public static Vector3f getUpPosition() {
        Vector4f up = SCRATCH_4.set(0.0f, 100.0f, 0.0f, 0.0f);
        CapturedRenderingState.INSTANCE.getGbufferModelView().transform(up);
        return SCRATCH_3.set(up.x, up.y, up.z);
    }

    // Always zero; no End flash on 1.12.2
    private static Vector3f getEndFlashPosition() {
        World world = Minecraft.getMinecraft().world;
        if (world == null || world.provider.getDimension() != 1) {
            return SCRATCH_3.zero();
        }

        float tickDelta = CapturedRenderingState.INSTANCE.getTickDelta();
        Vector3d camera = CameraUniforms.getCurrentCameraPositionUnshifted();
        for (Entity entity : world.loadedEntityList) {
            if (entity instanceof EntityDragon && ((EntityDragon) entity).deathTicks > 0) {
                double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * tickDelta - camera.x;
                double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * tickDelta - camera.y;
                double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * tickDelta - camera.z;
                Vector4f position = SCRATCH_4.set((float) x, (float) y, (float) z, 1.0f);
                // Camera-relative input, so the camera-centred matrix, not the raw feet-relative capture
                CapturedRenderingState.INSTANCE.getGbufferModelViewCameraCentered().transform(position);
                return SCRATCH_3.set(position.x, position.y, position.z);
            }
        }
        return SCRATCH_3.zero();
    }

    // World celestial angle, 0..1
    public static float getCelestialAngle() {
        World world = Minecraft.getMinecraft().world;
        if (world == null) {
            return 0.0f;
        }
        return world.getCelestialAngle(CapturedRenderingState.INSTANCE.getTickDelta());
    }

    // Celestial angle offset so 0 is sunrise
    public static float getSunAngle() {
        float celestialAngle = getCelestialAngle();
        return celestialAngle < 0.75f ? celestialAngle + 0.25f : celestialAngle - 0.75f;
    }

    // Sun angle by day, moon angle by night
    public static float getShadowAngle() {
        float sunAngle = getSunAngle();
        return sunAngle <= 0.5f ? sunAngle : sunAngle - 0.5f;
    }
}
