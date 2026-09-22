package com.bdmajora.extras.mixin.booster;

import com.bdmajora.extras.client.booster.FastMath;
import com.bdmajora.impetus.engine.impl.common.util.MathUtil;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

// GPU Booster's fast math on the MathHelper routines that still cost something here; each overwrite keeps vanilla's exact expression on its off branch, and Equilibrium's sine mixin on the same class touches only sin/cos
@Mixin(MathHelper.class)
public class MathHelperFastMixin {
    // Overwrite: floor-based wrap instead of the float remainder
    @Overwrite
    public static float wrapDegrees(float value) {
        if (FastMath.enabled) {
            return FastMath.wrapDegrees(value);
        }

        value = value % 360.0F;
        if (value >= 180.0F) {
            value -= 360.0F;
        }
        if (value < -180.0F) {
            value += 360.0F;
        }
        return value;
    }

    // Overwrite: floor-based wrap instead of the double remainder
    @Overwrite
    public static double wrapDegrees(double value) {
        if (FastMath.enabled) {
            return FastMath.wrapDegrees(value);
        }

        value = value % 360.0D;
        if (value >= 180.0D) {
            value -= 360.0D;
        }
        if (value < -180.0D) {
            value += 360.0D;
        }
        return value;
    }

    // Overwrite: leading-zero count instead of the de Bruijn multiply and table
    @Overwrite
    public static int log2DeBruijn(int value) {
        if (FastMath.enabled) {
            return FastMath.ceilLog2(value);
        }

        value = MathUtil.isPowerOfTwo(value) ? value : MathHelper.smallestEncompassingPowerOfTwo(value);
        return DE_BRUIJN[(int) ((long) value * 125613361L >> 27) & 31];
    }

    // Overwrite: leading-zero count instead of two de Bruijn lookups
    @Overwrite
    public static int log2(int value) {
        if (FastMath.enabled) {
            return FastMath.floorLog2(value);
        }
        return log2DeBruijn(value) - (MathUtil.isPowerOfTwo(value) ? 0 : 1);
    }

    // Vanilla's private table, duplicated here since the off branch must not depend on the shadowed original staying reachable
    private static final int[] DE_BRUIJN = {
            0, 1, 28, 2, 29, 14, 24, 3, 30, 22, 20, 15, 25, 17, 4, 8,
            31, 27, 13, 23, 21, 19, 16, 7, 26, 12, 18, 6, 11, 5, 10, 9
    };
}
