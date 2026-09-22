package com.bdmajora.impetus.mixin.core;

import net.minecraft.util.EnumFacing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(EnumFacing.class)
public class MixinDirection {
    // Overwrite: picks the facing directly from the biggest axis instead of looping over all directions and dotting each
    @Overwrite
    public static EnumFacing getFacingFromVector(float x, float y, float z) {
        // Vanilla quirk: return NORTH if all entries are zero
        if (x == 0 && y == 0 && z == 0) {
            return EnumFacing.NORTH;
        }

        // First choice in ties: negative, positive; Y, Z, X
        float yM = Math.abs(y);
        float zM = Math.abs(z);
        float xM = Math.abs(x);

        if (yM >= zM && yM >= xM) {
            return y <= 0 ? EnumFacing.DOWN : EnumFacing.UP;
        }

        if (zM > yM && zM >= xM) {
            return z <= 0 ? EnumFacing.NORTH : EnumFacing.SOUTH;
        }

        return x <= 0 ? EnumFacing.WEST : EnumFacing.EAST;
    }
}
