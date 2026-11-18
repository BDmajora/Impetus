package com.bdmajora.extras.mixin.culling;

import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Mixin that exposes the movement interpolation of EntityLivingBase.
@Mixin(EntityLivingBase.class)
public interface EntityLivingBaseInterpolationAccessor {
    // Returns the ticks left to reach the server position.
    @Accessor("newPosRotationIncrements")
    int impetus$getInterpolationSteps();

    // Stores the ticks left to reach the server position.
    @Accessor("newPosRotationIncrements")
    void impetus$setInterpolationSteps(int steps);

    // Returns the east position the server sent.
    @Accessor("interpTargetX")
    double impetus$getInterpolationX();

    // Returns the height the server sent.
    @Accessor("interpTargetY")
    double impetus$getInterpolationY();

    // Returns the south position the server sent.
    @Accessor("interpTargetZ")
    double impetus$getInterpolationZ();

    // Returns the yaw the server sent.
    @Accessor("interpTargetYaw")
    double impetus$getInterpolationYaw();

    // Returns the pitch the server sent.
    @Accessor("interpTargetPitch")
    double impetus$getInterpolationPitch();
}
