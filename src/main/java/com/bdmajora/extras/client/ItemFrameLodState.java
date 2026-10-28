package com.bdmajora.extras.client;

import com.bdmajora.extras.client.itemfaces.ItemFaceCache;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.util.EnumFacing;

import java.util.Collections;
import java.util.List;

// Render-thread state for item-frame LOD (MoreCulling's "Frame LOD"): walls of framed blocks submit full models with four faces never visible; a plain static flag since it is render thread only
public final class ItemFrameLodState {
    // True only while rendering the contents of a framed item beyond the LOD distance
    public static boolean active;

    private ItemFrameLodState() {
    }

    // The model's quads for a face (coplanar faces resolved by ItemFaceCache), minus the four side faces when LOD is active on a 3D model; NORTH/SOUTH is the front/back axis under vanilla's zero-rotation display transform, and flat sprites have no sides
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
