package com.bdmajora.equilibrium.mixin.math.sine_lut;

import com.bdmajora.equilibrium.common.util.math.CompactSineLUT;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes MathHelper.
@Mixin(MathHelper.class)
public class MathHelperMixin {
    // Reference to the field sin table of the target.
    @Shadow
    @Final
    @Mutable
    private static float[] SIN_TABLE;

    // Runs before every return of static initializer.
    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void onClassInit(CallbackInfo ci) {
        CompactSineLUT.init(SIN_TABLE);

        SIN_TABLE = null;
    }

    // Replaces the whole method sin.
    @Overwrite
    public static float sin(float value) {
        return CompactSineLUT.sin(value);
    }

    // Replaces the whole method cos.
    @Overwrite
    public static float cos(float value) {
        return CompactSineLUT.cos(value);
    }
}
