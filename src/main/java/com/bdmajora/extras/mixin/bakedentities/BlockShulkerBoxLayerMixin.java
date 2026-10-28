package com.bdmajora.extras.mixin.bakedentities;

import com.bdmajora.extras.client.bakedentities.BakedEntities;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.BlockShulkerBox;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockRenderLayer;
import org.spongepowered.asm.mixin.Mixin;

// Baked shulker boxes go in the cutout layer, as the renderer alpha-tests them: the sheet makes the lid's lower rows see-through exactly where the base's teeth fill in, and the solid layer would paint those transparent texels over the teeth
@Mixin(BlockShulkerBox.class)
public abstract class BlockShulkerBoxLayerMixin extends BlockContainer {
    private BlockShulkerBoxLayerMixin(Material material) {
        super(material);
    }

    // Forge's per-layer query the chunk mesher asks for every layer, added to BlockShulkerBox as an override so no other block pays for the check
    @Override
    public boolean canRenderInLayer(IBlockState state, BlockRenderLayer layer) {
        if (BakedEntities.isBaked(state.getBlock())) {
            return layer == BlockRenderLayer.CUTOUT;
        }
        return super.canRenderInLayer(state, layer);
    }
}
