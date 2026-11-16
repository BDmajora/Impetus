package com.bdmajora.fulgor.lighting;

import net.minecraft.block.BlockLiquid;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraftforge.fluids.BlockFluidBase;

import java.util.function.ToIntBiFunction;

// Rules for which block faces pass light.
public final class FaceLightRules {
    // Face up.
    public static final int FACE_UP = 1 << EnumFacing.UP.ordinal();
    // Face down.
    public static final int FACE_DOWN = 1 << EnumFacing.DOWN.ordinal();

    // Prevents instantiation
    private FaceLightRules() {
    }

    // Returns the faces of a state that pass light.
    public static int openFaces(IBlockState state) {
        if (state.getBlock() instanceof BlockSlab) {
            if (((BlockSlab) state.getBlock()).isDouble()) {
                return 0;
            }
            return state.getValue(BlockSlab.HALF) == BlockSlab.EnumBlockHalf.TOP ? FACE_DOWN : FACE_UP;
        }
        if (state.getBlock() instanceof BlockStairs) {
            return state.getValue(BlockStairs.HALF) == BlockStairs.EnumHalf.TOP ? FACE_DOWN : FACE_UP;
        }
        if (state.useNeighborBrightness() || state.getBlock() instanceof BlockLiquid || state.getBlock() instanceof BlockFluidBase) {
            return FACE_UP;
        }
        return 0;
    }

    // Returns the brightest neighbour light over the open faces.
    public static int foldOpenFaces(int faces, int level, EnumSkyBlock type, BlockPos pos, ToIntBiFunction<EnumSkyBlock, BlockPos> lightFor) {
        for (EnumFacing facing : EnumFacing.VALUES) {
            if (level >= 15) {
                break;
            }
            if ((faces & (1 << facing.ordinal())) != 0) {
                level = fold(level, lightFor.applyAsInt(type, pos.offset(facing)), type);
            }
        }
        return level;
    }

    // Returns the light a neighbour gives at one step.
    public static int fold(int level, int neighbour, EnumSkyBlock type) {
        int attenuation = type == EnumSkyBlock.SKY && neighbour == EnumSkyBlock.SKY.defaultLightValue ? 0 : 1;
        return Math.max(level, neighbour - attenuation);
    }

    // Returns the emission used for ambient occlusion.
    public static int ambientOcclusionEmission(int lightValue) {
        return Math.max(Math.min(lightValue - 1, 15), 0);
    }
}
