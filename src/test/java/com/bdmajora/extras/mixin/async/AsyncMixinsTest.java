package com.bdmajora.extras.mixin.async;

import com.bdmajora.extras.ExtrasConfig;
import com.bdmajora.extras.async.ConcurrentEntityList;
import com.bdmajora.extras.async.ConcurrentLong2ObjectMap;
import com.bdmajora.extras.async.ParallelProcessor;
import com.bdmajora.extras.async.ParallelWorkerThread;
import com.bdmajora.extras.async.ParallelWorld;
import com.bdmajora.extras.async.PortalAccessor;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.fml.relauncher.FMLLaunchHandler;
import net.minecraftforge.fml.relauncher.Side;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AsyncMixinsTest {
    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
        Statics.set(FMLLaunchHandler.class, "side", Side.CLIENT);
    }

    @AfterEach
    void switchOff() {
        ParallelProcessor.INSTALLED = false;
        ParallelProcessor.apply(new ExtrasConfig.AsyncSettings());
        ParallelProcessor.shutdown();
        ParallelProcessor.onServerStart();
    }

    // Parallel ticking installed and every live switch on
    private static void parallel() {
        ExtrasConfig.AsyncSettings settings = new ExtrasConfig.AsyncSettings();
        ParallelProcessor.install(settings);
        ParallelProcessor.apply(settings);
    }

    // Calls each named wrap handler with placeholder arguments and a recording original, which must run exactly once
    private static void runsOriginal(Object mixin, String... handlers) {
        for (String name : handlers) {
            Method method = handler(mixin.getClass(), name);
            Class<?>[] types = method.getParameterTypes();
            Object[] args = new Object[types.length];
            for (int i = 0; i < types.length - 1; i++) {
                args[i] = placeholder(types[i]);
            }
            Mc.Recorded<Object> original = Mc.operation(placeholder(method.getReturnType()));
            args[types.length - 1] = original;
            method.setAccessible(true);
            try {
                method.invoke(mixin, args);
            } catch (InvocationTargetException e) {
                throw new AssertionError(name, e.getCause());
            } catch (IllegalAccessException e) {
                throw new AssertionError(e);
            }
            assertEquals(1, original.count(), name);
        }
    }

    private static Method handler(Class<?> type, String name) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            for (Method method : c.getDeclaredMethods()) {
                if (method.getName().equals(name) && !method.isSynthetic()) {
                    return method;
                }
            }
        }
        throw new AssertionError("no handler " + name);
    }

    private static Object placeholder(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        return null;
    }

    @Test
    void sharedServerStructuresAreOnlyLockedWithParallelTickingInstalled() {
        Object[] mixins = {
                Mixins.instance(BiomeCacheParallelMixin.class),
                Mixins.instance(EntityTrackerParallelMixin.class),
                Mixins.instance(PlayerChunkMapEntryParallelMixin.class),
                Mixins.instance(VillageCollectionParallelMixin.class),
                Mixins.instance(VillageParallelMixin.class),
                Mixins.instance(EntityParallelMixin.class)};
        String[][] handlers = {
                {"impetus$lockedGetEntry", "impetus$lockedCleanup"},
                {"impetus$lockedTrack", "impetus$lockedUntrack", "impetus$lockedSendToTracking", "impetus$lockedSendToTrackingAndSelf", "impetus$lockedTick"},
                {"impetus$lockedBlockChanged", "impetus$lockedUpdate"},
                {"impetus$lockedAddPosition"},
                {"impetus$lockedAddOrRenewAggressor", "impetus$lockedModifyReputation", "impetus$lockedSetDefaultReputation"},
                {"impetus$lockedAddPassenger", "impetus$lockedRemovePassenger"}};
        for (boolean installed : new boolean[] {false, true}) {
            ParallelProcessor.INSTALLED = installed;
            for (int i = 0; i < mixins.length; i++) {
                runsOriginal(mixins[i], handlers[i]);
            }
        }
    }

    @Test
    void entityIdsComeFromAnAtomicCounterAndPortalTransitIsVisible() {
        EntityParallelMixin entity = Mixins.instance(EntityParallelMixin.class);
        Mixins.call(entity, "impetus$atomicId", Mixins.ci());
        int first = Mixins.get(entity, "entityId");
        Mixins.call(entity, "impetus$atomicResetId", Mixins.ci());
        assertEquals(first + 1, (int) Mixins.<Integer>get(entity, "entityId"));
        assertFalse(entity.impetus$isInPortal());
        Mixins.set(entity, "inPortal", true);
        assertTrue(entity.impetus$isInPortal());
    }

    @Test
    void worldAndServerWritesAreLockedOnlyForParallelWorlds() {
        WorldWriteLockMixin world = Mixins.instance(WorldWriteLockMixin.class);
        WorldServerParallelMixin server = Mixins.instance(WorldServerParallelMixin.class);
        for (boolean parallel : new boolean[] {false, true}) {
            doReturn(parallel).when(world).impetus$isParallel();
            doReturn(parallel).when(server).impetus$isParallel();
            runsOriginal(world, "impetus$lockedSetBlockState", "impetus$lockedMarkAndNotify", "impetus$lockedGetEntityByID",
                    "impetus$lockedSpawnEntity", "impetus$lockedRemoveEntity", "impetus$lockedRemoveEntityDangerously",
                    "impetus$lockedLoadEntities", "impetus$lockedUnloadEntities", "impetus$lockedAddTileEntity",
                    "impetus$lockedAddTileEntities", "impetus$lockedSetTileEntity", "impetus$lockedRemoveTileEntity");
            runsOriginal(server, "impetus$lockedOnEntityAdded", "impetus$lockedOnEntityRemoved", "impetus$lockedGetEntityFromUuid",
                    "impetus$lockedUpdateBlockTick", "impetus$lockedScheduleBlockUpdate", "impetus$lockedIsBlockTickPending",
                    "impetus$lockedIsUpdateScheduled", "impetus$lockedTickUpdates", "impetus$lockedAddBlockEvent",
                    "impetus$lockedSendQueuedBlockEvents", "impetus$lockedNewExplosion");
        }
    }

    @Test
    void randomTicksAreCollectedAndRunTogetherAfterTheSweep() {
        WorldServerParallelMixin server = Mixins.instance(WorldServerParallelMixin.class);
        Block block = mock(Block.class);
        IBlockState state = Blocks.STONE.getDefaultState();
        BlockPos pos = new BlockPos(1, 2, 3);

        // Off, the block ticks in place
        doReturn(false).when(server).impetus$isParallel();
        Mc.Recorded<Void> original = Mc.operation();
        Mixins.call(server, "impetus$collectRandomTick", block, server, pos, state, new Random(), original);
        assertEquals(1, original.count());
        Mixins.call(server, "impetus$flushRandomTicks", Mixins.ci());

        // On, the reaction waits for the flush, and runs with its own random source
        parallel();
        doReturn(true).when(server).impetus$isParallel();
        Mc.Recorded<Void> deferred = Mc.operation();
        Mixins.call(server, "impetus$collectRandomTick", block, server, pos, state, new Random(), deferred);
        Mixins.call(server, "impetus$collectRandomTick", block, server, pos, state, new Random(), deferred);
        assertEquals(0, deferred.count());
        verify(block, never()).randomTick(any(), any(), any(), any());
        Mixins.call(server, "impetus$flushRandomTicks", Mixins.ci());
        verify(block, org.mockito.Mockito.times(2)).randomTick(any(), any(), any(), any());
    }

    @Test
    void chunkProvidersAnswerLoadedChunksWithoutTheLock() {
        ChunkProviderServerParallelMixin provider = Mixins.instance(ChunkProviderServerParallelMixin.class);
        Long2ObjectOpenHashMap<Chunk> loaded = new Long2ObjectOpenHashMap<>();
        Mixins.set(provider, "loadedChunks", loaded);
        Mixins.set(provider, "world", mock(WorldServer.class));
        // Before the install everything goes straight through
        Mixins.call(provider, "impetus$installConcurrentMap", Mixins.ci());
        assertSame(loaded, Mixins.get(provider, "loadedChunks"));
        String[] handlers = {"impetus$lockedProvideChunk", "impetus$lockedLoadChunk", "impetus$lockedQueueUnload",
                "impetus$lockedQueueUnloadAll", "impetus$lockedTick", "impetus$lockedSaveChunks"};
        runsOriginal(provider, handlers);

        ParallelProcessor.INSTALLED = true;
        Mixins.call(provider, "impetus$installConcurrentMap", Mixins.ci());
        assertInstanceOf(ConcurrentLong2ObjectMap.class, Mixins.get(provider, "loadedChunks"));
        // An unloaded chunk goes through the original under the world and worldgen locks
        runsOriginal(provider, handlers);

        // A loaded one is answered straight from the map, and a load callback still runs
        Chunk chunk = mock(Chunk.class);
        doReturn(chunk).when(provider).getLoadedChunk(4, 5);
        Mc.Recorded<Chunk> original = Mc.operation(null);
        assertSame(chunk, Mixins.call(provider, "impetus$lockedProvideChunk", 4, 5, original));
        AtomicReference<Boolean> ran = new AtomicReference<>(false);
        assertSame(chunk, Mixins.call(provider, "impetus$lockedLoadChunk", 4, 5, (Runnable) () -> ran.set(true), original));
        assertTrue(ran.get());
        assertSame(chunk, Mixins.call(provider, "impetus$lockedLoadChunk", 4, 5, null, original));
        assertEquals(0, original.count());
    }

    private static BlockStateContainerParallelMixin container() {
        return Mixins.instance(BlockStateContainerParallelMixin.class);
    }

    @Test
    void sectionPalettesLockOnlyOnceTheirChunkSaysSo() {
        BlockStateContainerParallelMixin palette = container();
        // Without a lock reads and writes go straight through
        runsOriginal(palette, "impetus$lockedGet", "impetus$lockedSet");
        palette.impetus$enableParallelLock();
        palette.impetus$enableParallelLock();
        runsOriginal(palette, "impetus$lockedGet", "impetus$lockedSet");
    }

    private static ExtendedBlockStorage section(BlockStateContainerParallelMixin palette) {
        ExtendedBlockStorage section = new ExtendedBlockStorage(0, true);
        Mixins.set(section, "data", palette);
        return section;
    }

    @Test
    void aParallelWorldsChunksGetConcurrentStorageAndLockedSections() {
        ChunkParallelMixin chunk = Mixins.instance(ChunkParallelMixin.class);
        Map<BlockPos, TileEntity> tiles = new HashMap<>();
        Mixins.set(chunk, "tileEntities", tiles);
        WorldServer world = Mc.mock(WorldServer.class, ParallelWorld.class);
        Mixins.set(chunk, "world", world);
        BlockStateContainerParallelMixin palette = container();
        doReturn(new ExtendedBlockStorage[] {section(palette), null}).when(chunk).getBlockStorageArray();

        // Not installed, no world, a client world or a serial world all leave the chunk as vanilla built it
        Mixins.call(chunk, "impetus$installConcurrentMap", world, 0, 0, Mixins.ci());
        ParallelProcessor.INSTALLED = true;
        Mixins.call(chunk, "impetus$installConcurrentMap", null, 0, 0, Mixins.ci());
        World client = Mc.mock(World.class, ParallelWorld.class);
        Mixins.set(client, "isRemote", true);
        Mixins.call(chunk, "impetus$installConcurrentMap", client, 0, 0, Mixins.ci());
        Mixins.call(chunk, "impetus$installConcurrentMap", world, 0, 0, Mixins.ci());
        assertSame(tiles, Mixins.get(chunk, "tileEntities"));
        Mixins.call(chunk, "impetus$lockGeneratedSections", world, null, 0, 0, Mixins.ci());
        assertNull(Mixins.get(palette, "impetus$lock"));
        runsOriginal(chunk, "impetus$lockedAdd", "impetus$lockedRemove", "impetus$lockedGetEntities",
                "impetus$lockedGetEntitiesOfType", "impetus$lockedGetTileEntity");
        ExtendedBlockStorage plain = new ExtendedBlockStorage(16, true);
        assertSame(plain, Mixins.call(chunk, "impetus$lockNewSection", 16, true, Mc.operation(plain)));

        when(((ParallelWorld) world).impetus$isParallel()).thenReturn(true);
        Mixins.call(chunk, "impetus$installConcurrentMap", world, 0, 0, Mixins.ci());
        assertInstanceOf(ConcurrentHashMap.class, Mixins.get(chunk, "tileEntities"));
        // Every section the chunk gains from here on carries a palette lock
        Mixins.call(chunk, "impetus$lockLoadedSections", new ExtendedBlockStorage[0], Mixins.ci());
        assertNotNull(Mixins.get(palette, "impetus$lock"));
        BlockStateContainerParallelMixin fresh = container();
        Mixins.call(chunk, "impetus$lockNewSection", 32, true, Mc.operation(section(fresh)));
        assertNotNull(Mixins.get(fresh, "impetus$lock"));
        runsOriginal(chunk, "impetus$lockedAdd", "impetus$lockedRemove", "impetus$lockedGetEntities",
                "impetus$lockedGetEntitiesOfType");

        // A live block entity is answered lock-free; a check for one that is not there never builds it
        BlockPos pos = new BlockPos(1, 1, 1);
        TileEntity tile = mock(TileEntity.class);
        Map<BlockPos, TileEntity> concurrent = Mixins.get(chunk, "tileEntities");
        concurrent.put(pos, tile);
        Mc.Recorded<TileEntity> create = Mc.operation(null);
        assertSame(tile, Mixins.call(chunk, "impetus$lockedGetTileEntity", pos, Chunk.EnumCreateEntityType.IMMEDIATE, create));
        assertNull(Mixins.call(chunk, "impetus$lockedGetTileEntity", new BlockPos(2, 2, 2), Chunk.EnumCreateEntityType.CHECK, create));
        assertEquals(0, create.count());
        // An invalidated one goes to the original, which may replace it
        when(tile.isInvalid()).thenReturn(true);
        Mixins.call(chunk, "impetus$lockedGetTileEntity", pos, Chunk.EnumCreateEntityType.CHECK, create);
        assertEquals(1, create.count());
    }

    @Test
    void theServersPoolFollowsItsLifecycle() {
        MinecraftServerParallelMixin server = Mixins.instance(MinecraftServerParallelMixin.class);
        parallel();
        WorldServer world = Mc.mock(WorldServer.class, ParallelWorld.class);
        when(((ParallelWorld) world).impetus$isParallel()).thenReturn(true);
        Mixins.call(server, "impetus$shutdownPool", Mixins.ci());
        assertFalse(ParallelProcessor.isEntityTickingActive(world));
        Mixins.call(server, "impetus$serverStarting", Mixins.ci());
        assertTrue(ParallelProcessor.isEntityTickingActive(world));
    }

    @Test
    void theProfilerIgnoresWorkerThreads() throws Exception {
        ProfilerParallelMixin profiler = Mixins.instance(ProfilerParallelMixin.class);
        String[] handlers = {"impetus$skipStartOffThread", "impetus$skipStartSupplierOffThread", "impetus$skipEndOffThread",
                "impetus$skipEndStartOffThread", "impetus$skipEndStartSupplierOffThread"};
        List<Boolean> onMain = new ArrayList<>();
        List<Boolean> onWorker = new CopyOnWriteArrayList<>();
        Runnable probe = () -> {
            List<Boolean> into = ParallelWorkerThread.isCurrent() ? onWorker : onMain;
            for (String name : handlers) {
                Method method = handler(profiler.getClass(), name);
                CallbackInfo ci = Mixins.ci();
                Object[] args = method.getParameterCount() == 1 ? new Object[] {ci} : new Object[] {null, ci};
                method.setAccessible(true);
                try {
                    method.invoke(profiler, args);
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                }
                into.add(ci.isCancelled());
            }
        };
        probe.run();
        Thread worker = new ParallelWorkerThread(probe, "profiler probe");
        worker.start();
        worker.join();
        assertEquals(List.of(false, false, false, false, false), onMain);
        assertEquals(List.of(true, true, true, true, true), onWorker);
    }

    @Test
    void spawningTakesTheParallelPathOnlyWhenActive() {
        WorldEntitySpawnerParallelMixin spawner = Mixins.instance(WorldEntitySpawnerParallelMixin.class);
        WorldServer world = Mc.mock(WorldServer.class, ParallelWorld.class);
        CallbackInfoReturnable<Integer> serial = Mixins.cir();
        Mixins.call(spawner, "impetus$parallelSpawn", world, false, false, false, serial);
        assertFalse(serial.isCancelled());

        parallel();
        when(((ParallelWorld) world).impetus$isParallel()).thenReturn(true);
        CallbackInfoReturnable<Integer> active = Mixins.cir();
        Mixins.call(spawner, "impetus$parallelSpawn", world, false, false, false, active);
        assertTrue(active.isCancelled());
        assertEquals(0, active.getReturnValue());
    }

    private static EntityPig pig(World world, boolean dead) {
        EntityPig pig = Mc.mock(EntityPig.class, PortalAccessor.class);
        Mixins.set(pig, "world", world);
        Mixins.set(pig, "isDead", dead);
        Mixins.set(pig, "addedToChunk", true);
        return pig;
    }

    @SuppressWarnings("unchecked")
    @Test
    void entitiesAreBatchedBetweenTheRegularAndBlockEntitySections() {
        WorldParallelTickMixin world = Mixins.instance(WorldParallelTickMixin.class);
        Mixins.set(world, "loadedEntityList", new ArrayList<Entity>());
        Mixins.set(world, "playerEntities", new ArrayList<>());
        doNothing().when((World) (Object) world).updateEntity(any());

        // Not installed: vanilla's lists stay, nothing is armed, every entity ticks in place
        Mixins.call(world, "impetus$installConcurrentLists", Mixins.ci());
        assertFalse(world.impetus$isParallel());
        Mixins.call(world, "impetus$armBatch", Mixins.ci());
        World other = mock(World.class);
        EntityPig pig = pig((World) (Object) world, false);
        Mixins.call(world, "impetus$collectOrTick", other, pig);
        verify(other).updateEntity(pig);
        Mixins.call(world, "impetus$runBatch", Mixins.ci());
        // A client world is never parallel either
        ParallelProcessor.INSTALLED = true;
        Mixins.set(world, "isRemote", true);
        Mixins.call(world, "impetus$installConcurrentLists", Mixins.ci());
        assertFalse(world.impetus$isParallel());
        Mixins.set(world, "isRemote", false);

        parallel();
        Mixins.call(world, "impetus$installConcurrentLists", Mixins.ci());
        assertTrue(world.impetus$isParallel());
        List<Entity> loaded = Mixins.get(world, "loadedEntityList");
        assertInstanceOf(ConcurrentEntityList.class, loaded);
        assertInstanceOf(CopyOnWriteArrayList.class, Mixins.get(world, "playerEntities"));

        // With nothing dead the batch just ticks
        Mixins.call(world, "impetus$armBatch", Mixins.ci());
        Mixins.call(world, "impetus$collectOrTick", other, pig);
        Mixins.call(world, "impetus$runBatch", Mixins.ci());
        verify((World) (Object) world).updateEntity(pig);

        // The dead leave their chunk, the list and the listeners once; one vanilla already removed is skipped
        EntityPig dying = pig((World) (Object) world, true);
        EntityPig gone = pig((World) (Object) world, true);
        loaded.add(dying);
        Chunk chunk = mock(Chunk.class);
        Mixins.stub(world, "isChunkLoaded", invocation -> true);
        doReturn(chunk).when(world).getChunk(0, 0);
        Mixins.call(world, "impetus$armBatch", Mixins.ci());
        Mixins.call(world, "impetus$collectOrTick", other, dying);
        Mixins.call(world, "impetus$collectOrTick", other, gone);
        Mixins.call(world, "impetus$runBatch", Mixins.ci());
        verify(chunk).removeEntity(dying);
        verify(chunk, never()).removeEntity(gone);
        assertFalse(loaded.contains(dying));
        verify(world).onEntityRemoved(dying);

        // A batch where the only dead entity was already gone removes nothing
        Mixins.call(world, "impetus$armBatch", Mixins.ci());
        Mixins.call(world, "impetus$collectOrTick", other, gone);
        Mixins.call(world, "impetus$runBatch", Mixins.ci());
        verify(world, never()).onEntityRemoved(gone);
    }
}
