package com.bdmajora.equilibrium.mixin.world.ghost_chunks;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.BlockFarmland;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Mixin that changes BlockFarmland.
@Mixin(BlockFarmland.class)
public abstract class BlockFarmlandMixin {
    // Wraps the call of World.getBlockState in hasWater.
    @WrapOperation(method = "hasWater", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/state/IBlockState;"), require = 0, expect = 0)
    private IBlockState equilibrium$readLoadedOnly(World world, BlockPos pos, Operation<IBlockState> original) {
        return equilibrium$isUnloaded(world, pos) ? Blocks.AIR.getDefaultState() : original.call(world, pos);
    }

    // Wraps the call of FluidloggedUtils.getFluidOrReal in hasWater.
    @WrapOperation(method = "hasWater", at = @At(value = "INVOKE", target = "Lgit/jbredwards/fluidlogged_api/api/util/FluidloggedUtils;getFluidOrReal(Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/state/IBlockState;", remap = false), require = 0, expect = 0)
    private IBlockState equilibrium$readLoadedOnlyFluidlogged(IBlockAccess access, BlockPos pos, Operation<IBlockState> original) {
        return equilibrium$isUnloaded(access, pos) ? Blocks.AIR.getDefaultState() : original.call(access, pos);
    }

    // Returns whether a position's chunk is unloaded.
    private static boolean equilibrium$isUnloaded(IBlockAccess access, BlockPos pos) {
        if (!(access instanceof World)) {
            return false;
        }
        World world = (World) access;
        return world.isOutsideBuildHeight(pos) || world.getChunkProvider().getLoadedChunk(pos.getX() >> 4, pos.getZ() >> 4) == null;
    }
}
