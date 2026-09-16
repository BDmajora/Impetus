package com.bdmajora.equilibrium.mixin.world.entity_cleanup;

import com.bdmajora.equilibrium.common.world.UnloadedEntityRemover;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;

// Exposes the removal half of World.updateEntities so WorldServerMixin can run it on the ticks vanilla skips; the bodies are vanilla's own lines, minus the profiler sections
@Mixin(World.class)
public abstract class WorldMixin implements UnloadedEntityRemover {
    @Shadow
    @Final
    public List<Entity> loadedEntityList;

    @Shadow
    @Final
    protected List<Entity> unloadedEntityList;

    @Shadow
    @Final
    public List<TileEntity> loadedTileEntityList;

    @Shadow
    @Final
    public List<TileEntity> tickableTileEntities;

    @Shadow
    @Final
    private List<TileEntity> tileEntitiesToBeRemoved;

    @Shadow
    protected abstract boolean isChunkLoaded(int x, int z, boolean allowEmpty);

    @Shadow
    public abstract net.minecraft.world.chunk.Chunk getChunk(int chunkX, int chunkZ);

    @Shadow
    public abstract void onEntityRemoved(Entity entityIn);

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
