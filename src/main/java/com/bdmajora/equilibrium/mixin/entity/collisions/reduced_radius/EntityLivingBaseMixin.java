package com.bdmajora.equilibrium.mixin.entity.collisions.reduced_radius;

import com.bdmajora.equilibrium.common.entity.ReducedRadiusQuery;
import com.google.common.base.Predicate;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

// The per-tick pushing check every living entity runs; the hottest AABB query on a busy server
@Mixin(EntityLivingBase.class)
public abstract class EntityLivingBaseMixin {
    @WrapOperation(method = "collideWithNearbyEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getEntitiesInAABBexcluding(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/AxisAlignedBB;Lcom/google/common/base/Predicate;)Ljava/util/List;"))
    private List<Entity> equilibrium$collideWithVanillaRadius(World world, Entity entity, AxisAlignedBB box, Predicate<? super Entity> predicate, Operation<List<Entity>> original) {
        if (entity != null && ReducedRadiusQuery.applies() && ReducedRadiusQuery.isEligible(entity)) {
            return ReducedRadiusQuery.getEntitiesInAABBexcluding(world, entity, box, predicate);
        }
        return original.call(world, entity, box, predicate);
    }
}
