package com.bdmajora.extras.client.bakedentities;

import net.minecraft.block.BlockChest;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.model.ModelChest;
import net.minecraft.client.model.ModelLargeChest;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.joml.Matrix4f;

import java.util.Collections;
import java.util.List;

// Chests and trapped chests, single and double: a double is baked once as the large model in the lower half's frame and split at the shared plane, so each block carries only what lies inside it and section culling stays exact
public final class ChestBakedModel extends BakedEntityModel {
    private final ModelChest single = new ModelChest();
    private final ModelChest large = new ModelLargeChest();

    // Which block of a pair this is, if any, and along which axis the pair runs
    enum Role {
        SINGLE(null, false), PRIMARY_X(EnumFacing.Axis.X, false), SECONDARY_X(EnumFacing.Axis.X, true),
        PRIMARY_Z(EnumFacing.Axis.Z, false), SECONDARY_Z(EnumFacing.Axis.Z, true);

        final EnumFacing.Axis axis;
        final boolean secondary;

        Role(EnumFacing.Axis axis, boolean secondary) {
            this.axis = axis;
            this.secondary = secondary;
        }
    }

    @Override
    protected List<BakedQuad> quadsFor(IBlockState state, BakedEntityContext context) {
        if (!(state.getBlock() instanceof BlockChest)) {
            return null;
        }
        BlockChest block = (BlockChest) state.getBlock();
        EnumFacing facing = state.getValue(BlockChest.FACING);
        Role role = Role.SINGLE;
        if (context != null) {
            IBlockAccess access = context.access;
            BlockPos pos = context.pos;
            // Same adjacency rule as TileEntityChest.checkForAdjacentChests: a chest of the same type on a side makes a pair, and the lower coordinate is the one the renderer draws from
            if (isChest(access, pos.west(), block)) {
                role = Role.SECONDARY_X;
            } else if (isChest(access, pos.east(), block)) {
                role = Role.PRIMARY_X;
            } else if (isChest(access, pos.north(), block)) {
                role = Role.SECONDARY_Z;
            } else if (isChest(access, pos.south(), block)) {
                role = Role.PRIMARY_Z;
            }
            if (!settled(access, pos) || (role != Role.SINGLE && !settled(access, partner(pos, role)))) {
                return Collections.emptyList();
            }
        }
        Role finalRole = role;
        BakedEntities.ChestTexture texture = BakedEntities.chestTexture(block.chestType, role != Role.SINGLE);
        int key = facing.getHorizontalIndex() | (role.ordinal() << 2) | (texture.ordinal() << 5);
        return variant(key, k -> bake(facing, finalRole, texture));
    }

    private static boolean isChest(IBlockAccess access, BlockPos pos, BlockChest block) {
        return access.getBlockState(pos).getBlock() == block;
    }

    private static BlockPos partner(BlockPos pos, Role role) {
        switch (role) {
            case PRIMARY_X:
                return pos.east();
            case SECONDARY_X:
                return pos.west();
            case PRIMARY_Z:
                return pos.south();
            default:
                return pos.north();
        }
    }

    // Replays TileEntityChestRenderer's matrix for the primary of the pair, including its one-block nudges for the two facings whose rotation would otherwise put the model on the wrong side
    private List<BakedQuad> bake(EnumFacing facing, Role role, BakedEntities.ChestTexture texture) {
        TextureAtlasSprite sprite = BakedEntities.sprite(texture.location);
        float nudgeX = facing == EnumFacing.NORTH && role.axis == EnumFacing.Axis.X ? 1.0F : 0.0F;
        float nudgeZ = facing == EnumFacing.EAST && role.axis == EnumFacing.Axis.Z ? -1.0F : 0.0F;
        Matrix4f matrix = chestMatrix(facing, nudgeX, nudgeZ);

        ModelChest model = role == Role.SINGLE ? this.single : this.large;
        synchronized (model) {
            model.chestLid.rotateAngleX = 0.0F;
            model.chestKnob.rotateAngleX = 0.0F;
            EntityModelBaker baker = new EntityModelBaker(sprite);
            baker.bake(model.chestLid, matrix);
            baker.bake(model.chestKnob, matrix);
            baker.bake(model.chestBelow, matrix);
            if (role == Role.SINGLE) {
                return baker.quads();
            }
            return EntityModelBaker.clip(baker.quads(), role.axis, 1.0F, role.secondary, role.secondary ? 1.0F : 0.0F);
        }
    }

    @Override
    protected TextureAtlasSprite particleSprite() {
        return BakedEntities.sprite(BakedEntities.ChestTexture.NORMAL.location);
    }
}
