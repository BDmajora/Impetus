package com.bdmajora.impetus.impl.render.entity;

import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.ClassInheritanceMultiMap;
import net.minecraft.world.chunk.Chunk;
import com.bdmajora.impetus.mixin.core.terrain.ChunkAccessor;
import com.bdmajora.impetus.mixin.core.terrain.ChunkProviderClientAccessor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

// Collector of the entities available for a render pass.
public class EntityGatherer {
    private final List<Entity> entities = new ArrayList<>();
    private final Set<Entity> seenEntities = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Consumer<Entity> addEntity = entity -> {
        if (this.seenEntities.add(entity)) {
            this.entities.add(entity);
        }
    };

    public List<Entity> collect(WorldClient world) {
        this.entities.clear();
        this.seenEntities.clear();
        if (world.getChunkProvider() instanceof ChunkProviderClientAccessor provider) {
            var loadedChunks = provider.impetus$getLoadedChunks();
            for (Chunk chunk : loadedChunks.values()) {
                if (chunk instanceof ChunkAccessor accessor && !accessor.impetus$getHasEntities()) {
                    continue;
                }
                ClassInheritanceMultiMap<Entity>[] entityMaps = chunk.getEntityLists();
                for (ClassInheritanceMultiMap<Entity> map : entityMaps) {
                    map.forEach(this.addEntity);
                }
            }
        }
        world.getLoadedEntityList().forEach(this.addEntity);
        return this.entities;
    }
}
