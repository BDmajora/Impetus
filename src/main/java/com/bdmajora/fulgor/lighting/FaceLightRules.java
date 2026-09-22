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

// Pulsar's answer to MC-92: a block that takes its brightness from its neighbours takes it only through the faces that are actually open, rather than vanilla's maximum over every neighbour, which lit a slab from a torch on the side its solid half faces
public final class FaceLightRules {
    public static final int FACE_UP = 1 << EnumFacing.UP.ordinal();
    public static final int FACE_DOWN = 1 << EnumFacing.DOWN.ordinal();

    private FaceLightRules() {
    }

    // Bitmask over EnumFacing ordinals of the faces light may enter through, or zero for a block that is lit by its own position; slabs and stairs open their free vertical face, everything else that asks for neighbour brightness opens the top
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

    // The level at pos raised by whatever reaches it through its open faces, one neighbour per open face; stops early at full brightness. lightFor is the caller's own getLightFor, since World and ChunkCache each have one but IBlockAccess declares none
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

    // Folds one open neighbour into the running level: an open face still attenuates by one, except that full skylight stays full so a surface open to the sky reads 15
    public static int fold(int level, int neighbour, EnumSkyBlock type) {
        int attenuation = type == EnumSkyBlock.SKY && neighbour == EnumSkyBlock.SKY.defaultLightValue ? 0 : 1;
        return Math.max(level, neighbour - attenuation);
    }

    // The emission a block counts as for ambient occlusion: a level-one emitter (brown mushroom, some plants) keeps smooth lighting, stronger ones do not shade themselves (MC-249343, MC-50734)
    public static int ambientOcclusionEmission(int lightValue) {
        return Math.max(Math.min(lightValue - 1, 15), 0);
    }
}
