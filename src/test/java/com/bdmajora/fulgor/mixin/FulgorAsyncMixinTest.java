package com.bdmajora.fulgor.mixin;

import com.bdmajora.fulgor.Fulgor;
import com.bdmajora.fulgor.FulgorConfig;
import com.bdmajora.fulgor.async.AsyncLitChunk;
import com.bdmajora.fulgor.async.AsyncLitWorld;
import com.bdmajora.fulgor.async.AsyncLightStorage;
import com.bdmajora.fulgor.async.ChunkLightHelper;
import com.bdmajora.fulgor.async.SWMRNibbleArray;
import com.bdmajora.fulgor.async.WorldLightManager;
import com.bdmajora.fulgor.async.engine.LightCachedState;
import com.bdmajora.fulgor.async.engine.LightInfo;
import com.bdmajora.fulgor.mixin.async.block.BlockStateMixin;
import com.bdmajora.fulgor.mixin.async.world.AnvilChunkLoaderMixin;
import com.bdmajora.fulgor.mixin.async.world.ChunkLightingMixin;
import com.bdmajora.fulgor.mixin.async.world.ChunkMixin;
import com.bdmajora.fulgor.mixin.async.world.ChunkSectionMixin;
import com.bdmajora.fulgor.mixin.async.world.PlayerChunkMapEntryMixin;
import com.bdmajora.fulgor.mixin.async.world.WorldEntitySpawnerMixin;
import com.bdmajora.fulgor.mixin.async.world.WorldMixin;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.management.PlayerChunkMapEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
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

class FulgorAsyncMixinTest {
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

    private static WorldServer serverWorld() {
        WorldServer world = mock(WorldServer.class);
        Mixins.set(world, "isRemote", false);
        WorldProvider dimension = mock(WorldProvider.class);
        when(dimension.hasSkyLight()).thenReturn(true);
        Mixins.set(world, "provider", dimension);
        return world;
    }

    @Test
    void theWorldCreatesItsManagerOnFirstUseAndQueuesEveryCheck() {
        WorldMixin world = Mixins.instance(WorldMixin.class);
        World self = (World) (Object) world;
        Mixins.set(self, "isRemote", false);
        WorldProvider dimension = mock(WorldProvider.class);
        when(dimension.hasSkyLight()).thenReturn(true);
        Mixins.set(self, "provider", dimension);

        // Before the world's constructor has returned nothing is queued
        var early = Mixins.<Boolean>cir();
        Mixins.call(world, "fulgor$queueLightUpdate", EnumSkyBlock.BLOCK, BlockPos.ORIGIN, early);
        assertFalse(early.isCancelled());
        Mixins.call(world, "fulgor$markReady", Mixins.ci());
        // Checks a plain World gets no manager.
        assertNull(world.fulgor$getLightManager());
        var refused = Mixins.<Boolean>cir();
        Mixins.call(world, "fulgor$queueLightUpdate", EnumSkyBlock.BLOCK, BlockPos.ORIGIN, refused);
        assertFalse(refused.isCancelled());
        assertNull(world.fulgor$getAnyChunkImmediately(0, 0));
        assertFalse(world.fulgor$hasChunkPendingLight(0, 0));
        world.fulgor$shutdownLightManager();

        // With a manager in place the check is queued and answered as handled
        WorldLightManager manager = mock(WorldLightManager.class);
        Mixins.set(world, "fulgor$lightManager", manager);
        var queued = Mixins.<Boolean>cir();
        Mixins.call(world, "fulgor$queueLightUpdate", EnumSkyBlock.BLOCK, new BlockPos(1, 2, 3), queued);
        assertTrue(queued.getReturnValue());
        Mockito.verify(manager).queueBlockChange(1, 2, 3);
        when(manager.hasChunkPendingLight(0, 0)).thenReturn(true);
        assertTrue(world.fulgor$hasChunkPendingLight(0, 0));
        assertSame(manager, world.fulgor$getLightManager());
        world.fulgor$shutdownLightManager();
        Mockito.verify(manager).shutdown();

        // On the client a chunk whose light is not ready yet refuses the update outright
        Mixins.set(self, "isRemote", true);
        Mixins.set(world, "fulgor$lightManager", manager);
        Chunk chunk = Mc.mock(Chunk.class, AsyncLitChunk.class);
        when(manager.getLoadedChunk(0, 0)).thenReturn(chunk);
        var notReady = Mixins.<Boolean>cir();
        Mixins.call(world, "fulgor$queueLightUpdate", EnumSkyBlock.BLOCK, new BlockPos(1, 2, 3), notReady);
        assertFalse(notReady.getReturnValue());
        when(((AsyncLitChunk) chunk).fulgor$isLightReady()).thenReturn(true);
        var ready = Mixins.<Boolean>cir();
        Mixins.call(world, "fulgor$queueLightUpdate", EnumSkyBlock.BLOCK, new BlockPos(1, 2, 3), ready);
        assertTrue(ready.getReturnValue());
        // A server world always gets one
        WorldMixin server = Mixins.instance(WorldMixin.class);
        Mixins.set(server, "fulgor$ready", true);
        assertTrue((boolean) Mixins.call(WorldMixin.class, "fulgor$isRealWorld", serverWorld()));
    }

