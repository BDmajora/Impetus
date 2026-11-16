package com.bdmajora.dynamiclights.client;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.fluids.IFluidBlock;
import net.minecraftforge.fml.common.Loader;

// Tests whether a position holds a fluid.
public final class FluidHandler {
    // Whether the fluid logging mod is loaded.
    private static final boolean FLUIDLOGGING = Loader.isModLoaded("fluidlogged_api");

    // Prevents instantiation
    private FluidHandler() {
    }

    // Returns whether the eyes of the entity are in a fluid.
    public static boolean isFluid(Entity entity) {
        float partialTicks = Minecraft.getMinecraft().getRenderPartialTicks();
        return isFluid(entity.world, new BlockPos(entity.getPositionEyes(partialTicks)));
    }

    // Returns whether the position holds a fluid.
    public static boolean isFluid(IBlockAccess access, BlockPos pos) {
        IBlockState state = getFluidState(access, pos);
        return state.getMaterial().isLiquid() || state.getBlock() instanceof IFluidBlock;
    }

    // Returns the fluid state or the block state of the position.
    private static IBlockState getFluidState(IBlockAccess access, BlockPos pos) {
        return FLUIDLOGGING ? FluidloggedCompat.getFluidOrReal(access, pos) : access.getBlockState(pos);
    }
}
