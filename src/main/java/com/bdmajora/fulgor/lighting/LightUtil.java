package com.bdmajora.fulgor.lighting;

import com.bdmajora.fulgor.Fulgor;
import com.bdmajora.fulgor.api.LightInfoBlock;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

// Cheap answers to the questions the engine's innermost loop asks per position (six neighbours each, tens of thousands of positions per bulk edit)
public final class LightUtil {
    private static final IBlockState AIR = Blocks.AIR.getDefaultState();

    private LightUtil() {
    }

    // Skips Chunk.getBlockState's section re-derivation, bounds check and crash-report machinery; positions here are decoded from a known-good key in a resolved chunk
    public static IBlockState posToState(BlockPos pos, Chunk chunk) {
        return posToState(pos, chunk.getBlockStorageArray()[pos.getY() >> 4]);
    }

    // Section-local state read, masking the position to its 0-15 coordinates
    public static IBlockState posToState(BlockPos pos, ExtendedBlockStorage section) {
        if (section == Chunk.NULL_BLOCK_STORAGE) {
            return AIR;
        }

        return section.getData().get(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
    }

    // Skips the position-aware overload when LightInfoBlock knows the block does not override it, avoiding two megamorphic virtual calls Forge's default just forwards back to the state
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

    // Same fast-path trade as getLightValue but hotter: opacity is asked once per position plus once per each of six neighbours
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