    @Test
    void theChunkOwnsItsNibblesAndAnswersLightFromThem() {
        ChunkMixin chunk = Mixins.instance(ChunkMixin.class);
        WorldServer world = serverWorld();
        Mixins.set(chunk, "world", world);
        Mixins.call(chunk, "fulgor$allocateNibbles", world, 0, 0, Mixins.ci());
        assertNotNull(chunk.fulgor$getBlockNibbles());
        assertNotNull(chunk.fulgor$getSkyNibbles());
        assertFalse(chunk.fulgor$isLightReady());
        assertFalse(chunk.fulgor$isLightUsable());
        assertFalse(chunk.fulgor$hasSavedLightValid());

        // Checks vanilla's answer before the light is usable.
        var early = Mixins.<Integer>cir();
        Mixins.call(chunk, "fulgor$getLightFor", EnumSkyBlock.BLOCK, BlockPos.ORIGIN, early);
        assertFalse(early.isCancelled());

        chunk.fulgor$setLightUsable(true);
        assertTrue(chunk.fulgor$isLightUsable());
        SWMRNibbleArray[] block = chunk.fulgor$getBlockNibbles();
        block[5] = new SWMRNibbleArray();
        block[5].set(1, 2, 3, 9);
        block[5].updateVisible();
        var lit = Mixins.<Integer>cir();
        Mixins.call(chunk, "fulgor$getLightFor", EnumSkyBlock.BLOCK, new BlockPos(1, 66, 3), lit);
        assertEquals(9, lit.getReturnValue());
        var sky = Mixins.<Integer>cir();
        Mixins.call(chunk, "fulgor$getLightFor", EnumSkyBlock.SKY, new BlockPos(1, 66, 3), sky);
        assertEquals(15, sky.getReturnValue());
        when(world.provider.hasSkyLight()).thenReturn(false);
        var noSky = Mixins.<Integer>cir();
        Mixins.call(chunk, "fulgor$getLightFor", EnumSkyBlock.SKY, new BlockPos(1, 66, 3), noSky);
        assertEquals(0, noSky.getReturnValue());
        when(world.provider.hasSkyLight()).thenReturn(true);

        // The accessors the engine writes through
        chunk.fulgor$setLightReady(true);
        assertTrue(chunk.fulgor$isLightReady());
        chunk.fulgor$setSavedLightValid(true);
        assertTrue(chunk.fulgor$hasSavedLightValid());
        boolean[] emptiness = new boolean[16];
        chunk.fulgor$setBlockEmptinessMap(emptiness);
        assertSame(emptiness, chunk.fulgor$getBlockEmptinessMap());
        chunk.fulgor$setSkyEmptinessMap(emptiness);
        assertSame(emptiness, chunk.fulgor$getSkyEmptinessMap());
        SWMRNibbleArray[] replacement = ChunkLightHelper.newNullNibbles();
        chunk.fulgor$setBlockNibbles(replacement);
        assertSame(replacement, chunk.fulgor$getBlockNibbles());
        chunk.fulgor$setSkyNibbles(replacement);
        assertSame(replacement, chunk.fulgor$getSkyNibbles());
    }

