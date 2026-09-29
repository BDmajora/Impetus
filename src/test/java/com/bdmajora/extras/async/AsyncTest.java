package com.bdmajora.extras.async;

import com.bdmajora.extras.ExtrasConfig;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.server.management.PlayerChunkMap;
import net.minecraft.server.management.PlayerChunkMapEntry;
import net.minecraft.util.ReportedException;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.ForgeModContainer;
import net.minecraftforge.fml.relauncher.FMLLaunchHandler;
import net.minecraftforge.fml.relauncher.Side;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AsyncTest {
    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
        Statics.set(FMLLaunchHandler.class, "side", Side.CLIENT);
    }

    @AfterEach
    void stopThePool() {
        ParallelProcessor.shutdown();
        ParallelProcessor.onServerStart();
        ForgeModContainer.removeErroringEntities = false;
        ParallelProcessor.moddedEntities = false;
    }

    @Test
    void theEntityListReadsOptimisticallyAndIteratesASnapshot() {
        Entity a = mock(Entity.class);
        Entity b = mock(Entity.class);
        Entity c = mock(Entity.class);
        ConcurrentEntityList list = new ConcurrentEntityList(List.of(a));
        assertTrue(list.add(b));
        list.add(0, c);
        assertEquals(List.of(c, a, b), list.copy());
        assertSame(c, list.get(0));
        assertEquals(3, list.size());
        assertThrows(IndexOutOfBoundsException.class, () -> list.get(5));
        assertSame(c, list.set(0, a));
        assertSame(a, list.remove(0));
        assertTrue(list.remove(b));
        assertTrue(list.addAll(List.of(b, c)));
        assertTrue(list.addAll(0, List.of(c)));
        assertTrue(list.contains(b));
        assertEquals(1, list.indexOf(a));
        assertEquals(4, list.toArray().length);
        assertEquals(4, list.toArray(new Entity[0]).length);

        List<Entity> visited = new ArrayList<>();
        list.forEach(visited::add);
        assertEquals(list.copy(), visited);

        // Removing through the iterator reaches the live list by identity, while the walk carries on over its copy
        Iterator<Entity> iterator = list.iterator();
        assertThrows(IllegalStateException.class, iterator::remove);
        int walked = 0;
        while (iterator.hasNext()) {
            if (iterator.next() == a) {
                iterator.remove();
            }
            walked++;
        }
        assertEquals(4, walked);
        assertFalse(list.contains(a));
        assertThrows(NoSuchElementException.class, iterator::next);
        // A stale iterator removing something already gone does nothing
        Iterator<Entity> stale = list.iterator();
        stale.next();
        list.clear();
        stale.remove();

        list.addAll(List.of(a, b, c));
        assertTrue(list.removeAll(List.of(a)));
        assertTrue(list.retainAll(List.of(b)));
        assertTrue(list.removeIf(entity -> entity == b));
        assertTrue(list.isEmpty());
        assertEquals(0, list.snapshot().length);

        // Equal to any list with the same contents, hashed by identity since the contents move
        list.add(a);
        assertEquals(list, List.of(a));
        assertEquals(list, list);
        assertNotEquals(list, "not a list");
        assertEquals(System.identityHashCode(list), list.hashCode());
    }

    @Test
    void theChunkMapBoxesKeysButNeverCorruptsUnderConcurrentUse() {
        Long2ObjectOpenHashMap<String> source = new Long2ObjectOpenHashMap<>();
        source.put(1L, "one");
        ConcurrentLong2ObjectMap<String> map = new ConcurrentLong2ObjectMap<>(source);
        map.defaultReturnValue("none");
        assertEquals("one", map.get(1L));
        assertEquals("none", map.get(2L));
        assertTrue(map.containsKey(1L));
        assertEquals("none", map.put(2L, "two"));
        assertEquals("two", map.put(2L, "deux"));
        assertTrue(map.containsValue("deux"));
        assertEquals(2, map.size());
        assertFalse(map.isEmpty());
        assertEquals("deux", map.remove(2L));
        assertEquals("none", map.remove(2L));

        map.put(3L, "three");
        map.put(4L, "four");
        it.unimi.dsi.fastutil.objects.ObjectSet<Long2ObjectMap.Entry<String>> entries = map.long2ObjectEntrySet();
        assertEquals(3, entries.size());
        assertTrue(entries.contains(new AbstractMap.SimpleEntry<>(1L, "one")));
        assertFalse(entries.contains(new AbstractMap.SimpleEntry<>(1L, "uno")));
        assertFalse(entries.contains(new AbstractMap.SimpleEntry<>("1", "one")));
        assertFalse(entries.contains("not an entry"));
        assertTrue(entries.remove(new AbstractMap.SimpleEntry<>(4L, "four")));
        assertFalse(entries.remove(new AbstractMap.SimpleEntry<>("4", "four")));
        assertFalse(entries.remove("not an entry"));

        // Entries write through, and the iterator can skip and remove
        for (Long2ObjectMap.Entry<String> entry : entries) {
            if (entry.getLongKey() == 1L) {
                assertEquals("one", entry.setValue("ein"));
            }
        }
        assertEquals("ein", map.get(1L));
        ObjectIterator<Long2ObjectMap.Entry<String>> iterator = entries.iterator();
        assertEquals(1, iterator.skip(1));
        iterator.next();
        iterator.remove();
        assertEquals(0, iterator.skip(5));
        assertEquals(1, map.size());
        map.clear();
        assertTrue(map.isEmpty());
        assertTrue(new ConcurrentLong2ObjectMap<String>(16).isEmpty());
    }

    @Test
    void theCostModelCutsBatchesToAboutAQuarterMillisecond() {
        CostModel model = new CostModel(10_000D);
        // 25 items of 10 us is one target task, so anything under two tasks' worth runs in place
        assertTrue(model.shouldRunSequentially(40));
        assertFalse(model.shouldRunSequentially(60));
        assertEquals(25, model.chunkSize(10_000, 4));
        // Never more than an even share, so every worker gets something
        assertEquals(2, model.chunkSize(10, 4));

        AtomicInteger ran = new AtomicInteger();
        model.wrap(1000, ran::incrementAndGet).run();
        assertEquals(1, ran.get());
        // A batch that took next to nothing per item pulls the estimate down
        model.chunkSize(10_000, 4);
        assertTrue(model.chunkSize(10_000, 4) > 25);
    }

    @Test
    void theUsersListKeepsIdsAndWholeModsOnTheMainThread() {
        SyncEntityRules.load(new String[] {"  ", "pig", "examplemod:*", "minecraft:cow", "odd:thing"});
        assertTrue(SyncEntityRules.matches(new ResourceLocation("minecraft:pig")));
        assertTrue(SyncEntityRules.matches(new ResourceLocation("minecraft:cow")));
        assertTrue(SyncEntityRules.matches(new ResourceLocation("examplemod:anything")));
        assertTrue(SyncEntityRules.matches(new ResourceLocation("odd:thing")));
        assertFalse(SyncEntityRules.matches(new ResourceLocation("minecraft:sheep")));
        SyncEntityRules.load(new String[0]);
        assertNotNull(Mixins.construct(SyncEntityRules.class));
    }

    private static WorldServer world(boolean parallel) {
        WorldServer world = Mc.mock(WorldServer.class, ParallelWorld.class);
        when(((ParallelWorld) world).impetus$isParallel()).thenReturn(parallel);
        return world;
    }

    private static EntityPig pig(World world, int chunkX, int chunkZ) {
        EntityPig pig = Mc.mock(EntityPig.class, PortalAccessor.class);
        Mixins.set(pig, "world", world);
        Mixins.set(pig, "chunkCoordX", chunkX);
        Mixins.set(pig, "chunkCoordZ", chunkZ);
        return pig;
    }

    @Test
    void theSwitchesOnlyApplyToParallelServerWorlds() {
        ExtrasConfig.AsyncSettings settings = new ExtrasConfig.AsyncSettings();
        settings.enabled = true;
        settings.entities = true;
        settings.randomTicks = true;
        settings.spawning = true;
        ParallelProcessor.install(settings);
        ParallelProcessor.apply(settings);
        WorldServer world = world(true);
        assertTrue(ParallelProcessor.isEntityTickingActive(world));
        assertTrue(ParallelProcessor.isRandomTickingActive(world));
        assertTrue(ParallelProcessor.isSpawningActive(world));
        assertFalse(ParallelProcessor.isEntityTickingActive(world(false)));

        // Once the server is stopping nothing batches
        ParallelProcessor.shutdown();
        assertFalse(ParallelProcessor.isEntityTickingActive(world));
        ParallelProcessor.onServerStart();
        settings.enabled = false;
        ParallelProcessor.install(settings);
        ParallelProcessor.apply(settings);
        assertFalse(ParallelProcessor.isSpawningActive(world));
        assertFalse(ParallelProcessor.isRandomTickingActive(world));
    }

    @Test
    void theThreadCountFollowsTheSettingOrTheCores() {
        ExtrasConfig.AsyncSettings settings = new ExtrasConfig.AsyncSettings();
        settings.threads = 1;
        ParallelProcessor.apply(settings);
        assertEquals(1, ParallelProcessor.desiredThreads());
        assertTrue(ParallelProcessor.maxThreads() >= 1);
        assertEquals(0, ParallelProcessor.poolSize());

        // A running pool is resized in place, growing and shrinking
        ParallelProcessor.forEachParallel(new ArrayList<>(Collections.nCopies(400, 1)), new CostModel(1_000_000D), item -> { });
        assertEquals(1, ParallelProcessor.poolSize());
        settings.threads = 2;
        ParallelProcessor.apply(settings);
        assertEquals(Math.min(2, Runtime.getRuntime().availableProcessors()), ParallelProcessor.poolSize());
        settings.threads = 1;
        ParallelProcessor.apply(settings);
        assertEquals(1, ParallelProcessor.poolSize());
        ParallelProcessor.apply(settings);

        // Auto leaves headroom, and on a client one more thread for rendering
        settings.threads = 0;
        ParallelProcessor.apply(settings);
        assertTrue(ParallelProcessor.desiredThreads() >= 1);
        String os = System.getProperty("os.name");
        System.setProperty("os.name", "Windows 11");
        try {
            assertTrue(ParallelProcessor.desiredThreads() >= 1);
        } finally {
            System.setProperty("os.name", os);
        }
        Statics.set(FMLLaunchHandler.class, "side", Side.SERVER);
        assertTrue(ParallelProcessor.desiredThreads() >= 1);
        Statics.set(FMLLaunchHandler.class, "side", Side.CLIENT);
    }

    // Forge finds an entity's id by its exact class, so a generated mock class is registered under a name while a test runs
    private static Map<Class<? extends Entity>, net.minecraftforge.fml.common.registry.EntityEntry> byClass() {
        return Mixins.get(net.minecraftforge.fml.common.registry.EntityRegistry.instance(), "entityClassEntries");
    }

    private static void register(Entity entity, String name) {
        byClass().put(entity.getClass(), new net.minecraftforge.fml.common.registry.EntityEntry(entity.getClass(), name)
                .setRegistryName(new ResourceLocation(name)));
    }

    @Test
    void entitiesThatTouchSharedStateTickOnTheMainThread() {
        WorldServer world = world(true);
        EntityPig pig = pig(world, 0, 0);
        net.minecraft.entity.passive.EntityCow beast = Mc.mock(net.minecraft.entity.passive.EntityCow.class, PortalAccessor.class);
        Mixins.set(beast, "world", world);
        register(pig, "minecraft:pig");
        register(beast, "examplemod:beast");
        try {
            assertFalse(ParallelProcessor.shouldTickSynchronously(pig));
            // Cached per class after the first answer
            assertFalse(ParallelProcessor.shouldTickSynchronously(pig));
            // A modded entity only joins the pool when the user lets modded ones in, and not if they listed it
            assertTrue(ParallelProcessor.shouldTickSynchronously(beast));
            ExtrasConfig.AsyncSettings settings = new ExtrasConfig.AsyncSettings();
            settings.moddedEntities = true;
            ParallelProcessor.apply(settings);
            assertFalse(ParallelProcessor.shouldTickSynchronously(beast));
            settings.synchronizedEntities = new String[] {"examplemod:*"};
            ParallelProcessor.apply(settings);
            assertTrue(ParallelProcessor.shouldTickSynchronously(beast));
        } finally {
            byClass().remove(pig.getClass());
            byClass().remove(beast.getClass());
            SyncEntityRules.load(new String[0]);
        }

        EntityPig inPortal = pig(world, 0, 0);
        when(((PortalAccessor) inPortal).impetus$isInPortal()).thenReturn(true);
        assertTrue(ParallelProcessor.shouldTickSynchronously(inPortal));

        EntityPlayer player = Mc.mock(EntityPlayer.class, PortalAccessor.class);
        Mixins.set(player, "world", world);
        assertTrue(ParallelProcessor.shouldTickSynchronously(player));

        // An entity nobody registered is an unknown quantity
        Entity unregistered = Mc.mock(Entity.class, PortalAccessor.class);
        Mixins.set(unregistered, "world", world);
        assertTrue(ParallelProcessor.shouldTickSynchronously(unregistered));

        // A client world never batches, and neither does a stopping server
        World client = Mc.mock(World.class, ParallelWorld.class);
        Mixins.set(client, "isRemote", true);
        assertTrue(ParallelProcessor.shouldTickSynchronously(pig(client, 0, 0)));
        ParallelProcessor.shutdown();
        assertTrue(ParallelProcessor.shouldTickSynchronously(pig));
        ParallelProcessor.onServerStart();
    }

    @Test
    void entitiesTickInSectionBatchesAndACrashSurfacesOnTheMainThread() {
        WorldServer world = world(true);
        List<Entity> async = new ArrayList<>();
        // Some sections full enough for their own tasks, the rest packed together
        for (int i = 0; i < 400; i++) {
            async.add(pig(world, i < 300 ? 0 : i, i < 300 ? 0 : -i));
        }
        EntityPig dead = pig(world, 1, 1);
        Mixins.set(dead, "isDead", true);
        async.add(dead);
        List<Entity> sync = List.of(pig(world, 2, 2));
        ParallelProcessor.tickEntities(world, async, sync);
        verify(world, org.mockito.Mockito.times(401)).updateEntity(any());
        assertTrue(ParallelProcessor.poolSize() > 0);

        // A crash in a pooled tick is rethrown here once the batch settles
        EntityPig crashing = async.get(350) instanceof EntityPig p ? p : null;
        doThrow(new IllegalStateException("bad tick")).when(world).updateEntity(crashing);
        assertThrows(ReportedException.class, () -> ParallelProcessor.tickEntities(world, async, sync));
        // A small batch ticks in place, throwing straight away
        assertThrows(ReportedException.class, () -> ParallelProcessor.tickEntities(world, List.of(crashing), List.of()));
        // With Forge's removal switch on, the entity is dropped instead
        ForgeModContainer.removeErroringEntities = true;
        ParallelProcessor.tickEntities(world, List.of(crashing), List.of());
        verify(world).removeEntity(crashing);
    }

    @Test
    void genericBatchesRunInPlaceOrOnThePoolAndLogFailures() {
        ParallelProcessor.forEachParallel(List.of(), new CostModel(1D), item -> fail("nothing to run"));
        List<Integer> small = List.of(1, 2, 3);
        Set<Integer> seen = ConcurrentHashMap.newKeySet();
        ParallelProcessor.forEachParallel(small, new CostModel(1D), seen::add);
        assertEquals(Set.of(1, 2, 3), seen);

        List<Integer> many = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            many.add(i);
        }
        Set<Integer> done = ConcurrentHashMap.newKeySet();
        AtomicInteger onWorkers = new AtomicInteger();
        ParallelProcessor.forEachParallel(many, new CostModel(100_000D), item -> {
            done.add(item);
            if (ParallelProcessor.isWorkerThread()) {
                onWorkers.incrementAndGet();
            }
            if (item == 500) {
                throw new IllegalStateException("one bad item");
            }
        });
        assertEquals(1000, done.size());
        assertFalse(ParallelProcessor.isWorkerThread());
        assertNotNull(Mixins.construct(ParallelProcessor.class));
        assertNotNull(new ParallelWorkerThread(() -> { }, "worker"));
    }

    @Test
    void spawningWalksTheChunksAroundEachPlayer() {
        WorldServer world = world(true);
        // Neither kind of mob wanted means nothing to do
        assertEquals(0, ParallelSpawner.findChunksForSpawning(world, false, false, true));

        EntityPlayer player = mock(EntityPlayer.class);
        EntityPlayer spectator = mock(EntityPlayer.class);
        when(spectator.isSpectator()).thenReturn(true);
        Mixins.set(world, "playerEntities", new ArrayList<>(List.of(player, spectator)));
        WorldBorder border = mock(WorldBorder.class);
        when(border.contains(any(net.minecraft.util.math.ChunkPos.class))).thenReturn(true);
        when(world.getWorldBorder()).thenReturn(border);
        PlayerChunkMap chunkMap = mock(PlayerChunkMap.class);
        PlayerChunkMapEntry sent = mock(PlayerChunkMapEntry.class);
        when(sent.isSentToPlayers()).thenReturn(true);
        when(chunkMap.getEntry(anyInt(), anyInt())).thenReturn(sent);
        when(world.getPlayerChunkMap()).thenReturn(chunkMap);
        when(world.getSpawnPoint()).thenReturn(new BlockPos(10_000, 64, 10_000));
        Chunk chunk = mock(Chunk.class);
        when(chunk.getHeight(any())).thenReturn(63);
        when(world.getChunk(anyInt(), anyInt())).thenReturn(chunk);
        // Every random start lands in stone, so each chunk is visited and nothing is placed
        when(world.getBlockState(any())).thenReturn(Blocks.STONE.getDefaultState());
        // Animals are over their cap already
        when(world.countEntities(any(), anyBoolean())).thenAnswer(invocation ->
                invocation.<net.minecraft.entity.EnumCreatureType>getArgument(0).getAnimal() ? 1000 : 0);

        assertEquals(0, ParallelSpawner.findChunksForSpawning(world, true, true, true));
        // Without the tick-rate flag animals are skipped outright
        assertEquals(0, ParallelSpawner.findChunksForSpawning(world, true, false, false));
        verify(world, org.mockito.Mockito.atLeastOnce()).getChunk(anyInt(), anyInt());
    }
}
