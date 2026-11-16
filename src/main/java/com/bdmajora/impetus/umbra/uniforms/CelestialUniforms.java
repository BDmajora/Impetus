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

// Sun and moon uniforms.
public final class CelestialUniforms {
    // Sun path rotation.
    private static volatile float sunPathRotation = 0.0f;

    // Prevents instantiation
    private CelestialUniforms() {
    }

    // Stores the sun path rotation.
    public static void setSunPathRotation(float degrees) {
        sunPathRotation = degrees;
    }

    // Returns the sun path rotation.
    public static float getSunPathRotation() {
        return sunPathRotation;
    }

    // Adds the sun and moon uniforms.
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

    // Returns the sun position.
    public static Vector3f getSunPosition() {
        return getCelestialPosition(100.0f);
    }

    // Returns the moon position.
    public static Vector3f getMoonPosition() {
        return getCelestialPosition(-100.0f);
    }

    // Returns the shadow light position.
    private static Vector3f getShadowLightPosition() {
        return getSunAngle() <= 0.5f ? getSunPosition() : getMoonPosition();
    }

    // Returns the shadow light position in world space.
    public static Vector3f getShadowLightPositionInWorldSpace() {
        Vector4f position = new Vector4f(0.0f, getSunAngle() <= 0.5f ? 100.0f : -100.0f, 0.0f, 0.0f);
        Matrix4f celestial = new Matrix4f();
        celestial.rotateY((float) Math.toRadians(-90.0));
        celestial.rotateZ((float) Math.toRadians(sunPathRotation));
        celestial.rotateX((float) Math.toRadians(getCelestialAngle() * 360.0f));
        celestial.transform(position);
        return new Vector3f(position.x, position.y, position.z);
    }

    // Reusable matrix.
    private static final Matrix4f SCRATCH_MATRIX = new Matrix4f();
    // Reusable four component vector.
    private static final Vector4f SCRATCH_4 = new Vector4f();
    // Reusable three component vector.
    private static final Vector3f SCRATCH_3 = new Vector3f();

    // Returns the view position of a body at a height.
    private static Vector3f getCelestialPosition(float y) {
        Vector4f position = SCRATCH_4.set(0.0f, y, 0.0f, 0.0f);
        Matrix4f celestial = SCRATCH_MATRIX.set(CapturedRenderingState.INSTANCE.getGbufferModelView());
        celestial.rotateY((float) Math.toRadians(-90.0));
        celestial.rotateZ((float) Math.toRadians(sunPathRotation));
        celestial.rotateX((float) Math.toRadians(getCelestialAngle() * 360.0f));
        celestial.transform(position);
        return SCRATCH_3.set(position.x, position.y, position.z);
    }

    // Returns the up position.
    public static Vector3f getUpPosition() {
        Vector4f up = SCRATCH_4.set(0.0f, 100.0f, 0.0f, 0.0f);
        CapturedRenderingState.INSTANCE.getGbufferModelView().transform(up);
        return SCRATCH_3.set(up.x, up.y, up.z);
    }

    // Returns the end flash position.
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
                CapturedRenderingState.INSTANCE.getGbufferModelViewCameraCentered().transform(position);
                return SCRATCH_3.set(position.x, position.y, position.z);
            }
        }
        return SCRATCH_3.zero();
    }

    // Returns the celestial angle.
    public static float getCelestialAngle() {
        World world = Minecraft.getMinecraft().world;
        if (world == null) {
            return 0.0f;
        }
        return world.getCelestialAngle(CapturedRenderingState.INSTANCE.getTickDelta());
    }

    // Returns the sun angle.
    public static float getSunAngle() {
        float celestialAngle = getCelestialAngle();
        return celestialAngle < 0.75f ? celestialAngle + 0.25f : celestialAngle - 0.75f;
    }

    // Returns the shadow angle.
    public static float getShadowAngle() {
        float sunAngle = getSunAngle();
        return sunAngle <= 0.5f ? sunAngle : sunAngle - 0.5f;
    }
}
