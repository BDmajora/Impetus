package com.bdmajora.extras.client.culling;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.ExtrasConfig;
import com.bdmajora.extras.mixin.culling.EntityArmorStandPoseAccessor;
import com.bdmajora.extras.mixin.culling.EntityLivingBaseInterpolationAccessor;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Rotations;

// Light client tick of hidden armor stands that keeps their movement and pose current.
public final class DormantTicks {
    // Armor stands paused during the running tick.
    private static int pausedThisTick;
    // Armor stands paused during the last finished tick.
    private static volatile int lastPaused;

    // Prevents instantiation
    private DormantTicks() {
    }

    // Returns whether the full client tick of the entity is skipped.
    public static boolean shouldPause(Entity entity) {
        if (!(entity instanceof EntityArmorStand) || entity.world == null || !entity.world.isRemote) {
            return false;
        }
        ExtrasConfig.OcclusionSettings settings = Extras.options().occlusion;
        if (!settings.pauseHidden || entity.isRiding() || entity.isBeingRidden()) {
            return false;
        }
        return OcclusionCulling.isHidden(entity);
    }

    // Runs the light tick of a paused armor stand.
    public static void tick(EntityArmorStand stand) {
        pausedThisTick++;
        stand.prevPosX = stand.posX;
        stand.prevPosY = stand.posY;
        stand.prevPosZ = stand.posZ;
        stand.prevRenderYawOffset = stand.renderYawOffset;
        stand.prevRotationYawHead = stand.rotationYawHead;
        stepInterpolation(stand, (EntityLivingBaseInterpolationAccessor) stand);
        // Copies the body yaw the way the armor stand does every tick.
        stand.prevRenderYawOffset = stand.prevRotationYaw;
        stand.renderYawOffset = stand.rotationYaw;
        stand.prevRotationYaw = wrapPrevious(stand.rotationYaw, stand.prevRotationYaw);
        stand.prevRenderYawOffset = wrapPrevious(stand.renderYawOffset, stand.prevRenderYawOffset);
        stand.prevRotationPitch = wrapPrevious(stand.rotationPitch, stand.prevRotationPitch);
        stand.prevRotationYawHead = wrapPrevious(stand.rotationYawHead, stand.prevRotationYawHead);
        syncPose(stand, (EntityArmorStandPoseAccessor) stand);
    }

    // Moves the entity one step toward the position and rotation the server sent.
    static void stepInterpolation(EntityLivingBase entity, EntityLivingBaseInterpolationAccessor access) {
        int steps = access.impetus$getInterpolationSteps();
        if (steps <= 0 || entity.canPassengerSteer()) {
            return;
        }
        double x = entity.posX + (access.impetus$getInterpolationX() - entity.posX) / steps;
        double y = entity.posY + (access.impetus$getInterpolationY() - entity.posY) / steps;
        double z = entity.posZ + (access.impetus$getInterpolationZ() - entity.posZ) / steps;
        double yawChange = MathHelper.wrapDegrees(access.impetus$getInterpolationYaw() - entity.rotationYaw);
        float yaw = (float) (entity.rotationYaw + yawChange / steps);
        float pitch = (float) (entity.rotationPitch + (access.impetus$getInterpolationPitch() - entity.rotationPitch) / steps);
        access.impetus$setInterpolationSteps(steps - 1);
        entity.setPosition(x, y, z);
        entity.rotationYaw = yaw % 360.0F;
        entity.rotationPitch = pitch % 360.0F;
    }

    // Returns the previous angle shifted by whole turns to within half a turn of the current one.
    static float wrapPrevious(float current, float previous) {
        float difference = current - previous;
        if (!Float.isFinite(difference) || difference >= -180.0F && difference < 180.0F) {
            return previous;
        }
        return previous + 360.0F * (float) Math.floor((difference + 180.0F) / 360.0F);
    }

    // Applies the poses and marker flag the server synced.
    static void syncPose(EntityArmorStand stand, EntityArmorStandPoseAccessor access) {
        EntityDataManager data = stand.getDataManager();
        Rotations head = data.get(EntityArmorStand.HEAD_ROTATION);
        if (!head.equals(access.impetus$getHeadPose())) {
            stand.setHeadRotation(head);
        }
        Rotations body = data.get(EntityArmorStand.BODY_ROTATION);
        if (!body.equals(access.impetus$getBodyPose())) {
            stand.setBodyRotation(body);
        }
        Rotations leftArm = data.get(EntityArmorStand.LEFT_ARM_ROTATION);
        if (!leftArm.equals(access.impetus$getLeftArmPose())) {
            stand.setLeftArmRotation(leftArm);
        }
        Rotations rightArm = data.get(EntityArmorStand.RIGHT_ARM_ROTATION);
        if (!rightArm.equals(access.impetus$getRightArmPose())) {
            stand.setRightArmRotation(rightArm);
        }
        Rotations leftLeg = data.get(EntityArmorStand.LEFT_LEG_ROTATION);
        if (!leftLeg.equals(access.impetus$getLeftLegPose())) {
            stand.setLeftLegRotation(leftLeg);
        }
        Rotations rightLeg = data.get(EntityArmorStand.RIGHT_LEG_ROTATION);
        if (!rightLeg.equals(access.impetus$getRightLegPose())) {
            stand.setRightLegRotation(rightLeg);
        }
        boolean marker = stand.hasMarker();
        if (access.impetus$getWasMarker() != marker) {
            access.impetus$updateMarkerBox(marker);
            stand.preventEntitySpawning = !marker;
            access.impetus$setWasMarker(marker);
        }
    }

    // Publishes the count of the finished tick.
    public static void endTick() {
        lastPaused = pausedThisTick;
        pausedThisTick = 0;
    }

    // Returns the number of armor stands paused during the last tick.
    public static int lastPaused() {
        return lastPaused;
    }
}
