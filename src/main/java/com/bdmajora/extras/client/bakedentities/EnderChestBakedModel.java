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

// The ender chest: the single chest model under its own texture, never paired
public final class EnderChestBakedModel extends BakedEntityModel {
    static final ResourceLocation TEXTURE = new ResourceLocation("minecraft", "entity/chest/ender");

    private final ModelChest model = new ModelChest();

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

    @Override
    protected TextureAtlasSprite particleSprite() {
        return BakedEntities.sprite(TEXTURE);
    }
}
