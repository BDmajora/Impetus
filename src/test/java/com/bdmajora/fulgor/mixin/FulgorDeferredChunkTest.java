package com.bdmajora.fulgor.mixin;

import com.bdmajora.fulgor.Fulgor;
import com.bdmajora.fulgor.FulgorConfig;
import com.bdmajora.fulgor.api.ChunkLightingData;
import com.bdmajora.fulgor.api.LightingEngineProvider;
import com.bdmajora.fulgor.lighting.LightingEngine;
import com.bdmajora.fulgor.lighting.LightingHooks;
import com.bdmajora.fulgor.lighting.NeighborLightFlags;
import com.bdmajora.fulgor.lighting.WorldChunkSlice;
import com.bdmajora.fulgor.mixin.world.ChunkMixin;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import net.minecraft.init.Blocks;
import net.minecraft.profiler.Profiler;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FulgorDeferredChunkTest {
    @TempDir
    Path dir;

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
        Mc.forge();
    }

    @BeforeEach
    void freshConfig() {
        Statics.set(net.minecraft.launchwrapper.Launch.class, "minecraftHome", dir.toFile());
        Mixins.set(FulgorConfig.class, "instance", null);
        Statics.set(Fulgor.class, "dynamicLights", false);
        Statics.set(Fulgor.class, "fluidloggedApi", false);
    }

    // A world whose chunk provider answers from a small map, and which records the light checks it is given
    private static World world(IChunkProvider provider) {
        World world = Mc.mock(World.class, LightingEngineProvider.class);
        Mixins.set(world, "isRemote", false);
        Mixins.set(world, "profiler", new Profiler());
        WorldProvider dimension = mock(WorldProvider.class);
        when(dimension.hasSkyLight()).thenReturn(true);
        Mixins.set(world, "provider", dimension);
        when(world.getChunkProvider()).thenReturn(provider);
        when(((LightingEngineProvider) world).fulgor$getLightingEngine()).thenReturn(mock(LightingEngine.class));
        return world;
    }

    private static ChunkMixin chunk(World world, int x, int z) {
        ChunkMixin chunk = Mixins.instance(ChunkMixin.class);
        Mixins.set(chunk, "world", world);
        Mixins.set(chunk, "x", x);
        Mixins.set(chunk, "z", z);
        Mixins.set(chunk, "heightMap", new int[256]);
        Mixins.set(chunk, "updateSkylightColumns", new boolean[256]);
        ExtendedBlockStorage[] sections = new ExtendedBlockStorage[16];
        sections[4] = new ExtendedBlockStorage(64, true);
        Mixins.set(chunk, "storageArrays", sections);
        // The hooks read the sections through the chunk's own accessor
        Mockito.doReturn(sections).when((Chunk) (Object) chunk).getBlockStorageArray();
        return chunk;
    }

    @Test
    void theChunkAsksTheEngineForEveryLightRead() {
        IChunkProvider provider = mock(IChunkProvider.class);
        World world = world(provider);
        ChunkMixin chunk = chunk(world, 0, 0);
        LightingEngine engine = ((LightingEngineProvider) world).fulgor$getLightingEngine();
        Mixins.call(chunk, "fulgor$captureLightingEngine", world, 0, 0, Mixins.ci());
        assertSame(engine, chunk.fulgor$getLightingEngine());

        Mixins.call(chunk, "fulgor$flushBeforeLightSubtracted", BlockPos.ORIGIN, 0, Mixins.cir());
        Mockito.verify(engine).processLightUpdates();

        // Reading light flushes that type's queue and then answers from the section
        ExtendedBlockStorage section = Mixins.<ExtendedBlockStorage[]>get(chunk, "storageArrays")[4];
        section.setBlockLight(1, 2, 3, 6);
        section.setSkyLight(1, 2, 3, 9);
        BlockPos pos = new BlockPos(1, 66, 3);
        assertEquals(6, chunk.getLightFor(EnumSkyBlock.BLOCK, pos));
        assertEquals(9, chunk.getLightFor(EnumSkyBlock.SKY, pos));
        Mockito.verify(engine).processLightUpdatesForType(EnumSkyBlock.BLOCK);
        // Without a section the answer depends on whether the column sees the sky
        Mockito.doReturn(true).when(chunk).canSeeSky(any());
        assertEquals(15, chunk.fulgor$getCachedLightFor(EnumSkyBlock.SKY, new BlockPos(1, 100, 3)));
        Mockito.doReturn(false).when(chunk).canSeeSky(any());
        assertEquals(0, chunk.fulgor$getCachedLightFor(EnumSkyBlock.SKY, new BlockPos(1, 100, 3)));
        when(world.provider.hasSkyLight()).thenReturn(false);
        assertEquals(0, chunk.fulgor$getCachedLightFor(EnumSkyBlock.SKY, pos));
        when(world.provider.hasSkyLight()).thenReturn(true);
    }

    @Test
    void theChunkCarriesFulgorsOwnLightingState() {
        ChunkMixin chunk = chunk(world(mock(IChunkProvider.class)), 0, 0);
        assertNull(chunk.fulgor$getNeighborLightChecks());
        chunk.fulgor$initNeighborLightChecks();
        assertEquals(Fulgor.BOUNDARY_FLAG_COUNT, chunk.fulgor$getNeighborLightChecks().length);
        short[] existing = chunk.fulgor$getNeighborLightChecks();
        chunk.fulgor$initNeighborLightChecks();
        assertSame(existing, chunk.fulgor$getNeighborLightChecks());
        short[] replacement = new short[Fulgor.BOUNDARY_FLAG_COUNT];
        chunk.fulgor$setNeighborLightChecks(replacement);
        assertSame(replacement, chunk.fulgor$getNeighborLightChecks());
        assertFalse(chunk.fulgor$isLightInitialized());
        chunk.fulgor$setLightInitialized(true);
        assertTrue(chunk.fulgor$isLightInitialized());
        chunk.fulgor$setSkylightUpdated();
        Mixins.verifyCall(chunk, "setSkylightUpdated");
    }

    @Test
    void placingLightHighInAColumnRelightsWhatIsBelow() {
        IChunkProvider provider = mock(IChunkProvider.class);
        World world = world(provider);
        ChunkMixin chunk = chunk(world, 0, 0);
        // Opaque only at y=69 in that column, so the height settles just above it
        Mixins.stub(chunk, "getBlockLightOpacity", invocation ->
                (int) invocation.getArgument(1) == 69 ? 255 : 0);
        // The column's height moves to just above the opaque block, and the sky column is rechecked
        Mixins.call(chunk, "relightBlock", 1, 70, 2);
        assertEquals(70, Mixins.<int[]>get(chunk, "heightMap")[2 << 4 | 1]);
        Mockito.verify(world, Mockito.atLeastOnce()).checkLightFor(Mockito.eq(EnumSkyBlock.SKY), any());
        // Nothing changes when the height is already right
        Mixins.call(chunk, "relightBlock", 1, 70, 2);
        assertEquals(70, Mixins.<int[]>get(chunk, "heightMap")[2 << 4 | 1]);
    }

    @Test
    void gapsAreRecheckedAgainstTheNeighbourhood() {
        IChunkProvider provider = mock(IChunkProvider.class);
        World world = world(provider);
        ChunkMixin chunk = chunk(world, 0, 0);
        Chunk self = (Chunk) (Object) chunk;
        when(provider.getLoadedChunk(Mockito.anyInt(), Mockito.anyInt())).thenReturn(self);
        Mockito.doReturn(70).when(chunk).getHeightValue(Mockito.anyInt(), Mockito.anyInt());
        Mockito.doReturn(60).when(self).getLowestHeight();
        Mockito.doReturn(64).when(self).getHeightValue(Mockito.anyInt(), Mockito.anyInt());
        when(world.isAreaLoaded(any(BlockPos.class), Mockito.anyInt())).thenReturn(true);

        // With no column flagged there is nothing to do
        Mixins.call(chunk, "recheckGaps", false);
        assertFalse(Mixins.<Boolean>get(chunk, "isGapLightingUpdated"));
        // A flagged column schedules the checks that close the gap
        Mixins.<boolean[]>get(chunk, "updateSkylightColumns")[0] = true;
        Mixins.call(chunk, "recheckGaps", true);
        Mockito.verify(world, Mockito.atLeastOnce()).checkLightFor(Mockito.eq(EnumSkyBlock.SKY), any());
        assertFalse(Mixins.<boolean[]>get(chunk, "updateSkylightColumns")[0]);
        // An unloaded neighbourhood is left for later
        when(world.isAreaLoaded(any(BlockPos.class), Mockito.anyInt())).thenReturn(false);
        Mixins.call(chunk, "recheckGaps", false);
    }

    @Test
    void chunkLightingIsInitialisedOnceItsNeighboursAre() {
        IChunkProvider provider = mock(IChunkProvider.class);
        World world = world(provider);
        ChunkMixin mixin = chunk(world, 0, 0);
        Chunk chunk = (Chunk) (Object) mixin;
        when(world.isAreaLoaded(any(BlockPos.class), any(BlockPos.class), Mockito.anyBoolean())).thenReturn(true);
        Mixins.<ExtendedBlockStorage[]>get(mixin, "storageArrays")[4]
                .set(1, 2, 3, Blocks.GLOWSTONE.getDefaultState());

        // Every emitter in the chunk is handed to the engine, and the chunk is marked initialised
        LightingHooks.initChunkLighting(world, chunk);
        assertTrue(mixin.fulgor$isLightInitialized());
        Mockito.verify(world, Mockito.atLeastOnce()).checkLightFor(Mockito.eq(EnumSkyBlock.BLOCK), any());
        // An unloaded neighbourhood means the pass is skipped entirely
        ChunkMixin later = chunk(world, 5, 5);
        when(world.isAreaLoaded(any(BlockPos.class), any(BlockPos.class), Mockito.anyBoolean())).thenReturn(false);
        LightingHooks.initChunkLighting(world, (Chunk) (Object) later);
        assertFalse(later.fulgor$isLightInitialized());

        // checkLight only declares the chunk lit once all eight neighbours are
        when(world.isAreaLoaded(any(BlockPos.class), any(BlockPos.class), Mockito.anyBoolean())).thenReturn(true);
        LightingHooks.checkChunkLighting(world, chunk);
        Mockito.verify(chunk, Mockito.never()).setLightPopulated(true);
        when(provider.getLoadedChunk(Mockito.anyInt(), Mockito.anyInt())).thenReturn(chunk);
        LightingHooks.checkChunkLighting(world, chunk);
        Mockito.verify(chunk).setLightPopulated(true);
        Mixins.call(mixin, "checkLight");
        assertTrue(Mixins.<Boolean>get(mixin, "isTerrainPopulated"));
    }

    @Test
    void boundaryChecksAreRecordedAndReplayed() {
        IChunkProvider provider = mock(IChunkProvider.class);
        World world = world(provider);
        ChunkMixin mixin = chunk(world, 0, 0);
        Chunk chunk = (Chunk) (Object) mixin;
        // A column relit next to an unloaded chunk records the check for when that chunk arrives
        Mixins.<ExtendedBlockStorage[]>get(mixin, "storageArrays")[5] = Chunk.NULL_BLOCK_STORAGE;
        LightingHooks.relightSkylightColumn(world, chunk, 15, 8, 80, 96);
        assertNotNull(mixin.fulgor$getNeighborLightChecks());

        // With the neighbour present the checks are scheduled directly instead
        ChunkMixin neighbourMixin = chunk(world, 1, 0);
        Chunk neighbour = (Chunk) (Object) neighbourMixin;
        when(provider.getLoadedChunk(1, 0)).thenReturn(neighbour);
        LightingHooks.relightSkylightColumn(world, chunk, 15, 8, 80, 96);

        // Loading replays whatever both sides recorded
        Mixins.call(mixin, "fulgor$replayBoundaryChecks", Mixins.ci());
        LightingHooks.scheduleRelightChecksForChunkBoundaries(world, chunk);
        Mockito.verify(world, Mockito.atLeastOnce()).checkLightFor(any(), any());

        // A fresh section is seeded with full skylight above the heightmap
        ExtendedBlockStorage section = new ExtendedBlockStorage(64, true);
        Mockito.doReturn(0).when(mixin).getHeightValue(Mockito.anyInt(), Mockito.anyInt());
        LightingHooks.initSkylightForSection(world, chunk, section);
        assertEquals(15, section.getSkyLight(0, 0, 0));
        // Below the heightmap nothing is seeded, and a dimension without sky is skipped
        ExtendedBlockStorage shaded = new ExtendedBlockStorage(64, true);
        Mockito.doReturn(200).when(mixin).getHeightValue(Mockito.anyInt(), Mockito.anyInt());
        LightingHooks.initSkylightForSection(world, chunk, shaded);
        assertEquals(0, shaded.getSkyLight(0, 0, 0));
        when(world.provider.hasSkyLight()).thenReturn(false);
        LightingHooks.initSkylightForSection(world, chunk, section);
        Mixins.call(mixin, "fulgor$seedNewSectionOnly", chunk, EnumSkyBlock.SKY, new BlockPos(0, 64, 0), 0);
        when(world.provider.hasSkyLight()).thenReturn(true);

        // An area relight schedules every position in the box
        LightingHooks.scheduleRelightChecksForArea(world, EnumSkyBlock.BLOCK, 0, 0, 0, 1, 1, 1);
        Mockito.verify(world, Mockito.atLeastOnce()).checkLightFor(Mockito.eq(EnumSkyBlock.BLOCK), any());
    }
}
