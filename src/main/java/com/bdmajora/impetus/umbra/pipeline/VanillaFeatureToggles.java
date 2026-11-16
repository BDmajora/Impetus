package com.bdmajora.impetus.umbra.pipeline;

import com.bdmajora.impetus.umbra.Umbra;
import com.bdmajora.impetus.umbra.shaderpack.ShaderPack;
import com.bdmajora.impetus.umbra.shaderpack.ShaderProperties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;

// Switches of vanilla effects that the pack turns off.
public final class VanillaFeatureToggles {
    // Prevents instantiation
    private VanillaFeatureToggles() {
    }

    // Returns whether a pack switch is on or the default.
    private static boolean isEnabled(Function<ShaderProperties, Optional<Boolean>> directive) {
        ShaderPack pack = Umbra.getCurrentPack();
        if (pack == null || Umbra.getRenderingPipeline() == null) {
            return true;
        }
        return directive.apply(pack.getProperties()).orElse(Boolean.TRUE);
    }

    // Returns whether render sun.
    public static boolean shouldRenderSun() {
        return isEnabled(ShaderProperties::getRenderSun);
    }

    // Returns whether render moon.
    public static boolean shouldRenderMoon() {
        return isEnabled(ShaderProperties::getRenderMoon);
    }

    // Returns whether render stars.
    public static boolean shouldRenderStars() {
        return isEnabled(ShaderProperties::getRenderStars);
    }

    // Returns whether render sky.
    public static boolean shouldRenderSky() {
        return isEnabled(ShaderProperties::getRenderSky);
    }

    // Returns whether render vignette.
    public static boolean shouldRenderVignette() {
        return isEnabled(ShaderProperties::getRenderVignette);
    }

    // Returns whether render underwater overlay.
    public static boolean shouldRenderUnderwaterOverlay() {
        return isEnabled(ShaderProperties::getRenderUnderwaterOverlay);
    }

    // Returns whether render weather.
    public static boolean shouldRenderWeather() {
        return isEnabled(ShaderProperties::getRenderWeather);
    }

    // Returns the cloud mode.
    public static OptionalInt getCloudMode() {
        ShaderPack pack = Umbra.getCurrentPack();
        if (pack == null || Umbra.getRenderingPipeline() == null) {
            return OptionalInt.empty();
        }

        String mode = pack.getProperties().getCloudMode().orElse("").trim();
        switch (mode) {
            case "":
                return OptionalInt.empty();
            case "off":
                return OptionalInt.of(0);
            case "fast":
                return OptionalInt.of(1);
            case "fancy":
                return OptionalInt.of(2);
            default:
                if (!mode.equals(warnedCloudMode)) {
                    warnedCloudMode = mode;
                    LOGGER.error("[Umbra] Unrecognized clouds setting: {}", mode);
                }
                return OptionalInt.empty();
        }
    }

    // Logger of this class.
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");
    // Cloud mode that was last warned about.
    private static String warnedCloudMode;
}
