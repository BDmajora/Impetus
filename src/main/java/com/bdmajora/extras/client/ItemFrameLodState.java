package com.bdmajora.extras.client;

import com.bdmajora.extras.client.itemfaces.ItemFaceCache;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.util.EnumFacing;

import java.util.Collections;
import java.util.List;

// Reduces the faces of distant item frames.
public final class ItemFrameLodState {
    // Whether the reduced detail applies.
    public static boolean active;

    // Prevents instantiation
    private ItemFrameLodState() {
    }

    // Returns the quads of the model and drops the side faces while the reduced detail applies.
    public static List<BakedQuad> filterLodQuads(IBakedModel model, IBlockState state, EnumFacing side, long rand) {
        List<BakedQuad> quads = ItemFaceCache.quads(model, state, side, rand);

        if (!active || side == null || !model.isGui3d()) {
            return quads;
        }

        if (side == EnumFacing.NORTH || side == EnumFacing.SOUTH) {
            return quads;
        }

        return Collections.emptyList();
    }
}
