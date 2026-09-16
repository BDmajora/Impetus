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

// Farmland scans a 9x2x9 box for water on every random tick, and a field along a chunk border pulls the neighbouring chunk in to do it (FoamFix's ghostbuster, farmland); reads into an unloaded chunk answer air instead, which for a water search is the same as "no water here"
@Mixin(BlockFarmland.class)
public abstract class BlockFarmlandMixin {
    // Vanilla's read; absent when Fluidlogged API has rewritten it into the call below, so neither wrap is required
    @WrapOperation(method = "hasWater", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/state/IBlockState;"), require = 0, expect = 0)
    private IBlockState equilibrium$readLoadedOnly(World world, BlockPos pos, Operation<IBlockState> original) {
        return equilibrium$isUnloaded(world, pos) ? Blocks.AIR.getDefaultState() : original.call(world, pos);
    }

    // Fluidlogged API's replacement, which also sees fluid stored inside a block; the same guard in front of it
    @WrapOperation(method = "hasWater", at = @At(value = "INVOKE", target = "Lgit/jbredwards/fluidlogged_api/api/util/FluidloggedUtils;getFluidOrReal(Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/state/IBlockState;", remap = false), require = 0, expect = 0)
    private IBlockState equilibrium$readLoadedOnlyFluidlogged(IBlockAccess access, BlockPos pos, Operation<IBlockState> original) {
        return equilibrium$isUnloaded(access, pos) ? Blocks.AIR.getDefaultState() : original.call(access, pos);
    }

    // Only a World can load chunks on read; any other access answers from what it holds
    private static boolean equilibrium$isUnloaded(IBlockAccess access, BlockPos pos) {
        if (!(access instanceof World)) {
            return false;
        }
        World world = (World) access;
        return world.isOutsideBuildHeight(pos) || world.getChunkProvider().getLoadedChunk(pos.getX() >> 4, pos.getZ() >> 4) == null;
    }
}
