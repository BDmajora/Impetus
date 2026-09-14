package com.bdmajora.extras.client.bakedentities;

import net.minecraft.block.BlockBed;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.model.ModelBed;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityBed;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import org.joml.Matrix4f;

import java.util.List;

// Beds never animate, so both halves are always static; the colour lives on the block entity and is read through the pinned context
public final class BedBakedModel extends BakedEntityModel {
    private final ModelBed model = new ModelBed();

    static ResourceLocation texture(EnumDyeColor color) {
        return new ResourceLocation("minecraft", "entity/bed/" + color.getDyeColorName());
    }

    @Override
    protected List<BakedQuad> quadsFor(IBlockState state, BakedEntityContext context) {
        if (!(state.getBlock() instanceof BlockBed)) {
            return null;
        }
        EnumFacing facing = state.getValue(BlockBed.FACING);
        boolean head = state.getValue(BlockBed.PART) == BlockBed.EnumPartType.HEAD;
        EnumDyeColor color = EnumDyeColor.RED;
        if (context != null) {
            TileEntity tile = context.access.getTileEntity(context.pos);
            if (tile instanceof TileEntityBed) {
                color = ((TileEntityBed) tile).getColor();
            }
        }
        EnumDyeColor finalColor = color;
        int key = facing.getHorizontalIndex() | (head ? 4 : 0) | (color.getMetadata() << 3);
        return variant(key, k -> bake(facing, head, finalColor));
    }

    // TileEntityBedRenderer.renderPiece: a per-facing corner offset, lay the model flat, spin it, then the piece's own parts
    private List<BakedQuad> bake(EnumFacing facing, boolean head, EnumDyeColor color) {
        float degrees = 0.0F;
        float offsetX = 0.0F;
        float offsetZ = 0.0F;
        if (facing == EnumFacing.SOUTH) {
            degrees = 180.0F;
            offsetX = 1.0F;
            offsetZ = 1.0F;
        } else if (facing == EnumFacing.WEST) {
            degrees = -90.0F;
            offsetZ = 1.0F;
        } else if (facing == EnumFacing.EAST) {
            degrees = 90.0F;
            offsetX = 1.0F;
        }
        Matrix4f matrix = new Matrix4f();
        matrix.translate(offsetX, 0.5625F, offsetZ);
        matrix.rotateX((float) Math.toRadians(90.0F));
        matrix.rotateZ((float) Math.toRadians(degrees));

        synchronized (this.model) {
            this.model.preparePiece(head);
            EntityModelBaker baker = new EntityModelBaker(BakedEntities.sprite(texture(color)));
            baker.bake(this.model.headPiece, matrix);
            baker.bake(this.model.footPiece, matrix);
            for (int i = 0; i < 4; i++) {
                baker.bake(this.model.legs[i], matrix);
            }
            return baker.quads();
        }
    }

    @Override
    protected TextureAtlasSprite particleSprite() {
        return BakedEntities.sprite(texture(EnumDyeColor.RED));
    }
}
