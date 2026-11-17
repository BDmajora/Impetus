package com.bdmajora.extras.mixin.sky_colors;

import com.bdmajora.extras.Extras;
import net.minecraft.world.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// Mixin that changes Biome.
@Mixin(Biome.class)
public class BiomeMixin {
    // Temperature every biome's sky colour is taken at when sky colours are uniform.
    @Unique
    private static final float IMPETUS$UNIFORM_TEMPERATURE = 0.8F;

    // Changes a local variable in getSkyColorByTemp.
    @ModifyVariable(method = "getSkyColorByTemp", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float impetus$uniformSkyColor(float temperature) {
        if (!Extras.options().detail.skyColors) {
            return IMPETUS$UNIFORM_TEMPERATURE;
        }
        return temperature;
    }
}
