package com.bdmajora.equilibrium.mixin.util.block_entity_retrieval;

import com.bdmajora.equilibrium.common.world.ChunkAccess;
import com.bdmajora.equilibrium.common.world.TileEntityAccess;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;

import javax.annotation.Nullable;

// Mixin that changes World.
@Mixin(World.class)
public abstract class WorldMixin implements TileEntityAccess {
    // Returns the existing tile entity.
    @Nullable
    @Override
    public TileEntity equilibrium$getExistingTileEntity(BlockPos pos) {
        World world = (World) (Object) this;

        if (world.isOutsideBuildHeight(pos)) {
            return null;
        }

        Chunk chunk = this instanceof ChunkAccess
                ? ((ChunkAccess) this).equilibrium$getLoadedChunk(pos.getX() >> 4, pos.getZ() >> 4)
                : world.getChunk(pos);

        if (chunk == null) {
            return null;
        }

        TileEntity tileEntity = chunk.getTileEntity(pos, Chunk.EnumCreateEntityType.CHECK);

        return tileEntity != null && !tileEntity.isInvalid() ? tileEntity : null;
    }
}
