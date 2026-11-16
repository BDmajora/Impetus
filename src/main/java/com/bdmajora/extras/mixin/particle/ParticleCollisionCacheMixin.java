package com.bdmajora.extras.mixin.particle;

import com.bdmajora.extras.client.particle.ParticleTicker;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.particle.Particle;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Collections;
import java.util.List;

// Mixin that changes Particle.
@Mixin(Particle.class)
public abstract class ParticleCollisionCacheMixin {
    // Ticks between collision rechecks.
    private static final int RECHECK_TICKS = 10;

    // Field cell x added to the target.
    @Unique
    private int impetus$cellX = Integer.MIN_VALUE;

    // Field cell y added to the target.
    @Unique
    private int impetus$cellY;

    // Field cell z added to the target.
    @Unique
    private int impetus$cellZ;

    // Field cell empty added to the target.
    @Unique
    private boolean impetus$cellEmpty;

    // Field cell age added to the target.
    @Unique
    private int impetus$cellAge;

    // Wraps the call of World.getCollisionBoxes in move.
    @WrapOperation(method = "move", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/World;getCollisionBoxes(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/AxisAlignedBB;)Ljava/util/List;"))
    private List<AxisAlignedBB> impetus$cachedCollisions(World world, Entity entity, AxisAlignedBB sweep, Operation<List<AxisAlignedBB>> original) {
        if (!ParticleTicker.collisionCache) {
            return original.call(world, entity, sweep);
        }
        int cellX = MathHelper.floor(sweep.minX);
        int cellY = MathHelper.floor(sweep.minY);
        int cellZ = MathHelper.floor(sweep.minZ);
        boolean inside = sweep.maxX <= cellX + 1 && sweep.maxY <= cellY + 1 && sweep.maxZ <= cellZ + 1;
        if (!inside) {
            return original.call(world, entity, sweep);
        }
        if (cellX != this.impetus$cellX || cellY != this.impetus$cellY || cellZ != this.impetus$cellZ || ++this.impetus$cellAge >= RECHECK_TICKS) {
            this.impetus$cellX = cellX;
            this.impetus$cellY = cellY;
            this.impetus$cellZ = cellZ;
            this.impetus$cellAge = 0;
            this.impetus$cellEmpty = original.call(world, entity, new AxisAlignedBB(cellX, cellY, cellZ, cellX + 1, cellY + 1, cellZ + 1)).isEmpty();
        }
        if (this.impetus$cellEmpty) {
            return Collections.emptyList();
        }
        return original.call(world, entity, sweep);
    }
}