    @Test
    void loadingAndUnloadingRegisterTheChunkWithTheManager() {
        ChunkMixin chunk = Mixins.instance(ChunkMixin.class);
        Chunk self = (Chunk) (Object) chunk;
        WorldServer world = Mc.mock(WorldServer.class, AsyncLitWorld.class);
        Mixins.set(world, "isRemote", false);
        WorldProvider dimension = mock(WorldProvider.class);
        when(dimension.hasSkyLight()).thenReturn(true);
        Mixins.set(world, "provider", dimension);
        Mixins.set(chunk, "world", world);
        ExtendedBlockStorage[] sections = new ExtendedBlockStorage[16];
        sections[4] = new ExtendedBlockStorage(64, true);
        sections[4].getBlockLight().set(1, 2, 3, 5);
        Mockito.doReturn(sections).when(chunk).getBlockStorageArray();
        Mixins.call(chunk, "fulgor$allocateNibbles", world, 0, 0, Mixins.ci());

        // Without a manager the chunk still imports whatever vanilla light it was given
        Mixins.call(chunk, "fulgor$onLoad", Mixins.ci());
        assertEquals(5, chunk.fulgor$getBlockNibbles()[5].getVisible(1, 2, 3));

        WorldLightManager manager = mock(WorldLightManager.class);
        when(((AsyncLitWorld) world).fulgor$getLightManager()).thenReturn(manager);
        Mixins.call(chunk, "fulgor$onLoad", Mixins.ci());
        Mockito.verify(manager).registerChunk(self);
        Mockito.verify(manager).queueChunkLight(Mockito.anyInt(), Mockito.anyInt(), any(), any());

        // A chunk restored with valid saved light publishes it and asks only for the load-time setup
        chunk.fulgor$setSavedLightValid(true);
        Mixins.call(chunk, "fulgor$onLoad", Mixins.ci());
        Mockito.verify(manager).queueChunkLoadInit(Mockito.anyInt(), Mockito.anyInt(), any(), any());
        assertEquals(5, sections[4].getBlockLight().get(1, 2, 3));

        // A chunk unloading with unfinished light is no longer ready and leaves the queues
        when(manager.finishForUnload(Mockito.anyInt(), Mockito.anyInt())).thenReturn(false);
        chunk.fulgor$setLightReady(true);
        Mixins.call(chunk, "fulgor$onUnload", Mixins.ci());
        assertFalse(chunk.fulgor$isLightReady());
        Mockito.verify(manager).removeChunkFromQueues(Mockito.anyInt(), Mockito.anyInt());
        Mockito.verify(manager).unregisterChunk(Mockito.anyInt(), Mockito.anyInt());

        // Checks the client wraps received arrays.
        Mixins.set(world, "isRemote", true);
        Mixins.call(chunk, "fulgor$onRead", null, 0xFFFF, true, Mixins.ci());
        assertTrue(chunk.fulgor$isLightReady());
        assertSame(sections[4].getBlockLight().getData(), chunk.fulgor$getBlockNibbles()[5].getVisibleData());
        Mixins.call(chunk, "fulgor$onUnload", Mixins.ci());
    }

