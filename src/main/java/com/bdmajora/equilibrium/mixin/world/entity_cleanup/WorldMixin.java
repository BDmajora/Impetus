package com.bdmajora.equilibrium.mixin.world.entity_cleanup;

import net.minecraft.world.chunk.Chunk;
import com.bdmajora.equilibrium.common.world.UnloadedEntityRemover;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;

// Mixin that changes World.
@Mixin(World.class)
public abstract class WorldMixin implements UnloadedEntityRemover {
    // Reference to the field loaded entity list of the target.
    @Shadow
    @Final
    public List<Entity> loadedEntityList;

    // Reference to the field unloaded entity list of the target.
    @Shadow
    @Final
    protected List<Entity> unloadedEntityList;

    // Reference to the field loaded tile entity list of the target.
    @Shadow
    @Final
    public List<TileEntity> loadedTileEntityList;

    // Reference to the field tickable tile entities of the target.
    @Shadow
    @Final
    public List<TileEntity> tickableTileEntities;

    // Reference to the field tile entities to be removed of the target.
    @Shadow
    @Final
    private List<TileEntity> tileEntitiesToBeRemoved;

    // Reference to the method is chunk loaded of the target.
    @Shadow
    protected abstract boolean isChunkLoaded(int x, int z, boolean allowEmpty);

    // Reference to the method get chunk of the target.
    @Shadow
    public abstract Chunk getChunk(int chunkX, int chunkZ);

    // Reference to the method on entity removed of the target.
    @Shadow
    public abstract void onEntityRemoved(Entity entityIn);

    // Removes entities queued for unload in one pass.
    @Override
    public void equilibrium$removeUnloaded() {
        if (!this.unloadedEntityList.isEmpty()) {
            this.loadedEntityList.removeAll(this.unloadedEntityList);
            for (Entity entity : this.unloadedEntityList) {
                int chunkX = entity.chunkCoordX;
                int chunkZ = entity.chunkCoordZ;
                if (entity.addedToChunk && this.isChunkLoaded(chunkX, chunkZ, true)) {
                    this.getChunk(chunkX, chunkZ).removeEntity(entity);
                }
            }
            for (Entity entity : this.unloadedEntityList) {
                this.onEntityRemoved(entity);
            }
            this.unloadedEntityList.clear();
        }
        if (!this.tileEntitiesToBeRemoved.isEmpty()) {
            for (TileEntity tile : this.tileEntitiesToBeRemoved) {
                tile.onChunkUnload();
            }
            this.tickableTileEntities.removeAll(this.tileEntitiesToBeRemoved);
            this.loadedTileEntityList.removeAll(this.tileEntitiesToBeRemoved);
            this.tileEntitiesToBeRemoved.clear();
        }
    }
}
