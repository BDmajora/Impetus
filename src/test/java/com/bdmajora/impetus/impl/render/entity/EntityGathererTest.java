package com.bdmajora.impetus.impl.render.entity;

import com.bdmajora.impetus.mixin.core.terrain.ChunkAccessor;
import com.bdmajora.impetus.mixin.core.terrain.ChunkProviderClientAccessor;
import com.bdmajora.testing.Mc;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.ClassInheritanceMultiMap;
import net.minecraft.world.chunk.Chunk;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EntityGathererTest {
    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
    }

    @Test
    void chunkEntitiesComeFirstAndOnesAlsoInTheWorldListAreNotRepeated() {
        Entity inChunk = mock(Entity.class);
        Entity both = mock(Entity.class);
        Entity worldOnly = mock(Entity.class);
        WorldClient world = world(List.of(chunk(true, inChunk, both)), new ArrayList<>(List.of(both, worldOnly, both)));
        assertEquals(List.of(inChunk, both, worldOnly), new EntityGatherer().collect(world));
    }

    @Test
    void aChunkFlaggedEmptyIsSkippedWithoutReadingItsLists() {
        Entity stale = mock(Entity.class);
        Chunk empty = chunk(false, stale);
        WorldClient world = world(List.of(empty), new ArrayList<>());
        assertEquals(List.of(), new EntityGatherer().collect(world));
        verify(empty, never()).getEntityLists();
    }

    @Test
    void aChunkWithoutTheAccessorIsStillRead() {
        // A chunk subclass from another mod that the accessor mixin never reached
        Entity entity = mock(Entity.class);
        Chunk plain = mock(Chunk.class);
        when(plain.getEntityLists()).thenReturn(lists(entity));
        WorldClient world = world(List.of(plain), new ArrayList<>());
        assertEquals(List.of(entity), new EntityGatherer().collect(world));
    }

    @Test
    void aProviderWithoutTheAccessorFallsBackToTheWorldList() {
        Entity entity = mock(Entity.class);
        WorldClient world = mock(WorldClient.class);
        when(world.getChunkProvider()).thenReturn(mock(ChunkProviderClient.class));
        when(world.getLoadedEntityList()).thenReturn(new ArrayList<>(List.of(entity, entity)));
        assertEquals(List.of(entity), new EntityGatherer().collect(world));
        // No provider at all (world torn down mid-frame) behaves the same
        when(world.getChunkProvider()).thenReturn(null);
        assertEquals(List.of(entity), new EntityGatherer().collect(world));
    }

    @Test
    void eachCollectStartsOverAndReusesTheSameList() {
        Entity first = mock(Entity.class);
        Entity second = mock(Entity.class);
        List<Entity> loaded = new ArrayList<>(List.of(first));
        WorldClient world = world(List.of(), loaded);
        EntityGatherer gatherer = new EntityGatherer();
        List<Entity> before = gatherer.collect(world);
        assertEquals(List.of(first), before);
        // An entity seen last frame is collected again this frame: the seen set is per collect, not per gatherer
        loaded.add(second);
        List<Entity> after = gatherer.collect(world);
        assertSame(before, after);
        assertEquals(List.of(first, second), after);
        loaded.clear();
        assertTrue(gatherer.collect(world).isEmpty());
    }

    @Test
    void anEntityListedInTwoChunksWhileCrossingTheBorderIsDrawnOnce() {
        Entity crossing = mock(Entity.class);
        Entity other = mock(Entity.class);
        WorldClient world = world(List.of(chunk(true, crossing), chunk(true, other, crossing)), new ArrayList<>(List.of(crossing)));
        List<Entity> collected = new EntityGatherer().collect(world);
        assertEquals(2, collected.size());
        assertEquals(1, collected.stream().filter(e -> e == crossing).count());
    }

    @Test
    void anEntityInSeveralSectionsOfOneChunkIsDrawnOnce() {
        Entity tall = mock(Entity.class);
        Chunk chunk = Mc.mock(Chunk.class, ChunkAccessor.class);
        when(((ChunkAccessor) chunk).impetus$getHasEntities()).thenReturn(true);
        ClassInheritanceMultiMap<Entity>[] sections = lists(tall);
        sections[1].add(tall);
        when(chunk.getEntityLists()).thenReturn(sections);
        assertEquals(List.of(tall), new EntityGatherer().collect(world(List.of(chunk), new ArrayList<>())));
    }

    @Test
    void manyDuplicatesKeepFirstSeenOrderAndExactCount() {
        List<Entity> unique = new ArrayList<>();
        for (int i = 0; i < 2_000; i++) {
            unique.add(mock(Entity.class));
        }
        List<Entity> loaded = new ArrayList<>();
        for (int round = 0; round < 5; round++) {
            loaded.addAll(unique);
        }
        // The first 100 also sit in a chunk, so they come first
        Chunk chunk = chunk(true, unique.subList(0, 100).toArray(new Entity[0]));
        List<Entity> collected = new EntityGatherer().collect(world(List.of(chunk), loaded));
        assertEquals(unique, collected);
    }

    @Test
    void switchingWorldsCarriesNothingOver() {
        Entity overworld = mock(Entity.class);
        Entity nether = mock(Entity.class);
        EntityGatherer gatherer = new EntityGatherer();
        assertEquals(List.of(overworld), gatherer.collect(world(List.of(), new ArrayList<>(List.of(overworld)))));
        assertEquals(List.of(nether), gatherer.collect(world(List.of(), new ArrayList<>(List.of(nether)))));
    }

    @Test
    void theWorldListStillSuppliesEntitiesOfASkippedChunk() {
        // hasEntities can lag a spawn by a tick; the world list is authoritative, so the entity is not lost
        Entity justSpawned = mock(Entity.class);
        WorldClient world = world(List.of(chunk(false, justSpawned)), new ArrayList<>(List.of(justSpawned)));
        assertEquals(List.of(justSpawned), new EntityGatherer().collect(world));
    }

    @Test
    void deadAndInvisibleEntitiesAreCollectedForTheRendererToJudge() {
        // Gathering does no filtering: renderEntities applies the pass, frustum and visibility checks
        Entity dead = mock(Entity.class);
        dead.isDead = true;
        Entity invisible = mock(Entity.class);
        when(invisible.isInvisible()).thenReturn(true);
        WorldClient world = world(List.of(), new ArrayList<>(List.of(dead, invisible)));
        assertEquals(List.of(dead, invisible), new EntityGatherer().collect(world));
    }

    @Test
    void anEmptyWorldGivesAnEmptyList() {
        assertTrue(new EntityGatherer().collect(world(List.of(), new ArrayList<>())).isEmpty());
        assertTrue(new EntityGatherer().collect(world(List.of(chunk(true), chunk(false)), new ArrayList<>())).isEmpty());
    }

    private static WorldClient world(List<Chunk> chunks, List<Entity> loaded) {
        Long2ObjectOpenHashMap<Chunk> map = new Long2ObjectOpenHashMap<>();
        for (int i = 0; i < chunks.size(); i++) {
            map.put(i, chunks.get(i));
        }
        ChunkProviderClient provider = Mc.mock(ChunkProviderClient.class, ChunkProviderClientAccessor.class);
        when(((ChunkProviderClientAccessor) provider).impetus$getLoadedChunks()).thenReturn(map);
        WorldClient world = mock(WorldClient.class);
        when(world.getChunkProvider()).thenReturn(provider);
        when(world.getLoadedEntityList()).thenReturn(loaded);
        return world;
    }

    private static Chunk chunk(boolean hasEntities, Entity... entities) {
        Chunk chunk = Mc.mock(Chunk.class, ChunkAccessor.class);
        when(((ChunkAccessor) chunk).impetus$getHasEntities()).thenReturn(hasEntities);
        when(chunk.getEntityLists()).thenReturn(lists(entities));
        return chunk;
    }

    @SuppressWarnings("unchecked")
    private static ClassInheritanceMultiMap<Entity>[] lists(Entity... entities) {
        ClassInheritanceMultiMap<Entity> section = new ClassInheritanceMultiMap<>(Entity.class);
        for (Entity entity : entities) {
            section.add(entity);
        }
        return new ClassInheritanceMultiMap[] {section, new ClassInheritanceMultiMap<>(Entity.class)};
    }
}
