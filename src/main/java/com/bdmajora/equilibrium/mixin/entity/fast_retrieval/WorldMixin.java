package com.bdmajora.equilibrium.mixin.entity.fast_retrieval;

import com.bdmajora.equilibrium.common.world.ChunkAccess;
import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import javax.annotation.Nullable;
import java.util.List;

// Mixin that changes World.
@Mixin(World.class)
public abstract class WorldMixin implements ChunkAccess {
    // Replaces the whole method getEntitiesInAABBexcluding.
    @Overwrite
    public List<Entity> getEntitiesInAABBexcluding(@Nullable Entity entityIn, AxisAlignedBB boundingBox,
                                                   @Nullable Predicate<? super Entity> predicate) {
        List<Entity> entities = Lists.newArrayList();

        int minChunkX = MathHelper.floor((boundingBox.minX - World.MAX_ENTITY_RADIUS) / 16.0D);
        int maxChunkX = MathHelper.floor((boundingBox.maxX + World.MAX_ENTITY_RADIUS) / 16.0D);
        int minChunkZ = MathHelper.floor((boundingBox.minZ - World.MAX_ENTITY_RADIUS) / 16.0D);
        int maxChunkZ = MathHelper.floor((boundingBox.maxZ + World.MAX_ENTITY_RADIUS) / 16.0D);

        for (int chunkX = minChunkX; chunkX <= maxChunkX; ++chunkX) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; ++chunkZ) {
                Chunk chunk = this.equilibrium$getLoadedChunk(chunkX, chunkZ);

                if (chunk != null) {
                    chunk.getEntitiesWithinAABBForEntity(entityIn, boundingBox, entities, predicate);
                }
            }
        }

        return entities;
    }
}
