package com.bdmajora.equilibrium.mixin.math.fast_blockpos;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.Vec3i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes EnumFacing.
@Mixin(EnumFacing.class)
public class EnumFacingMixin {
    // Cached x offset.
    private int equilibrium$offsetX;
    // Cached y offset.
    private int equilibrium$offsetY;
    // Cached z offset.
    private int equilibrium$offsetZ;

    // Runs before every return of constructor.
    @Inject(method = "<init>", at = @At("RETURN"))
    private void equilibrium$captureOffsets(String enumName, int ordinal, int index, int opposite,
                                            // Horizontal index and name.
                                            int horizontalIndex, String name,
                                            // Axis direction and axis.
                                            EnumFacing.AxisDirection axisDirection, EnumFacing.Axis axis,
                                            // Direction vector.
                                            Vec3i directionVec, CallbackInfo ci) {
        this.equilibrium$offsetX = directionVec.getX();
        this.equilibrium$offsetY = directionVec.getY();
        this.equilibrium$offsetZ = directionVec.getZ();
    }

    // Replaces the whole method getXOffset.
    @Overwrite
    public int getXOffset() {
        return this.equilibrium$offsetX;
    }

    // Replaces the whole method getYOffset.
    @Overwrite
    public int getYOffset() {
        return this.equilibrium$offsetY;
    }

    // Replaces the whole method getZOffset.
    @Overwrite
    public int getZOffset() {
        return this.equilibrium$offsetZ;
    }
}
