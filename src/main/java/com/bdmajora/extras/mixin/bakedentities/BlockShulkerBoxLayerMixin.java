package com.bdmajora.extras.mixin.bakedentities;

import com.bdmajora.extras.client.bakedentities.BakedEntities;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.BlockShulkerBox;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockRenderLayer;
import org.spongepowered.asm.mixin.Mixin;

// Mixin that changes BlockShulkerBox.
@Mixin(BlockShulkerBox.class)
public abstract class BlockShulkerBoxLayerMixin extends BlockContainer {
    // Creates a block shulker box layer mixin from its material.
    private BlockShulkerBoxLayerMixin(Material material) {
        super(material);
    }

    // Returns whether render in layer.
    @Override
    public boolean canRenderInLayer(IBlockState state, BlockRenderLayer layer) {
        if (BakedEntities.isBaked(state.getBlock())) {
            return layer == BlockRenderLayer.CUTOUT;
        }
        return super.canRenderInLayer(state, layer);
    }
}