    @Test
    void aSectionCreatedByABlockPlacementIsFilledFromTheEngine() {
        ChunkSectionMixin chunk = Mixins.instanceWith(ChunkSectionMixin.class, AsyncLitChunk.class);
        WorldServer world = Mc.mock(WorldServer.class, AsyncLitWorld.class);
        Mixins.set(world, "isRemote", false);
        WorldProvider dimension = mock(WorldProvider.class);
        when(dimension.hasSkyLight()).thenReturn(true);
        Mixins.set(world, "provider", dimension);
        Mixins.set(chunk, "world", world);
        Mixins.set(chunk, "x", 0);
        Mixins.set(chunk, "z", 0);
        ExtendedBlockStorage[] sections = new ExtendedBlockStorage[16];
        Mockito.doReturn(sections).when(chunk).getBlockStorageArray();
        AsyncLitChunk lit = (AsyncLitChunk) (Object) chunk;
        SWMRNibbleArray[] block = ChunkLightHelper.newNullNibbles();
        block[5] = new SWMRNibbleArray();
        block[5].set(0, 0, 0, 11);
        block[5].updateVisible();
        Mockito.doReturn(block).when(lit).fulgor$getBlockNibbles();
        Mockito.doReturn(ChunkLightHelper.newNullNibbles()).when(lit).fulgor$getSkyNibbles();
        WorldLightManager manager = mock(WorldLightManager.class);
        when(((AsyncLitWorld) world).fulgor$getLightManager()).thenReturn(manager);

        BlockPos pos = new BlockPos(0, 64, 0);
        com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef wasEmpty = Mc.booleanRef(false);
        Mixins.call(chunk, "fulgor$preSetBlockState", pos, Blocks.STONE.getDefaultState(), Mixins.cir(), wasEmpty);
        assertTrue(wasEmpty.get());
        // Checks a nested placement.
        com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef nested = Mc.booleanRef(false);
        sections[5] = new ExtendedBlockStorage(80, true);
        Mixins.call(chunk, "fulgor$preSetBlockState", new BlockPos(0, 80, 0), Blocks.STONE.getDefaultState(), Mixins.cir(), nested);
        Mixins.call(chunk, "fulgor$postSetBlockState", new BlockPos(0, 80, 0), Blocks.STONE.getDefaultState(),
                Mixins.cir(Blocks.AIR.getDefaultState()), nested);
        assertFalse(nested.get());
        // The section vanilla created is filled from what the engine already knows
        sections[4] = new ExtendedBlockStorage(64, true);
        Mixins.call(chunk, "fulgor$postSetBlockState", pos, Blocks.STONE.getDefaultState(),
                Mixins.cir(Blocks.AIR.getDefaultState()), wasEmpty);
        assertEquals(11, sections[4].getBlockLight().get(0, 0, 0));
        Mockito.verify(manager).queueSectionChange(0, 4, 0, false);

        // Checks the cases that do nothing.
        com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef again = Mc.booleanRef(false);
        Mixins.call(chunk, "fulgor$preSetBlockState", pos, Blocks.STONE.getDefaultState(), Mixins.cir(), again);
        Mixins.call(chunk, "fulgor$postSetBlockState", pos, Blocks.STONE.getDefaultState(), Mixins.cir(null), again);
        com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef outside = Mc.booleanRef(false);
        Mixins.call(chunk, "fulgor$preSetBlockState", new BlockPos(0, 300, 0), Blocks.STONE.getDefaultState(), Mixins.cir(), outside);
        Mixins.call(chunk, "fulgor$postSetBlockState", new BlockPos(0, 300, 0), Blocks.STONE.getDefaultState(),
                Mixins.cir(Blocks.AIR.getDefaultState()), outside);
        // Checks a refused write.
        com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef refused = Mc.booleanRef(true);
        Mixins.call(chunk, "fulgor$postSetBlockState", new BlockPos(0, 100, 0), Blocks.STONE.getDefaultState(), Mixins.cir(null), refused);
        // Checks air placed into nothing.
        Mixins.call(chunk, "fulgor$postSetBlockState", new BlockPos(0, 100, 0), Blocks.AIR.getDefaultState(),
                Mixins.cir(Blocks.AIR.getDefaultState()), refused);
        // A refused write outside the world
        Mixins.call(chunk, "fulgor$postSetBlockState", new BlockPos(0, -20, 0), Blocks.STONE.getDefaultState(),
                Mixins.cir(Blocks.AIR.getDefaultState()), refused);
        Mockito.verify(manager, Mockito.times(1)).queueSectionChange(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt(), Mockito.anyBoolean());
    }

