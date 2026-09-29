package com.bdmajora.equilibrium.mixin;

import com.bdmajora.equilibrium.common.world.ChunkAccess;
import com.bdmajora.equilibrium.common.world.UnloadedEntityRemover;
import com.bdmajora.equilibrium.mixin.math.fast_blockpos.BlockPosMixin;
import com.bdmajora.equilibrium.mixin.math.sine_lut.MathHelperMixin;
import com.bdmajora.equilibrium.mixin.util.chunk_access.WorldMixin;
import com.bdmajora.equilibrium.mixin.world.chunk_gen_limit.PlayerChunkMapMixin;
import com.bdmajora.equilibrium.mixin.world.entity_cleanup.WorldServerMixin;
import com.bdmajora.equilibrium.mixin.world.ghost_chunks.BlockBedMixin;
import com.bdmajora.equilibrium.mixin.world.ghost_chunks.BlockFarmlandMixin;
import com.bdmajora.equilibrium.mixin.world.ghost_chunks.BlockFluidClassicMixin;
import com.bdmajora.equilibrium.mixin.world.ghost_chunks.BlockFluidFiniteMixin;
import com.bdmajora.equilibrium.mixin.world.spawn_chunks.MinecraftServerMixin;
import com.bdmajora.equilibrium.mixin.worldgen.chunk_copy.ExtendedBlockStorageAccessor;
import com.bdmajora.equilibrium.mixin.worldgen.int_cache.IntCacheMixin;
import com.bdmajora.equilibrium.mixin.worldgen.primer_state_cache.ChunkPrimerMixin;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.ShadowStubs;
import com.bdmajora.testing.Statics;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.server.management.PlayerChunkMap;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorldMixinTest {
    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
    }

    @AfterEach
    void reset() {
        ShadowStubs.clear();
    }

    @Test
    void theChunkCacheAnswersRepeatedReadsInTheSameChunk() {
        WorldMixin world = Mixins.instance(WorldMixin.class);
        IChunkProvider provider = mock(IChunkProvider.class);
        Mixins.set(world, "chunkProvider", provider);
        Chunk chunk = mock(Chunk.class);
        Mixins.set(chunk, "x", 1);
        Mixins.set(chunk, "z", 2);
        when(chunk.isLoaded()).thenReturn(true);
        when(provider.getLoadedChunk(1, 2)).thenReturn(chunk);
        when(provider.provideChunk(1, 2)).thenReturn(chunk);

        assertSame(chunk, world.equilibrium$getLoadedChunk(1, 2));
        chunk.unloadQueued = true;
        assertSame(chunk, world.equilibrium$getLoadedChunk(1, 2));
        // The second read is answered from the cache, which also cancels the pending unload
        assertFalse(chunk.unloadQueued);
        Mockito.verify(provider, Mockito.times(1)).getLoadedChunk(1, 2);
        assertNull(world.equilibrium$getLoadedChunk(5, 5));
        // An unloaded cached chunk is dropped rather than answered
        when(chunk.isLoaded()).thenReturn(false);
        assertSame(chunk, world.equilibrium$getLoadedChunk(1, 2));
        Mockito.verify(provider, Mockito.times(2)).getLoadedChunk(1, 2);

        when(chunk.isLoaded()).thenReturn(true);
        assertSame(chunk, world.equilibrium$getChunkCached(1, 2));
        assertSame(chunk, world.equilibrium$getChunkCached(1, 2));
        Mockito.verify(provider, Mockito.never()).provideChunk(1, 2);
        // The client's shared empty chunk claims the wrong coordinates, so it is never cached
        Chunk empty = mock(Chunk.class);
        Mixins.set(empty, "x", 0);
        Mixins.set(empty, "z", 0);
        when(provider.provideChunk(9, 9)).thenReturn(empty);
        assertSame(empty, world.equilibrium$getChunkCached(9, 9));
        assertSame(empty, world.equilibrium$getChunkCached(9, 9));
        Mockito.verify(provider, Mockito.times(2)).provideChunk(9, 9);

        // The other world mixins resolve everything through the same cache
        var inlineBlocks = Mixins.instance(com.bdmajora.equilibrium.mixin.world.inline_block_access.WorldMixin.class);
        Mockito.doReturn(chunk).when(inlineBlocks).equilibrium$getChunkCached(1, 2);
        assertSame(chunk, inlineBlocks.getChunk(1, 2));

        var inlineHeight = Mixins.instance(com.bdmajora.equilibrium.mixin.world.inline_height.WorldMixin.class);
        Mockito.doReturn(chunk).when(inlineHeight).equilibrium$getLoadedChunk(0, 0);
        when(chunk.getHeightValue(1, 2)).thenReturn(70);
        assertEquals(70, inlineHeight.getHeight(1, 2));
        assertEquals(0, inlineHeight.getHeight(100, 100));
        Mockito.doReturn(63).when((World) (Object) inlineHeight).getSeaLevel();
        assertEquals(64, inlineHeight.getHeight(-30000001, 0));
    }

    @Test
    void existingTileEntitiesAreReadWithoutCreatingThem() {
        var world = Mixins.instanceWith(com.bdmajora.equilibrium.mixin.util.block_entity_retrieval.WorldMixin.class, ChunkAccess.class);
        BlockPos pos = new BlockPos(1, 2, 3);
        Chunk chunk = mock(Chunk.class);
        TileEntity tile = mock(TileEntity.class);
        Mockito.doReturn(false).when((World) (Object) world).isOutsideBuildHeight(pos);
        Mockito.doReturn(chunk).when((ChunkAccess) world).equilibrium$getLoadedChunk(0, 0);
        when(chunk.getTileEntity(pos, Chunk.EnumCreateEntityType.CHECK)).thenReturn(tile);
        assertSame(tile, world.equilibrium$getExistingTileEntity(pos));
        // An invalidated tile entity is not handed out
        when(tile.isInvalid()).thenReturn(true);
        assertNull(world.equilibrium$getExistingTileEntity(pos));
        when(chunk.getTileEntity(pos, Chunk.EnumCreateEntityType.CHECK)).thenReturn(null);
        assertNull(world.equilibrium$getExistingTileEntity(pos));
        // Outside the build height, or in an unloaded chunk, there is nothing to read
        Mockito.doReturn(true).when((World) (Object) world).isOutsideBuildHeight(pos);
        assertNull(world.equilibrium$getExistingTileEntity(pos));
        Mockito.doReturn(false).when((World) (Object) world).isOutsideBuildHeight(pos);
        Mockito.doReturn(null).when((ChunkAccess) world).equilibrium$getLoadedChunk(0, 0);
        assertNull(world.equilibrium$getExistingTileEntity(pos));
    }

    @Test
    void idleWorldsStillDrainTheirRemovalQueues() {
        var world = Mixins.instance(com.bdmajora.equilibrium.mixin.world.entity_cleanup.WorldMixin.class);
        List<Entity> loaded = new ArrayList<>();
        List<Entity> unloaded = new ArrayList<>();
        List<TileEntity> loadedTiles = new ArrayList<>();
        List<TileEntity> tickableTiles = new ArrayList<>();
        List<TileEntity> removedTiles = new ArrayList<>();
        Mixins.set(world, "loadedEntityList", loaded);
        Mixins.set(world, "unloadedEntityList", unloaded);
        Mixins.set(world, "loadedTileEntityList", loadedTiles);
        Mixins.set(world, "tickableTileEntities", tickableTiles);
        Mixins.set(world, "tileEntitiesToBeRemoved", removedTiles);
        // Nothing queued is a no-op
        world.equilibrium$removeUnloaded();

        Entity inChunk = mock(Entity.class);
        inChunk.addedToChunk = true;
        inChunk.chunkCoordX = 1;
        inChunk.chunkCoordZ = 2;
        Entity loose = mock(Entity.class);
        loaded.add(inChunk);
        loaded.add(loose);
        unloaded.add(inChunk);
        unloaded.add(loose);
        Chunk chunk = mock(Chunk.class);
        Mixins.stub(world, "isChunkLoaded", invocation -> true);
        Mockito.doReturn(chunk).when(world).getChunk(1, 2);
        TileEntity tile = mock(TileEntity.class);
        loadedTiles.add(tile);
        tickableTiles.add(tile);
        removedTiles.add(tile);

        world.equilibrium$removeUnloaded();
        assertTrue(loaded.isEmpty());
        assertTrue(unloaded.isEmpty());
        assertTrue(removedTiles.isEmpty());
        assertTrue(loadedTiles.isEmpty());
        assertTrue(tickableTiles.isEmpty());
        Mockito.verify(chunk).removeEntity(inChunk);
        Mockito.verify(tile).onChunkUnload();
        Mockito.verify(world).onEntityRemoved(inChunk);
        Mockito.verify(world).onEntityRemoved(loose);

        // The server only runs it on the ticks vanilla skips
        WorldServerMixin server = Mixins.instanceWith(WorldServerMixin.class, UnloadedEntityRemover.class);
        WorldServer self = (WorldServer) (Object) server;
        Mixins.set(self, "playerEntities", new ArrayList<EntityPlayer>());
        Mixins.set(server, "updateEntityTick", 299);
        Mixins.call(server, "equilibrium$removeOnIdleTicks", Mixins.ci());
        Mixins.set(server, "updateEntityTick", 300);
        Mixins.call(server, "equilibrium$removeOnIdleTicks", Mixins.ci());
        self.playerEntities.add(mock(EntityPlayer.class));
        Mixins.call(server, "equilibrium$removeOnIdleTicks", Mixins.ci());
        // Only the idle tick reached the remover, which the server implements itself
        Mockito.verify((UnloadedEntityRemover) self, Mockito.times(1)).equilibrium$removeUnloaded();
    }

    @Test
    void ghostChunkGuardsRefuseToLoadNeighbours() {
        BlockBedMixin bed = Mixins.instance(BlockBedMixin.class);
        World world = mock(World.class);
        IBlockState foot = Blocks.BED.getDefaultState()
                .withProperty(BlockBed.PART, BlockBed.EnumPartType.FOOT)
                .withProperty(BlockBed.FACING, net.minecraft.util.EnumFacing.NORTH);
        IBlockState head = foot.withProperty(BlockBed.PART, BlockBed.EnumPartType.HEAD);
        BlockPos pos = new BlockPos(0, 64, 0);
        when(world.isBlockLoaded(any())).thenReturn(true);
        var loadedHalf = Mixins.ci();
        Mixins.call(bed, "equilibrium$skipUnloadedOtherHalf", foot, world, pos, Blocks.BED, pos, loadedHalf);
        assertFalse(loadedHalf.isCancelled());
        when(world.isBlockLoaded(pos.north())).thenReturn(false);
        var unloadedFoot = Mixins.ci();
        Mixins.call(bed, "equilibrium$skipUnloadedOtherHalf", foot, world, pos, Blocks.BED, pos, unloadedFoot);
        assertTrue(unloadedFoot.isCancelled());
        var loadedHead = Mixins.ci();
        Mixins.call(bed, "equilibrium$skipUnloadedOtherHalf", head, world, pos, Blocks.BED, pos, loadedHead);
        assertFalse(loadedHead.isCancelled());

        // Farmland reads answer air rather than pulling a chunk in
        BlockFarmlandMixin farmland = Mixins.instance(BlockFarmlandMixin.class);
        IChunkProvider provider = mock(IChunkProvider.class);
        when(world.getChunkProvider()).thenReturn(provider);
        Operation<IBlockState> read = args -> Blocks.WATER.getDefaultState();
        assertSame(Blocks.AIR.getDefaultState(),
                Mixins.call(farmland, "equilibrium$readLoadedOnly", world, pos, read));
        when(provider.getLoadedChunk(0, 0)).thenReturn(mock(Chunk.class));
        assertSame(Blocks.WATER.getDefaultState(),
                Mixins.call(farmland, "equilibrium$readLoadedOnly", world, pos, read));
        when(world.isOutsideBuildHeight(pos)).thenReturn(true);
        assertSame(Blocks.AIR.getDefaultState(),
                Mixins.call(farmland, "equilibrium$readLoadedOnly", world, pos, read));
        // The fluidlogged replacement is guarded the same way, and a non-world access answers from itself
        assertSame(Blocks.WATER.getDefaultState(),
                Mixins.call(farmland, "equilibrium$readLoadedOnlyFluidlogged", mock(net.minecraft.world.IBlockAccess.class), pos, read));
        assertSame(Blocks.AIR.getDefaultState(),
                Mixins.call(farmland, "equilibrium$readLoadedOnlyFluidlogged", world, pos, read));

        // Fluids only tick with their working area loaded
        BlockFluidClassicMixin classic = Mixins.instance(BlockFluidClassicMixin.class);
        BlockFluidFiniteMixin finite = Mixins.instance(BlockFluidFiniteMixin.class);
        when(world.isAreaLoaded(pos, 4)).thenReturn(false);
        when(world.isAreaLoaded(pos, 1)).thenReturn(true);
        var classicTick = Mixins.ci();
        Mixins.call(classic, "equilibrium$requireLoadedArea", world, pos, Blocks.WATER.getDefaultState(), new Random(), classicTick);
        assertTrue(classicTick.isCancelled());
        var finiteTick = Mixins.ci();
        Mixins.call(finite, "equilibrium$requireLoadedArea", world, pos, Blocks.WATER.getDefaultState(), new Random(), finiteTick);
        assertFalse(finiteTick.isCancelled());
    }

    @Test
    void startupAndSchedulingLimitsAreLowered() {
        PlayerChunkMapMixin chunkMap = Mixins.instance(PlayerChunkMapMixin.class);
        assertEquals(23, (int) Mixins.call(chunkMap, "equilibrium$chunkLimit", 49));
        assertEquals(25_000_000L, (long) Mixins.call(chunkMap, "equilibrium$timeLimit", 50_000_000L));

        MinecraftServerMixin server = Mixins.instance(MinecraftServerMixin.class);
        WorldServer overworld = mock(WorldServer.class);
        IChunkProvider provider = mock(net.minecraft.world.gen.ChunkProviderServer.class);
        Mockito.doReturn(provider).when(overworld).getChunkProvider();
        when(overworld.getSpawnPoint()).thenReturn(new BlockPos(40, 64, -20));
        server.worlds = new WorldServer[] {overworld};
        var loading = Mixins.ci();
        Mixins.call(server, "equilibrium$loadOnlySpawnChunk", loading);
        assertTrue(loading.isCancelled());
        Mockito.verify(provider).provideChunk(2, -2);
        Mixins.verifyCall(server, "setUserMessage", "menu.generatingTerrain");
        Mixins.verifyCall(server, "clearCurrentTask");

        var scheduler = Mixins.instance(com.bdmajora.equilibrium.mixin.world.tick_scheduler.WorldServerMixin.class);
        Mixins.call(scheduler, "equilibrium$useFastutilSet", Mixins.ci());
        assertInstanceOf(it.unimi.dsi.fastutil.objects.ObjectOpenHashSet.class,
                Mixins.get(scheduler, "pendingTickListEntriesHashSet"));
    }

    @Test
    void blockPositionOffsetsAreBuiltDirectly() {
        BlockPosMixin pos = Mixins.instance(BlockPosMixin.class, 1, 2, 3);
        BlockPos self = (BlockPos) (Object) pos;
        assertEquals(new BlockPos(1, 3, 3), pos.up());
        assertEquals(new BlockPos(1, 4, 3), pos.up(2));
        assertSame(self, pos.up(0));
        assertEquals(new BlockPos(1, 1, 3), pos.down());
        assertEquals(new BlockPos(1, 0, 3), pos.down(2));
        assertSame(self, pos.down(0));
        assertEquals(new BlockPos(1, 2, 2), pos.north());
        assertEquals(new BlockPos(1, 2, 1), pos.north(2));
        assertSame(self, pos.north(0));
        assertEquals(new BlockPos(1, 2, 4), pos.south());
        assertEquals(new BlockPos(1, 2, 5), pos.south(2));
        assertSame(self, pos.south(0));
        assertEquals(new BlockPos(0, 2, 3), pos.west());
        assertEquals(new BlockPos(-1, 2, 3), pos.west(2));
        assertSame(self, pos.west(0));
        assertEquals(new BlockPos(2, 2, 3), pos.east());
        assertEquals(new BlockPos(3, 2, 3), pos.east(2));
        assertSame(self, pos.east(0));
    }

    @Test
    void facingLookupsAvoidArrayCopies() {
        var fast = Mixins.instance(com.bdmajora.equilibrium.mixin.math.fast_util.EnumFacingMixin.class);
        Mixins.set(com.bdmajora.equilibrium.mixin.math.fast_util.EnumFacingMixin.class, "VALUES", net.minecraft.util.EnumFacing.VALUES);
        Mixins.set(fast, "opposite", net.minecraft.util.EnumFacing.UP.ordinal());
        assertSame(net.minecraft.util.EnumFacing.UP, fast.getOpposite());
        Random fixed = new Random(7);
        assertSame(net.minecraft.util.EnumFacing.VALUES[new Random(7).nextInt(6)],
                com.bdmajora.equilibrium.mixin.math.fast_util.EnumFacingMixin.random(fixed));

        var offsets = Mixins.instance(com.bdmajora.equilibrium.mixin.math.fast_blockpos.EnumFacingMixin.class);
        Mixins.call(offsets, "equilibrium$captureOffsets", "UP", 1, 1, 0, -1, "up",
                net.minecraft.util.EnumFacing.AxisDirection.POSITIVE, net.minecraft.util.EnumFacing.Axis.Y,
                new net.minecraft.util.math.Vec3i(0, 1, 0), Mixins.ci());
        assertEquals(0, offsets.getXOffset());
        assertEquals(1, offsets.getYOffset());
        assertEquals(0, offsets.getZOffset());
    }

    @Test
    void theSineTableIsReplacedAtClassInit() {
        float[] vanilla = Statics.get(MathHelper.class, "SIN_TABLE");
        Mixins.set(MathHelperMixin.class, "SIN_TABLE", vanilla);
        Mixins.call(MathHelperMixin.class, "onClassInit", Mixins.ci());
        assertNull(Mixins.get(MathHelperMixin.class, "SIN_TABLE"));
        for (float angle = -3f; angle < 3f; angle += 0.017f) {
            assertEquals(MathHelper.sin(angle), MathHelperMixin.sin(angle));
            assertEquals(MathHelper.cos(angle), MathHelperMixin.cos(angle));
        }
    }

    @Test
    void worldgenCachesIdsAndPoolsScratchArrays() {
        ChunkPrimerMixin primer = Mixins.instance(ChunkPrimerMixin.class);
        Mixins.set(primer, "data", new char[65536]);
        Mixins.set(ChunkPrimerMixin.class, "DEFAULT_STATE", Blocks.AIR.getDefaultState());
        IBlockState stone = Blocks.STONE.getDefaultState();
        primer.setBlockState(1, 2, 3, stone);
        // The second write of the same state reuses the resolved id
        primer.setBlockState(1, 3, 3, stone);
        assertSame(stone, primer.getBlockState(1, 2, 3));
        assertSame(stone, primer.getBlockState(1, 3, 3));
        assertSame(Blocks.AIR.getDefaultState(), primer.getBlockState(0, 0, 0));
        primer.setBlockState(2, 2, 3, Blocks.WATER.getDefaultState());
        assertSame(Blocks.WATER.getDefaultState(), primer.getBlockState(2, 2, 3));
        // An id with no state behind it falls back to the primer's default
        Mixins.<char[]>get(primer, "data")[0] = (char) 60000;
        Mixins.set(primer, "equilibrium$lastGetState", null);
        assertSame(Blocks.AIR.getDefaultState(), primer.getBlockState(0, 0, 0));

        // Copying a primer into a chunk updates the counters without reading the slot back
        var copy = Mixins.instance(com.bdmajora.equilibrium.mixin.worldgen.chunk_copy.ChunkMixin.class);
        ExtendedBlockStorage section = new ExtendedBlockStorage(0, true);
        Mixins.call(copy, "equilibrium$fillFreshSlot", section, 1, 2, 3, stone);
        ExtendedBlockStorageAccessor counts = (ExtendedBlockStorageAccessor) section;
        assertEquals(1, counts.equilibrium$getBlockRefCount());
        assertEquals(0, counts.equilibrium$getTickRefCount());
        assertSame(stone, section.get(1, 2, 3));
        Mixins.call(copy, "equilibrium$fillFreshSlot", section, 1, 3, 3, Blocks.AIR.getDefaultState());
        assertEquals(1, counts.equilibrium$getBlockRefCount());
        Mixins.call(copy, "equilibrium$fillFreshSlot", section, 2, 2, 3, Blocks.SAPLING.getDefaultState());
        assertEquals(2, counts.equilibrium$getBlockRefCount());
        assertEquals(1, counts.equilibrium$getTickRefCount());

        // The biome scratch pool is per thread and reuses arrays exactly as vanilla did
        IntCacheMixin.resetIntCache();
        int[] small = IntCacheMixin.getIntCache(100);
        assertEquals(256, small.length);
        int[] large = IntCacheMixin.getIntCache(1000);
        assertEquals(1000, large.length);
        assertTrue(IntCacheMixin.getCacheSizes().startsWith("cache: 0, tcache: 0, allocated: 1, tallocated: 1"));
        IntCacheMixin.resetIntCache();
        assertSame(small, IntCacheMixin.getIntCache(200));
        assertSame(large, IntCacheMixin.getIntCache(900));
        // A bigger request throws the pool away and starts again at the new size
        int[] bigger = IntCacheMixin.getIntCache(2000);
        assertEquals(2000, bigger.length);
        IntCacheMixin.resetIntCache();
        IntCacheMixin.resetIntCache();
        assertNotSame(bigger, IntCacheMixin.getIntCache(2000));
        assertTrue(IntCacheMixin.getCacheSizes().contains("tallocated"));
    }
}
