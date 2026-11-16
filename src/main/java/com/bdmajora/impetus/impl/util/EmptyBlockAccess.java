package com.bdmajora.impetus.impl.util;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import org.jetbrains.annotations.Nullable;

// Choices of the empty block access setting.
public enum EmptyBlockAccess implements IBlockAccess {
    // Value that selects instance.
    INSTANCE;

    // Air block state.
    private static final IBlockState AIR = Blocks.AIR.getDefaultState();

    // Returns the tile entity.
    @Override
    public @Nullable TileEntity getTileEntity(BlockPos pos) {
        return null;
    }

    // Returns the combined light.
    @Override
    public int getCombinedLight(BlockPos blockPos, int lightValue) {
        return 0;
    }

    // Returns the block state.
    @Override
    public IBlockState getBlockState(BlockPos pos) {
        return AIR;
    }

    // Returns whether air block.
    @Override
    public boolean isAirBlock(BlockPos pos) {
        return true;
    }

    // Returns the biome.
    @Override
    public Biome getBiome(BlockPos pos) {
        return Biomes.PLAINS;
    }

    // Returns the strong power.
    @Override
    public int getStrongPower(BlockPos blockPos, EnumFacing direction) {
        return 0;
    }

    // Returns the world type.
    @Override
    public WorldType getWorldType() {
        return WorldType.DEFAULT;
    }

    // Returns whether side solid.
    @Override
    public boolean isSideSolid(BlockPos blockPos, EnumFacing enumFacing, boolean bl) {
        return false;
    }
}
