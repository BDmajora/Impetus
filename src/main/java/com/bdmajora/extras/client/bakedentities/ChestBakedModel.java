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

// Baked model of chests.
public final class ChestBakedModel extends BakedEntityModel {
    // Base model of a single chest.
    private final ModelChest single = new ModelChest();
    // Base model of a double chest.
    private final ModelChest large = new ModelLargeChest();

    // Position of a chest in a pair.
    enum Role {
        SINGLE(null, false), PRIMARY_X(EnumFacing.Axis.X, false), SECONDARY_X(EnumFacing.Axis.X, true),
        PRIMARY_Z(EnumFacing.Axis.Z, false), SECONDARY_Z(EnumFacing.Axis.Z, true);

        // Axis of the pair or null.
        final EnumFacing.Axis axis;
        // Whether the chest is the second half.
        final boolean secondary;

        // Creates a role.
        Role(EnumFacing.Axis axis, boolean secondary) {
            this.axis = axis;
            this.secondary = secondary;
        }
    }

    // Returns the quads of the chest state.
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

    // Returns whether the position holds the chest block.
    private static boolean isChest(IBlockAccess access, BlockPos pos, BlockChest block) {
        return access.getBlockState(pos).getBlock() == block;
    }

    // Returns the position of the other half.
    private static BlockPos partner(BlockPos pos, Role role) {
        return switch (role) {
            case PRIMARY_X -> pos.east();
            case SECONDARY_X -> pos.west();
            case PRIMARY_Z -> pos.south();
            default -> pos.north();
        };
    }

    // Returns the quads of one chest variant.
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

    // Returns the sprite of the particles.
    @Override
    protected TextureAtlasSprite particleSprite() {
        return BakedEntities.sprite(BakedEntities.ChestTexture.NORMAL.location);
    }
}
