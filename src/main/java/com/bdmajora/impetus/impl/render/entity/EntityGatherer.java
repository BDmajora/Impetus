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

// Collector of the entities to draw this frame.
public class EntityGatherer {
    // Number of entity passes.
    public static final int NUM_PASSES = 2;

    // Entities of each pass.
    private final List<Entity>[] entityLists;
    // Entities already added this frame by identity.
    private final Set<Entity> seenEntities = Collections.newSetFromMap(new IdentityHashMap<>());
    // Adder of an entity to its pass.
    private final Consumer<Entity> addEntity;

    // Creates empty pass lists.
    @SuppressWarnings("unchecked")
    public EntityGatherer() {
        this.entityLists = new List[NUM_PASSES];
        for (int i = 0; i < NUM_PASSES; i++) {
            this.entityLists[i] = new ArrayList<>();
        }
        var entityLists = this.entityLists;
        this.addEntity = entity -> {
            if (!this.seenEntities.add(entity)) {
                return;
            }
            for (int i = 0; i < NUM_PASSES; i++) {
                if (entity.shouldRenderInPass(i)) {
                    entityLists[i].add(entity);
                }
            }
        };
    }

    // Empties the collected entities.
    public void clear() {
        for (int i = 0; i < NUM_PASSES; i++) {
            entityLists[i].clear();
        }
        this.seenEntities.clear();
    }

    // Returns the entities of each pass.
    public List<Entity>[] getLoadedEntityList(WorldClient world) {
        Consumer<Entity> addEntity = this.addEntity;
        if (world.getChunkProvider() instanceof ChunkProviderClientAccessor provider) {
            var loadedChunks = provider.impetus$getLoadedChunks();
            for (Chunk chunk : loadedChunks.values()) {
                if (!((ChunkAccessor)chunk).impetus$getHasEntities()) {
                    continue;
                }
                ClassInheritanceMultiMap<Entity>[] entityMaps = chunk.getEntityLists();
                for (ClassInheritanceMultiMap<Entity> map : entityMaps) {
                    map.forEach(addEntity);
                }
            }
        }
        world.loadedEntityList.forEach(addEntity);
        return this.entityLists;
    }
}
