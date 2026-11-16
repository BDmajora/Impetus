package com.bdmajora.fulgor.lighting;

import com.bdmajora.fulgor.Fulgor;
import com.bdmajora.fulgor.api.LightInfoBlock;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

// Helpers for light opacity and emission.
public final class LightUtil {
    // Air block state.
    private static final IBlockState AIR = Blocks.AIR.getDefaultState();

    // Prevents instantiation
    private LightUtil() {
    }

    // Returns the state at a position of a chunk.
    public static IBlockState posToState(BlockPos pos, Chunk chunk) {
        int y = pos.getY();

        if (y < 0 || y > 255) {
            return AIR;
        }

        return posToState(pos, chunk.getBlockStorageArray()[y >> 4]);
    }

    // Returns the state at a position of a section.
    public static IBlockState posToState(BlockPos pos, ExtendedBlockStorage section) {
        if (section == Chunk.NULL_BLOCK_STORAGE) {
            return AIR;
        }

        return section.getData().get(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
    }

    // Returns the light value.
    @SuppressWarnings("deprecation")
    public static int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos, Chunk chunk) {
        if (Fulgor.hasDynamicLights() && DynamicLightsBridge.isAvailable()) {
            return DynamicLightsBridge.getLightValue(state, world, pos);
        }

        int value = Fulgor.useCachedBlockLightInfo()
                && !((LightInfoBlock) state.getBlock()).fulgor$hasPositionAwareLightValue()
                ? state.getLightValue()
                : state.getLightValue(world, pos);

        if (Fulgor.hasFluidloggedApi()) {
            return FluidLightCompat.getLightValue(value, world, pos, chunk);
        }

        return value;
    }

    // Returns the light opacity.
    @SuppressWarnings("deprecation")
    public static int getLightOpacity(IBlockState state, IBlockAccess world, BlockPos pos, Chunk chunk) {
        if (Fulgor.hasFluidloggedApi()) {
            return FluidLightCompat.getLightOpacity(state, world, pos, chunk);
        }

        return Fulgor.useCachedBlockLightInfo()
                && !((LightInfoBlock) state.getBlock()).fulgor$hasPositionAwareOpacity()
                ? state.getLightOpacity()
                : state.getLightOpacity(world, pos);
    }
}
