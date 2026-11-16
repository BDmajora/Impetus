package com.bdmajora.extras.client.bakedentities;

import net.minecraft.block.BlockEnderChest;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.model.ModelChest;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;

import java.util.Collections;
import java.util.List;

// Baked model of ender chests.
public final class EnderChestBakedModel extends BakedEntityModel {
    // Texture of the ender chest.
    static final ResourceLocation TEXTURE = new ResourceLocation("minecraft", "entity/chest/ender");

    // Base model of the chest.
    private final ModelChest model = new ModelChest();

    // Returns the quads of the ender chest state.
    @Override
    protected List<BakedQuad> quadsFor(IBlockState state, BakedEntityContext context) {
        if (!(state.getBlock() instanceof BlockEnderChest)) {
            return null;
        }
        if (context != null && !settled(context.access, context.pos)) {
            return Collections.emptyList();
        }
        EnumFacing facing = state.getValue(BlockEnderChest.FACING);
        return variant(facing, k -> bake(facing));
    }

    // Returns the quads of one facing.
    private List<BakedQuad> bake(EnumFacing facing) {
        synchronized (this.model) {
            this.model.chestLid.rotateAngleX = 0.0F;
            this.model.chestKnob.rotateAngleX = 0.0F;
            EntityModelBaker baker = new EntityModelBaker(BakedEntities.sprite(TEXTURE));
            baker.bake(this.model.chestLid, chestMatrix(facing, 0.0F, 0.0F));
            baker.bake(this.model.chestKnob, chestMatrix(facing, 0.0F, 0.0F));
            baker.bake(this.model.chestBelow, chestMatrix(facing, 0.0F, 0.0F));
            return baker.quads();
        }
    }

    // Returns the sprite of the particles.
    @Override
    protected TextureAtlasSprite particleSprite() {
        return BakedEntities.sprite(TEXTURE);
    }
}
