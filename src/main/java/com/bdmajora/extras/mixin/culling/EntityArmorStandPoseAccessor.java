package com.bdmajora.extras.mixin.culling;

import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.util.math.Rotations;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

// Mixin that exposes the pose fields of EntityArmorStand.
@Mixin(EntityArmorStand.class)
public interface EntityArmorStandPoseAccessor {
    // Returns the applied head pose.
    @Accessor("headRotation")
    Rotations impetus$getHeadPose();

    // Returns the applied body pose.
    @Accessor("bodyRotation")
    Rotations impetus$getBodyPose();

    // Returns the applied left arm pose.
    @Accessor("leftArmRotation")
    Rotations impetus$getLeftArmPose();

    // Returns the applied right arm pose.
    @Accessor("rightArmRotation")
    Rotations impetus$getRightArmPose();

    // Returns the applied left leg pose.
    @Accessor("leftLegRotation")
    Rotations impetus$getLeftLegPose();

    // Returns the applied right leg pose.
    @Accessor("rightLegRotation")
    Rotations impetus$getRightLegPose();

    // Returns whether the stand had the marker box at the last tick.
    @Accessor("wasMarker")
    boolean impetus$getWasMarker();

    // Stores whether the stand has the marker box.
    @Accessor("wasMarker")
    void impetus$setWasMarker(boolean marker);

    // Switches the stand between the marker box and the full box.
    @Invoker("updateBoundingBox")
    void impetus$updateMarkerBox(boolean marker);
}
