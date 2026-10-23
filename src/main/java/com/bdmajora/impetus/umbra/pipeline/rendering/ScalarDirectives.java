package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.material.WorldRenderingSettings;
import com.bdmajora.impetus.umbra.shaderpack.ConstDirectives;
import com.bdmajora.impetus.umbra.targets.NoiseTexture;
import com.bdmajora.impetus.umbra.uniforms.EyeBrightnessTracker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// The pack-wide scalar const directives Umbra collects into PackDirectives, with its defaults; the half-lives are in deciseconds (SmoothedFloat's unit), centerDepthHalflife in seconds
public final class ScalarDirectives {
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");
    public static final float DEFAULT_CENTER_DEPTH_HALF_LIFE = 1.0f;
    public static final float DEFAULT_WETNESS_HALF_LIFE = 600.0f;
    public static final float DEFAULT_DRYNESS_HALF_LIFE = 200.0f;
    public static final float DEFAULT_EYE_BRIGHTNESS_HALF_LIFE = 10.0f;
    // NoiseTexture allocates resolution^2 * 4 bytes twice, so a typo like 65536 would OOM the client; 4096 is far past anything real
    public static final int MAX_NOISE_RESOLUTION = 4096;

    private final float centerDepthHalfLife;
    private final int noiseResolution;
    private final float ambientOcclusionLevel;
    private final float wetnessHalfLife;
    private final float drynessHalfLife;
    private final float eyeBrightnessHalfLife;

    public ScalarDirectives(ConstDirectives consts) {
        this.centerDepthHalfLife = consts.getFloat("centerDepthHalflife", DEFAULT_CENTER_DEPTH_HALF_LIFE);
        // noisetex size; a pack sampling `texture2D(noisetex, uv * 32)` against a 256x256 texture gets the wrong spatial frequency (Body Camera's water normals)
        int noise = consts.getInt("noiseTextureResolution", NoiseTexture.DEFAULT_RESOLUTION);
        if (noise > MAX_NOISE_RESOLUTION) {
            LOGGER.warn("[Umbra] Pack requests noiseTextureResolution={}; clamping to {}", noise, MAX_NOISE_RESOLUTION);
            noise = MAX_NOISE_RESOLUTION;
        }
        this.noiseResolution = noise > 0 ? noise : NoiseTexture.DEFAULT_RESOLUTION;
        // Vanilla's baked AO strength for the block-model AO computation: 1.0 is vanilla, 0.0 lets the pack do its own
        this.ambientOcclusionLevel = Math.max(0.0f, Math.min(1.0f, consts.getFloat("ambientOcclusionLevel", 1.0f)));
        this.wetnessHalfLife = consts.getFloat("wetnessHalflife", DEFAULT_WETNESS_HALF_LIFE);
        this.drynessHalfLife = consts.getFloat("drynessHalflife", DEFAULT_DRYNESS_HALF_LIFE);
        this.eyeBrightnessHalfLife = consts.getFloat("eyeBrightnessHalflife", DEFAULT_EYE_BRIGHTNESS_HALF_LIFE);
    }

    // Hands the AO level to the mesher and the smoothing rates to the wetness/eyeBrightnessSmooth trackers
    public void publish() {
        WorldRenderingSettings.setAmbientOcclusionLevel(this.ambientOcclusionLevel);
        EyeBrightnessTracker.setHalfLives(this.wetnessHalfLife, this.drynessHalfLife, this.eyeBrightnessHalfLife);
    }

    // Back to vanilla's AO and the default rates once no pack is rendering; the pack's AO level used to outlive it and flatten vanilla's
    public static void reset() {
        WorldRenderingSettings.setAmbientOcclusionLevel(1.0f);
        EyeBrightnessTracker.setHalfLives(DEFAULT_WETNESS_HALF_LIFE, DEFAULT_DRYNESS_HALF_LIFE, DEFAULT_EYE_BRIGHTNESS_HALF_LIFE);
    }

    // centerDepthSmooth's half-life in seconds
    public float centerDepthHalfLife() {
        return this.centerDepthHalfLife;
    }

    // noisetex edge length, positive and capped
    public int noiseResolution() {
        return this.noiseResolution;
    }

    // Vanilla AO strength, 0..1
    public float ambientOcclusionLevel() {
        return this.ambientOcclusionLevel;
    }
}
