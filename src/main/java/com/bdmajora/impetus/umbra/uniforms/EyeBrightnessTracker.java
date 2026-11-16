package com.bdmajora.impetus.umbra.uniforms;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.joml.Vector2f;
import org.joml.Vector2i;

// Tracker of the smoothed eye brightness and wetness.
public final class EyeBrightnessTracker {
    // Default wetness half life.
    private static final float DEFAULT_WETNESS_HALF_LIFE = 600.0f;
    // Default dryness half life.
    private static final float DEFAULT_DRYNESS_HALF_LIFE = 200.0f;
    // Default eye brightness half life.
    private static final float DEFAULT_EYE_BRIGHTNESS_HALF_LIFE = 10.0f;

    // Wetness half life.
    private static volatile float wetnessHalfLife = DEFAULT_WETNESS_HALF_LIFE;
    // Dryness half life.
    private static volatile float drynessHalfLife = DEFAULT_DRYNESS_HALF_LIFE;
    // Eye brightness half life.
    private static volatile float eyeBrightnessHalfLife = DEFAULT_EYE_BRIGHTNESS_HALF_LIFE;

    // Raw eye brightness.
    private static final Vector2i eyeBrightness = new Vector2i();
    // Smoothed eye brightness.
    private static final Vector2f smoothed = new Vector2f();
    // Smoothed wetness.
    private static float wetness;
    // Time of the last step or minus one.
    private static long lastUpdateNanos = -1L;

    // Prevents instantiation
    private EyeBrightnessTracker() {
    }

    // Stores the half lives.
    public static void setHalfLives(float wetnessDeciseconds, float drynessDeciseconds,
                                   float eyeBrightnessDeciseconds) {
        wetnessHalfLife = wetnessDeciseconds;
        drynessHalfLife = drynessDeciseconds;
        eyeBrightnessHalfLife = eyeBrightnessDeciseconds;
    }

    // Returns the smoothing factor of a half life.
    private static float smoothingFactor(float halfLifeDeciseconds, float deltaSeconds) {
        if (halfLifeDeciseconds <= 0.0f) {
            return 1.0f;
        }
        return 1.0f - (float) Math.pow(0.5, deltaSeconds / (halfLifeDeciseconds * 0.1f));
    }

    // Steps the smoothed values.
    public static void update() {
        long now = System.nanoTime();
        float deltaSeconds = lastUpdateNanos < 0 ? 1.0f : (now - lastUpdateNanos) / 1_000_000_000.0f;
        lastUpdateNanos = now;

        Minecraft mc = Minecraft.getMinecraft();
        Entity camera = mc.getRenderViewEntity();
        World world = mc.world;
        if (camera == null || world == null) {
            return;
        }
        int combined = camera.getBrightnessForRender();
        eyeBrightness.set(combined & 0xFFFF, combined >> 16);

        float factor = smoothingFactor(eyeBrightnessHalfLife, deltaSeconds);
        smoothed.x += (eyeBrightness.x - smoothed.x) * factor;
        smoothed.y += (eyeBrightness.y - smoothed.y) * factor;

        float rainStrength = world.getRainStrength(CapturedRenderingState.INSTANCE.getTickDelta());
        float wetnessFactor =
                smoothingFactor(rainStrength > wetness ? wetnessHalfLife : drynessHalfLife, deltaSeconds);
        wetness += (rainStrength - wetness) * wetnessFactor;
    }

    // Returns the eye brightness.
    public static Vector2i getEyeBrightness() {
        return new Vector2i(eyeBrightness);
    }

    // Returns the eye brightness smooth.
    public static Vector2i getEyeBrightnessSmooth() {
        return new Vector2i(Math.round(smoothed.x), Math.round(smoothed.y));
    }

    // Returns the wetness.
    public static float getWetness() {
        return wetness;
    }
}
