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

// Particle Core's open-air shortcut: a particle drifting inside a block cell that has nothing to collide with, including the neighbours vanilla's query reaches into, keeps that answer until it leaves the cell, so the per-tick block sweep runs once per cell rather than once per tick
@Mixin(Particle.class)
public abstract class ParticleCollisionCacheMixin {
    // Answers go stale if the world changes under a long-lived particle, so they are re-asked periodically even within one cell
    private static final int RECHECK_TICKS = 10;

    @Unique
    private int impetus$cellX = Integer.MIN_VALUE;

    @Unique
    private int impetus$cellY;

    @Unique
    private int impetus$cellZ;

    @Unique
    private boolean impetus$cellEmpty;

    @Unique
    private int impetus$cellAge;

    @WrapOperation(method = "move", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/World;getCollisionBoxes(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/AxisAlignedBB;)Ljava/util/List;"))
    private List<AxisAlignedBB> impetus$cachedCollisions(World world, Entity entity, AxisAlignedBB sweep, Operation<List<AxisAlignedBB>> original) {
        if (!ParticleTicker.collisionCache) {
            return original.call(world, entity, sweep);
        }
        int cellX = MathHelper.floor(sweep.minX);
        int cellY = MathHelper.floor(sweep.minY);
        int cellZ = MathHelper.floor(sweep.minZ);
        // The sweep must sit inside one cell for the cached answer to cover it
        boolean inside = sweep.maxX <= cellX + 1 && sweep.maxY <= cellY + 1 && sweep.maxZ <= cellZ + 1;
        if (!inside) {
            return original.call(world, entity, sweep);
        }
        if (cellX != this.impetus$cellX || cellY != this.impetus$cellY || cellZ != this.impetus$cellZ || ++this.impetus$cellAge >= RECHECK_TICKS) {
            this.impetus$cellX = cellX;
            this.impetus$cellY = cellY;
            this.impetus$cellZ = cellZ;
            this.impetus$cellAge = 0;
            // Vanilla's query over the whole cell reaches one block out on every side, which is exactly what a sweep inside the cell could touch
            this.impetus$cellEmpty = original.call(world, entity, new AxisAlignedBB(cellX, cellY, cellZ, cellX + 1, cellY + 1, cellZ + 1)).isEmpty();
        }
        if (this.impetus$cellEmpty) {
            return Collections.emptyList();
        }
        return original.call(world, entity, sweep);
    }
}
