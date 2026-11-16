package com.bdmajora.equilibrium.mixin.entity.collisions.movement;

import com.bdmajora.equilibrium.common.world.ChunkSectionCursor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.border.WorldBorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import javax.annotation.Nullable;
import java.util.List;

// Mixin that changes World.
@Mixin(World.class)
public abstract class WorldMixin {
    // Replaces the whole method getCollisionBoxes.
    @Overwrite
    private boolean getCollisionBoxes(@Nullable Entity entityIn, AxisAlignedBB aabb, boolean stopOnFirst,
                                      @Nullable List<AxisAlignedBB> outList) {
        int minX = MathHelper.floor(aabb.minX) - 1;
        int maxX = MathHelper.ceil(aabb.maxX) + 1;
        int minY = MathHelper.floor(aabb.minY) - 1;
        int maxY = MathHelper.ceil(aabb.maxY) + 1;
        int minZ = MathHelper.floor(aabb.minZ) - 1;
        int maxZ = MathHelper.ceil(aabb.maxZ) + 1;

        World world = (World) (Object) this;

        WorldBorder border = world.getWorldBorder();

        boolean wasOutsideBorder = entityIn != null && entityIn.isOutsideBorder();
        boolean isInsideBorder = entityIn != null && world.isInsideWorldBorder(entityIn);

        IBlockState borderFiller = Blocks.STONE.getDefaultState();

        BlockPos.PooledMutableBlockPos pos = BlockPos.PooledMutableBlockPos.retain();

        ChunkSectionCursor cursor = new ChunkSectionCursor(world, false);

        if (stopOnFirst && !net.minecraftforge.event.ForgeEventFactory.gatherCollisionBoxes(world, entityIn, aabb, outList)) {
            pos.release();
            return true;
        }

        try {
            for (int x = minX; x < maxX; ++x) {
                for (int z = minZ; z < maxZ; ++z) {
                    boolean onXEdge = x == minX || x == maxX - 1;
                    boolean onZEdge = z == minZ || z == maxZ - 1;

                    if (onXEdge && onZEdge) {
                        continue;
                    }

                    if (!world.isBlockLoaded(pos.setPos(x, 64, z))) {
                        continue;
                    }

                    for (int y = minY; y < maxY; ++y) {
                        if ((onXEdge || onZEdge) && y == maxY - 1) {
                            continue;
                        }

                        if (stopOnFirst) {
                            if (x < -30000000 || x >= 30000000 || z < -30000000 || z >= 30000000) {
                                return true;
                            }
                        } else if (entityIn != null && wasOutsideBorder == isInsideBorder) {
                            entityIn.setOutsideBorder(!isInsideBorder);
                        }

                        pos.setPos(x, y, z);

                        IBlockState state;

                        if (!stopOnFirst && !border.contains(pos) && isInsideBorder) {
                            state = borderFiller;
                        } else {
                            state = cursor.getBlockState(x, y, z);
                        }

                        state.addCollisionBoxToList(world, pos, aabb, outList, entityIn, false);

                        if (stopOnFirst && !net.minecraftforge.event.ForgeEventFactory.gatherCollisionBoxes(world, entityIn, aabb, outList)) {
                            return true;
                        }
                    }
                }
            }
        } finally {
            pos.release();
        }

        return !outList.isEmpty();
    }
}
