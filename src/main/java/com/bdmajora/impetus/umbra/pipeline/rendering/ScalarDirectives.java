package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.material.WorldRenderingSettings;
import com.bdmajora.impetus.umbra.shaderpack.ConstDirectives;
import com.bdmajora.impetus.umbra.targets.NoiseTexture;
import com.bdmajora.impetus.umbra.uniforms.EyeBrightnessTracker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// Scalar constants of a pack read once per load.
public final class ScalarDirectives {
    // Logger of this class.
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");
    // Default center depth half life.
    public static final float DEFAULT_CENTER_DEPTH_HALF_LIFE = 1.0f;
    // Default wetness half life.
    public static final float DEFAULT_WETNESS_HALF_LIFE = 600.0f;
    // Default dryness half life.
    public static final float DEFAULT_DRYNESS_HALF_LIFE = 200.0f;
    // Default eye brightness half life.
    public static final float DEFAULT_EYE_BRIGHTNESS_HALF_LIFE = 10.0f;
    // Largest noise texture size.
    public static final int MAX_NOISE_RESOLUTION = 4096;

    // Center depth half life.
    private final float centerDepthHalfLife;
    // Noise resolution.
    private final int noiseResolution;
    // Ambient occlusion level.
    private final float ambientOcclusionLevel;
    // Wetness half life.
    private final float wetnessHalfLife;
    // Dryness half life.
    private final float drynessHalfLife;
    // Eye brightness half life.
    private final float eyeBrightnessHalfLife;

    // Creates a scalar directives from its consts.
    public ScalarDirectives(ConstDirectives consts) {
        this.centerDepthHalfLife = consts.getFloat("centerDepthHalflife", DEFAULT_CENTER_DEPTH_HALF_LIFE);
        int noise = consts.getInt("noiseTextureResolution", NoiseTexture.DEFAULT_RESOLUTION);
        if (noise > MAX_NOISE_RESOLUTION) {
            LOGGER.warn("[Umbra] Pack requests noiseTextureResolution={}; clamping to {}", noise, MAX_NOISE_RESOLUTION);
            noise = MAX_NOISE_RESOLUTION;
        }
        this.noiseResolution = noise > 0 ? noise : NoiseTexture.DEFAULT_RESOLUTION;
        this.ambientOcclusionLevel = Math.max(0.0f, Math.min(1.0f, consts.getFloat("ambientOcclusionLevel", 1.0f)));
        this.wetnessHalfLife = consts.getFloat("wetnessHalflife", DEFAULT_WETNESS_HALF_LIFE);
        this.drynessHalfLife = consts.getFloat("drynessHalflife", DEFAULT_DRYNESS_HALF_LIFE);
        this.eyeBrightnessHalfLife = consts.getFloat("eyeBrightnessHalflife", DEFAULT_EYE_BRIGHTNESS_HALF_LIFE);
    }

    // Makes these values the active ones.
    public void publish() {
        WorldRenderingSettings.setAmbientOcclusionLevel(this.ambientOcclusionLevel);
        EyeBrightnessTracker.setHalfLives(this.wetnessHalfLife, this.drynessHalfLife, this.eyeBrightnessHalfLife);
    }

    // Restores the default values.
    public static void reset() {
        WorldRenderingSettings.setAmbientOcclusionLevel(1.0f);
        EyeBrightnessTracker.setHalfLives(DEFAULT_WETNESS_HALF_LIFE, DEFAULT_DRYNESS_HALF_LIFE, DEFAULT_EYE_BRIGHTNESS_HALF_LIFE);
    }

    // Returns the center depth half life.
    public float centerDepthHalfLife() {
        return this.centerDepthHalfLife;
    }

    // Returns the noise texture size.
    public int noiseResolution() {
        return this.noiseResolution;
    }

    // Returns the ambient occlusion level.
    public float ambientOcclusionLevel() {
        return this.ambientOcclusionLevel;
    }
}
