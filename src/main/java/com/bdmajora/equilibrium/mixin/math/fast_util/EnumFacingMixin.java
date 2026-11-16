package com.bdmajora.equilibrium.mixin.math.fast_util;

import net.minecraft.util.EnumFacing;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Random;

// Mixin that changes EnumFacing.
@Mixin(EnumFacing.class)
public class EnumFacingMixin {
    // Reference to the field values of the target.
    @Shadow
    @Final
    public static EnumFacing[] VALUES;

    // Reference to the field opposite of the target.
    @Shadow
    @Final
    private int opposite;

    // Replaces the whole method getOpposite.
    @Overwrite
    public EnumFacing getOpposite() {
        return VALUES[this.opposite];
    }

    // Replaces the whole method random.
    @Overwrite
    public static EnumFacing random(Random rand) {
        return VALUES[rand.nextInt(VALUES.length)];
    }
}
