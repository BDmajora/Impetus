package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.material.WorldRenderingSettings;
import com.bdmajora.impetus.umbra.shaderpack.ConstDirectives;
import com.bdmajora.impetus.umbra.targets.NoiseTexture;
import com.bdmajora.impetus.umbra.uniforms.EyeBrightnessTracker;
import com.bdmajora.testing.Statics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScalarDirectivesTest {
    @AfterEach
    void vanilla() {
        ScalarDirectives.reset();
    }

    private static ScalarDirectives of(String text) {
        return new ScalarDirectives(new ConstDirectives(text));
    }

    @Test
    void absentDirectivesKeepUmbrasDefaults() {
        ScalarDirectives defaults = of("");
        assertEquals(1.0f, defaults.centerDepthHalfLife());
        assertEquals(NoiseTexture.DEFAULT_RESOLUTION, defaults.noiseResolution());
        assertEquals(1.0f, defaults.ambientOcclusionLevel());
    }

    @Test
    void theNoiseResolutionMustBePositiveAndIsCapped() {
        assertEquals(64, of("const int noiseTextureResolution = 64;").noiseResolution());
        assertEquals(NoiseTexture.DEFAULT_RESOLUTION, of("const int noiseTextureResolution = 0;").noiseResolution());
        assertEquals(NoiseTexture.DEFAULT_RESOLUTION, of("const int noiseTextureResolution = -8;").noiseResolution());
        assertEquals(ScalarDirectives.MAX_NOISE_RESOLUTION, of("const int noiseTextureResolution = 65536;").noiseResolution());
        assertEquals(ScalarDirectives.MAX_NOISE_RESOLUTION, of("const int noiseTextureResolution = 4096;").noiseResolution());
    }

    @Test
    void theAoLevelIsClampedAndInfiniteValuesStayInRange() {
        assertEquals(0.5f, of("const float ambientOcclusionLevel = 0.5;").ambientOcclusionLevel());
        assertEquals(0.0f, of("const float ambientOcclusionLevel = -2.0;").ambientOcclusionLevel());
        assertEquals(1.0f, of("const float ambientOcclusionLevel = 3;").ambientOcclusionLevel());
        assertEquals(1.0f, of("const float ambientOcclusionLevel = 1e40;").ambientOcclusionLevel());
        assertEquals(2.5f, of("const float centerDepthHalflife = 2.5;").centerDepthHalfLife());
    }

    @Test
    void publishHandsTheValuesOutAndResetGivesVanillaBack() {
        of(String.join("\n", "const float ambientOcclusionLevel = 0.25;", "const float wetnessHalflife = 300.0;",
                "const float drynessHalflife = 50.0;", "const float eyeBrightnessHalflife = 5.0;")).publish();
        assertEquals(0.25f, WorldRenderingSettings.getAmbientOcclusionLevel());
        assertEquals(300.0f, (float) Statics.get(EyeBrightnessTracker.class, "wetnessHalfLife"));
        assertEquals(50.0f, (float) Statics.get(EyeBrightnessTracker.class, "drynessHalfLife"));
        assertEquals(5.0f, (float) Statics.get(EyeBrightnessTracker.class, "eyeBrightnessHalfLife"));
        // The pack's AO level used to outlive it and flatten vanilla's once shaders were off
        ScalarDirectives.reset();
        assertEquals(1.0f, WorldRenderingSettings.getAmbientOcclusionLevel());
        assertEquals(600.0f, (float) Statics.get(EyeBrightnessTracker.class, "wetnessHalfLife"));
        assertEquals(200.0f, (float) Statics.get(EyeBrightnessTracker.class, "drynessHalfLife"));
        assertEquals(10.0f, (float) Statics.get(EyeBrightnessTracker.class, "eyeBrightnessHalfLife"));
    }

    @Test
    void stagesScannedSeparatelyStillLetTheLastDeclarationWin() {
        ConstDirectives consts = new ConstDirectives(List.of("const int noiseTextureResolution = 32;",
                "const int noiseTextureResolution = 128;"));
        assertEquals(128, new ScalarDirectives(consts).noiseResolution());
        // An unterminated declaration at the end of one stage no longer swallows the next stage's
        ConstDirectives split = new ConstDirectives(List.of("const int noiseTextureResolution = 32",
                "const int wetnessFrames = 8;"));
        assertEquals(8, split.getInt("wetnessFrames", -1));
        assertEquals(NoiseTexture.DEFAULT_RESOLUTION, new ScalarDirectives(split).noiseResolution());
        assertEquals(-1, new ConstDirectives("const int noiseTextureResolution = 32\nconst int wetnessFrames = 8;")
                .getInt("wetnessFrames", -1));
    }
}
