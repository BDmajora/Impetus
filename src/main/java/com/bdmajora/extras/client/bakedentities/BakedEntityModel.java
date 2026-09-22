package com.bdmajora.extras.client.bakedentities;

import java.util.function.Function;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
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

// A block entity's static geometry served as a block model: every quad is returned unassigned so no neighbour ever culls it, variants are baked on first request per key and dropped when the atlas restitches
public abstract class BakedEntityModel implements IBakedModel {
    private final Map<Object, List<BakedQuad>> variants = new ConcurrentHashMap<>();

    // The variant for this state and, when the mesher pinned one, this world position; null or empty hides the block from the mesh
    protected abstract List<BakedQuad> quadsFor(IBlockState state, BakedEntityContext context);

    // Sprite used for breaking particles and the missing-side fallback
    protected abstract TextureAtlasSprite particleSprite();

    protected List<BakedQuad> variant(Object key, Function<Object, List<BakedQuad>> baker) {
        return this.variants.computeIfAbsent(key, baker);
    }

    // Called on restitch: sprite coordinates baked into the quads are no longer valid
    public void invalidate() {
        this.variants.clear();
    }

    @Override
    public List<BakedQuad> getQuads(IBlockState state, EnumFacing side, long rand) {
        if (side != null || state == null) {
            return Collections.emptyList();
        }
        List<BakedQuad> quads = quadsFor(state, BakedEntityContext.get());
        return quads == null ? Collections.emptyList() : quads;
    }

    @Override
    public boolean isAmbientOcclusion() {
        return true;
    }

    @Override
    public boolean isGui3d() {
        return false;
    }

    @Override
    public boolean isBuiltInRenderer() {
        return false;
    }

    @Override
    public TextureAtlasSprite getParticleTexture() {
        return particleSprite();
    }

    @Override
    public ItemCameraTransforms getItemCameraTransforms() {
        return ItemCameraTransforms.DEFAULT;
    }

    @Override
    public ItemOverrideList getOverrides() {
        return ItemOverrideList.NONE;
    }

    // Whether the block entity at a position, if it is one of the animating kinds, is at rest; a missing entity counts as at rest so a freshly placed block draws immediately
    protected static boolean settled(IBlockAccess access, BlockPos pos) {
        TileEntity tile = access.getTileEntity(pos);
        return !(tile instanceof AnimatedBlockEntity) || ((AnimatedBlockEntity) tile).impetus$isSettled();
    }

    // The renderer's opening moves, in block units, shared by the chest family: origin to the block's far top corner, flip y and z, spin about the centre, back
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

    // Degrees the chest renderer spins the model for each facing
    protected static int chestRotation(EnumFacing facing) {
        return switch (facing) {
            case NORTH -> 180;
            case WEST -> 90;
            case EAST -> -90;
            default -> 0;
        };
    }
}
