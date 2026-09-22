package com.bdmajora.extras.client.bakedentities;

import net.minecraft.block.BlockStandingSign;
import net.minecraft.block.BlockWallSign;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.model.ModelSign;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import org.joml.Matrix4f;

import java.util.List;

// Sign boards and posts; the text stays with the renderer, which keeps drawing it over the baked board
public final class SignBakedModel extends BakedEntityModel {
    static final ResourceLocation TEXTURE = new ResourceLocation("minecraft", "entity/sign");
    private static final float SCALE = 0.6666667F;

    private final ModelSign model = new ModelSign();

    @Override
    protected List<BakedQuad> quadsFor(IBlockState state, BakedEntityContext context) {
        if (state.getBlock() instanceof BlockStandingSign) {
            int rotation = state.getValue(BlockStandingSign.ROTATION);
            return variant(rotation, k -> bakeStanding(rotation));
        }
        if (state.getBlock() instanceof BlockWallSign) {
            EnumFacing facing = state.getValue(BlockWallSign.FACING);
            return variant(facing, k -> bakeWall(facing));
        }
        return null;
    }

    // TileEntitySignRenderer's standing branch: centre, spin by the sixteenth, then the flipped two-thirds scale
    private List<BakedQuad> bakeStanding(int rotation) {
        Matrix4f matrix = new Matrix4f();
        matrix.translate(0.5F, 0.5F, 0.5F);
        matrix.rotateY((float) Math.toRadians(-(rotation * 360) / 16.0F));
        matrix.scale(SCALE, -SCALE, -SCALE);
        return bake(matrix, true);
    }

    // The wall branch: spin to the wall, then step back and down onto it
    private List<BakedQuad> bakeWall(EnumFacing facing) {
        float degrees = switch (facing) {
            case NORTH -> 180.0F;
            case WEST -> 90.0F;
            case EAST -> -90.0F;
            default -> 0.0F;
        };
        Matrix4f matrix = new Matrix4f();
        matrix.translate(0.5F, 0.5F, 0.5F);
        matrix.rotateY((float) Math.toRadians(-degrees));
        matrix.translate(0.0F, -0.3125F, -0.4375F);
        matrix.scale(SCALE, -SCALE, -SCALE);
        return bake(matrix, false);
    }

    private List<BakedQuad> bake(Matrix4f matrix, boolean stick) {
        synchronized (this.model) {
            this.model.signStick.showModel = stick;
            EntityModelBaker baker = new EntityModelBaker(BakedEntities.sprite(TEXTURE));
            baker.bake(this.model.signBoard, matrix);
            baker.bake(this.model.signStick, matrix);
            return baker.quads();
        }
    }

    @Override
    protected TextureAtlasSprite particleSprite() {
        return BakedEntities.sprite(TEXTURE);
    }
}