    @Test
    @SuppressWarnings("deprecation")
    void everyBlockStateMemoisesItsLightInfo() {
        BlockStateMixin state = Mixins.instance(BlockStateMixin.class);
        // Reads the mixin as the state.
        net.minecraft.block.state.IBlockState self = (net.minecraft.block.state.IBlockState) (Object) state;
        Mockito.doReturn(Blocks.GLOWSTONE).when(self).getBlock();
        Mockito.doReturn(0).when(self).getLightOpacity();
        Mockito.doReturn(15).when(self).getLightValue();
        int info = ((LightCachedState) state).fulgor$lightInfo();
        assertTrue((info & LightInfo.COMPUTED) != 0);
        assertEquals(15, LightInfo.emission(info));
        assertEquals(0, LightInfo.opacity(info));
        // The second read is the memoised one
        assertEquals(info, ((LightCachedState) state).fulgor$lightInfo());
    }

    @Test
    void chunksAreHeldBackFromClientsAndSpawningUntilTheirLightIsFinal() {
        PlayerChunkMapEntryMixin entry = Mixins.instance(PlayerChunkMapEntryMixin.class);
        Chunk chunk = Mc.mock(Chunk.class, AsyncLitChunk.class);
        Mixins.set(chunk, "x", 0);
        Mixins.set(chunk, "z", 0);
        WorldServer world = Mc.mock(WorldServer.class, AsyncLitWorld.class);
        when(chunk.getWorld()).thenReturn(world);
        Mixins.set(entry, "chunk", chunk);

        var notReady = Mixins.<Boolean>cir();
        Mixins.call(entry, "fulgor$gateSendOnLight", notReady);
        assertFalse(notReady.getReturnValue());

        when(((AsyncLitChunk) chunk).fulgor$isLightReady()).thenReturn(true);
        WorldLightManager manager = mock(WorldLightManager.class);
        when(((AsyncLitWorld) world).fulgor$getLightManager()).thenReturn(manager);
        var neighboursPending = Mixins.<Boolean>cir();
        Mixins.call(entry, "fulgor$gateSendOnLight", neighboursPending);
        assertFalse(neighboursPending.getReturnValue());
        when(manager.areNeighboursLightReady(0, 0)).thenReturn(true);
        var ready = Mixins.<Boolean>cir();
        Mixins.call(entry, "fulgor$gateSendOnLight", ready);
        assertFalse(ready.isCancelled());

        // Checks the skipped gates.
        Mixins.set(entry, "sentToPlayers", true);
        Mixins.call(entry, "fulgor$gateSendOnLight", Mixins.<Boolean>cir());
        Mixins.set(entry, "sentToPlayers", false);
        Mixins.set(entry, "chunk", null);
        Mixins.call(entry, "fulgor$gateSendOnLight", Mixins.<Boolean>cir());
        FulgorConfig.get().asyncSendChunksWithoutLight = true;
        Mixins.call(entry, "fulgor$gateSendOnLight", Mixins.<Boolean>cir());

        // Spawning skips a chunk whose light is still pending
        WorldEntitySpawnerMixin spawner = Mixins.instance(WorldEntitySpawnerMixin.class);
        PlayerChunkMapEntry mapEntry = mock(PlayerChunkMapEntry.class);
        when(mapEntry.getPos()).thenReturn(new ChunkPos(2, 3));
        Operation<Boolean> sent = args -> true;
        Operation<Boolean> notSent = args -> false;
        when(((AsyncLitWorld) world).fulgor$hasChunkPendingLight(2, 3)).thenReturn(true);
        assertFalse((boolean) Mixins.call(spawner, "fulgor$skipPendingLightChunks", mapEntry, sent, world));
        assertFalse((boolean) Mixins.call(spawner, "fulgor$skipPendingLightChunks", mapEntry, notSent, world));
        when(((AsyncLitWorld) world).fulgor$hasChunkPendingLight(2, 3)).thenReturn(false);
        assertTrue((boolean) Mixins.call(spawner, "fulgor$skipPendingLightChunks", mapEntry, sent, world));
    }

