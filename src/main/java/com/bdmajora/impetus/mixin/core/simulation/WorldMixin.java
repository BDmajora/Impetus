package com.bdmajora.impetus.mixin.core.simulation;

import com.bdmajora.impetus.impl.world.SimulationDistance;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(World.class)
public abstract class WorldMixin {
    @Inject(method = "updateEntityWithOptionalForce", at = @At("HEAD"), cancellable = true)
    private void impetus$limitEntityTicks(Entity entity, boolean tick, CallbackInfo ci) {
        if (tick && !(entity instanceof EntityPlayer)
                && !SimulationDistance.shouldTick((World) (Object) this,
                MathHelper.floor(entity.posX) >> 4, MathHelper.floor(entity.posZ) >> 4)) {
            ci.cancel();
        }
    }

    @WrapOperation(method = "updateEntities", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/World;isBlockLoaded(Lnet/minecraft/util/math/BlockPos;Z)Z"))
    private boolean impetus$limitBlockEntityTicks(World world, BlockPos pos, boolean allowEmpty, Operation<Boolean> original) {
        return SimulationDistance.shouldTick(world, pos) && original.call(world, pos, allowEmpty);
    }
}
