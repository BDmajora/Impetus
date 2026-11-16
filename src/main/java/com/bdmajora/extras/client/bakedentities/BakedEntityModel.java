package com.bdmajora.extras.client.bakedentities;

import java.util.function.Function;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.joml.Matrix4f;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Baked model of a block entity.
public abstract class BakedEntityModel implements IBakedModel {
    // Baked quads of each variant.
    private final Map<Object, List<BakedQuad>> variants = new ConcurrentHashMap<>();

    // Returns the quads of the state.
    protected abstract List<BakedQuad> quadsFor(IBlockState state, BakedEntityContext context);

    // Returns the sprite of the particles.
    protected abstract TextureAtlasSprite particleSprite();

    // Returns the quads of the variant and bakes them when needed.
    protected List<BakedQuad> variant(Object key, Function<Object, List<BakedQuad>> baker) {
        return this.variants.computeIfAbsent(key, baker);
    }

    // Discards every baked variant.
    public void invalidate() {
        this.variants.clear();
    }

    // Returns the quads for the general side only.
    @Override
    public List<BakedQuad> getQuads(IBlockState state, EnumFacing side, long rand) {
        if (side != null || state == null) {
            return Collections.emptyList();
        }
        List<BakedQuad> quads = quadsFor(state, BakedEntityContext.get());
        return quads == null ? Collections.emptyList() : quads;
    }

    // Returns true.
    @Override
    public boolean isAmbientOcclusion() {
        return true;
    }

    // Returns false.
    @Override
    public boolean isGui3d() {
        return false;
    }

    // Returns false.
    @Override
    public boolean isBuiltInRenderer() {
        return false;
    }

    // Returns the sprite of the particles.
    @Override
    public TextureAtlasSprite getParticleTexture() {
        return particleSprite();
    }

    // Returns no overrides.
    @Override
    public ItemOverrideList getOverrides() {
        return ItemOverrideList.NONE;
    }

    // Returns whether the block entity is at rest.
    protected static boolean settled(IBlockAccess access, BlockPos pos) {
        TileEntity tile = access.getTileEntity(pos);
        return !(tile instanceof AnimatedBlockEntity) || ((AnimatedBlockEntity) tile).impetus$isSettled();
    }

    // Returns the transform of a chest facing.
    protected static Matrix4f chestMatrix(EnumFacing facing, float preRotateX, float preRotateZ) {
        Matrix4f matrix = new Matrix4f();
        matrix.translate(0.0F, 1.0F, 1.0F);
        matrix.scale(1.0F, -1.0F, -1.0F);
        matrix.translate(0.5F, 0.5F, 0.5F);
        matrix.translate(preRotateX, 0.0F, preRotateZ);
        matrix.rotateY((float) Math.toRadians(chestRotation(facing)));
        matrix.translate(-0.5F, -0.5F, -0.5F);
        return matrix;
    }

    // Returns the rotation of a chest facing in degrees.
    protected static int chestRotation(EnumFacing facing) {
        return switch (facing) {
            case NORTH -> 180;
            case WEST -> 90;
            case EAST -> -90;
            default -> 0;
        };
    }
}
