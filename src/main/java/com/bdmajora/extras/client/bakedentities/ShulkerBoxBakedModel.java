package com.bdmajora.extras.client.bakedentities;

import net.minecraft.block.BlockShulkerBox;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.model.ModelShulker;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import org.joml.Matrix4f;

import java.util.Collections;
import java.util.List;

// Baked model of shulker boxes.
public final class ShulkerBoxBakedModel extends BakedEntityModel {
    // Base model of shulkers.
    private final ModelShulker model = new ModelShulker();

    // Returns the texture of the color.
    static ResourceLocation texture(EnumDyeColor color) {
        return new ResourceLocation("minecraft", "entity/shulker/shulker_" + color.getName());
    }

    // Returns the quads of the shulker box state.
    @Override
    protected List<BakedQuad> quadsFor(IBlockState state, BakedEntityContext context) {
        if (!(state.getBlock() instanceof BlockShulkerBox)) {
            return null;
        }
        if (context != null && !settled(context.access, context.pos)) {
            return Collections.emptyList();
        }
        EnumFacing facing = state.getValue(BlockShulkerBox.FACING);
        EnumDyeColor color = colorOf((BlockShulkerBox) state.getBlock());
        int key = facing.getIndex() | (color.getMetadata() << 3);
        return variant(key, k -> bake(facing, color));
    }

    // Returns the color of the block.
    static EnumDyeColor colorOf(BlockShulkerBox block) {
        for (EnumDyeColor color : EnumDyeColor.values()) {
            if (BlockShulkerBox.getBlockByColor(color) == block) {
                return color;
            }
        }
        return EnumDyeColor.PURPLE;
    }

    // Returns the quads of one variant.
    private List<BakedQuad> bake(EnumFacing facing, EnumDyeColor color) {
        Matrix4f matrix = new Matrix4f();
        matrix.translate(0.5F, 1.5F, 0.5F);
        matrix.scale(1.0F, -1.0F, -1.0F);
        switch (facing) {
            case DOWN -> {
                matrix.translate(0.0F, 2.0F, 0.0F);
                matrix.rotateX((float) Math.toRadians(180.0F));
            }
            case NORTH -> {
                matrix.translate(0.0F, 1.0F, 1.0F);
                matrix.rotateX((float) Math.toRadians(90.0F));
                matrix.rotateZ((float) Math.toRadians(180.0F));
            }
            case SOUTH -> {
                matrix.translate(0.0F, 1.0F, -1.0F);
                matrix.rotateX((float) Math.toRadians(90.0F));
            }
            case WEST -> {
                matrix.translate(-1.0F, 1.0F, 0.0F);
                matrix.rotateX((float) Math.toRadians(90.0F));
                matrix.rotateZ((float) Math.toRadians(-90.0F));
            }
            case EAST -> {
                matrix.translate(1.0F, 1.0F, 0.0F);
                matrix.rotateX((float) Math.toRadians(90.0F));
                matrix.rotateZ((float) Math.toRadians(90.0F));
            }
            default -> { }
        }
        synchronized (this.model) {
            EntityModelBaker baker = new EntityModelBaker(BakedEntities.sprite(texture(color)));
            baker.bake(this.model.base, matrix);
            baker.bake(this.model.lid, matrix);
            return baker.quads();
        }
    }

    // Returns the sprite of the particles.
    @Override
    protected TextureAtlasSprite particleSprite() {
        return BakedEntities.sprite(texture(EnumDyeColor.PURPLE));
    }
}