    @Test
    void theChunkLoaderCarriesTheEnginesLightThroughNbt() {
        AnvilChunkLoaderMixin loader = Mixins.instance(AnvilChunkLoaderMixin.class);
        WorldServer world = Mc.mock(WorldServer.class, AsyncLitWorld.class);
        Chunk chunk = Mc.mock(Chunk.class, AsyncLitChunk.class);
        Mixins.set(chunk, "x", 0);
        Mixins.set(chunk, "z", 0);
        when(((AsyncLitChunk) chunk).fulgor$getBlockNibbles()).thenReturn(ChunkLightHelper.newNullNibbles());
        when(((AsyncLitChunk) chunk).fulgor$getSkyNibbles()).thenReturn(ChunkLightHelper.newNullNibbles());
        NBTTagCompound compound = new NBTTagCompound();
        Mixins.call(loader, "fulgor$writeLight", chunk, world, compound, Mixins.ci());
        Mixins.call(loader, "fulgor$readLight", world, compound, Mixins.cir(chunk));
        Mixins.call(loader, "fulgor$readLight", world, compound, Mixins.cir(null));
    }

    @Test
    void theHeightmapIsRebuiltWithoutTouchingTheEnginesLight() {
        ChunkLightingMixin chunk = Mixins.instanceWith(ChunkLightingMixin.class, AsyncLitChunk.class);
        WorldServer world = serverWorld();
        Mixins.set(chunk, "world", world);
        Mixins.set(chunk, "x", 0);
        Mixins.set(chunk, "z", 0);
        Mixins.set(chunk, "heightMap", new int[256]);
        Mixins.set(chunk, "precipitationHeightMap", new int[256]);
        ExtendedBlockStorage[] sections = new ExtendedBlockStorage[16];
        sections[4] = new ExtendedBlockStorage(64, true);
        Mockito.doReturn(sections).when(chunk).getBlockStorageArray();
        Mockito.doReturn(64).when(chunk).getTopFilledSegment();
        Mockito.doReturn(Blocks.AIR.getDefaultState()).when(chunk).getBlockState(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt());
        Mockito.doReturn(Blocks.STONE.getDefaultState()).when(chunk).getBlockState(0, 64, 0);

        var ci = Mixins.ci();
        Mixins.call(chunk, "fulgor$generateSkylightMap", ci);
        assertTrue(ci.isCancelled());
        assertEquals(65, Mixins.<int[]>get(chunk, "heightMap")[0]);
        assertEquals(0, Mixins.<int[]>get(chunk, "heightMap")[1]);
        // Only a column with something opaque in it contributes to the minimum
        assertEquals(65, Mixins.<Integer>get(chunk, "heightMapMinimum"));

        // Relighting one column only moves that column's height
        Mockito.doReturn(Blocks.STONE.getDefaultState()).when(chunk).getBlockState(1, 70, 0);
        Mixins.call(chunk, "fulgor$relightBlock", 1, 71, 0, Mixins.ci());
        assertEquals(71, Mixins.<int[]>get(chunk, "heightMap")[1]);
        Mockito.verify(world).markBlocksDirtyVertical(1, 0, 71, 0);
        // Nothing changes when the height is already right
        Mixins.call(chunk, "fulgor$relightBlock", 1, 71, 0, Mixins.ci());
        Mockito.verify(world, Mockito.times(1)).markBlocksDirtyVertical(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt());
    }
}
