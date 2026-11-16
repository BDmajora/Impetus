package com.bdmajora.impetus.umbra.vertices;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

// Packers of extended vertex data.
public final class ExtendedDataHelper {
    // Prevents instantiation
    private ExtendedDataHelper() {
    }

    // Returns the block id of a state.
    public static int getBlockId(IBlockState state) {
        return Block.getIdFromBlock(state.getBlock());
    }

    // Returns the metadata of a state.
    public static int getBlockData(IBlockState state) {
        return state.getBlock().getMetaFromState(state);
    }
}
