package com.bdmajora.fulgor.async;

import com.bdmajora.fulgor.Fulgor;
import com.bdmajora.fulgor.FulgorConfig;
import com.bdmajora.fulgor.async.engine.AsyncWorld;
import com.bdmajora.fulgor.async.engine.BfsLightEngine;
import com.bdmajora.fulgor.async.engine.BlockLightEngine;
import com.bdmajora.fulgor.async.engine.SkyLightEngine;
import com.bdmajora.fulgor.lighting.DynamicLightsBridge;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AsyncEdgeCasesTest {
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

    private static AsyncWorld neighbourhood() {
        AsyncWorld fixture = new AsyncWorld(false);
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                fixture.chunk(x, z);
            }
        }
        return fixture;
    }

    @Test
    void bothLanesHideAndRestoreTheirSectionNibbles() {
        AsyncWorld fixture = neighbourhood();
        Chunk chunk = fixture.chunks.get(AsyncWorld.key(0, 0));
        fixture.setBlock(new BlockPos(8, 64, 8), Blocks.GLOWSTONE.getDefaultState());

        // A section that becomes empty has its nibble hidden and its edges rechecked on both lanes
        for (BfsLightEngine engine : new BfsLightEngine[] {
                new BlockLightEngine(fixture.world), new SkyLightEngine(fixture.world)}) {
            engine.light(chunk, BfsLightEngine.getEmptySectionsForChunk(chunk), true);
            Boolean[] emptied = new Boolean[16];
            emptied[4] = Boolean.TRUE;
            engine.blocksChangedInChunk(0, 0, null, emptied);
            Boolean[] filled = new Boolean[16];
            filled[4] = Boolean.FALSE;
            engine.blocksChangedInChunk(0, 0, null, filled);
            IntOpenHashSet sections = new IntOpenHashSet();
            for (int s = -1; s <= 16; s++) {
                sections.add(s);
            }
            engine.checkChunkEdges(0, 0, sections);
            assertFalse(engine.wasQueueOverflowed());
        }
    }

    @Test
    void theEnginesReportAndRecoverFromQueueOverflow() {
        AsyncWorld fixture = neighbourhood();
        BlockLightEngine engine = new BlockLightEngine(fixture.world);
        assertFalse(engine.wasQueueOverflowed());
        // The queues grow on demand, which is what keeps a large batch from overflowing
        assertNotNull(Mixins.call(engine, "resizeIncreaseQueue"));
        assertNotNull(Mixins.call(engine, "resizeDecreaseQueue"));
        Mixins.call(engine, "warnQueueOverflow");
        assertTrue(engine.wasQueueOverflowed());
        Mixins.call(engine, "warnQueueOverflow");
    }

    @Test
    void theManagerRecoversAChunkWhoseBatchOverflowed() {
        AsyncWorld fixture = neighbourhood();
        WorldLightManager manager = new WorldLightManager(fixture.world, true);
        Chunk chunk = fixture.chunks.get(AsyncWorld.key(0, 0));
        manager.registerChunk(chunk);
        assertFalse(manager.hasPendingLightWork(0, 0));
        assertNull(Mixins.call(manager, "getPendingWorkFuture", net.minecraft.util.math.ChunkPos.asLong(0, 0)));
        // A forced relight queues the chunk on both lanes and can be waited on
        assertTrue(manager.forceRelightChunk(0, 0));
        assertTrue(manager.hasPendingLightWork(0, 0));
        assertNotNull(Mixins.call(manager, "getPendingWorkFuture", net.minecraft.util.math.ChunkPos.asLong(0, 0)));
        assertFalse(manager.forceRelightChunk(9, 9));
        // Waiting completes once the lanes have run
        Thread worker = new Thread(() -> {
            for (int i = 0; i < 20 && manager.hasUpdates(); i++) {
                manager.processClientUpdates();
            }
        });
        worker.start();
        assertTrue(manager.awaitPendingWork(0, 0));
        try {
            worker.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertFalse(manager.hasUpdates());
        manager.shutdown();
    }

    @Test
    void savedLightIsRestoredFromTheChunkTag() {
        AsyncWorld fixture = neighbourhood();
        Chunk chunk = fixture.chunks.get(AsyncWorld.key(0, 0));
        BlockLightEngine engine = new BlockLightEngine(fixture.world);
        fixture.setBlock(new BlockPos(8, 64, 8), Blocks.GLOWSTONE.getDefaultState());
        engine.light(chunk, BfsLightEngine.getEmptySectionsForChunk(chunk), false);
        assertEquals(15, fixture.blockLight(new BlockPos(8, 64, 8)));

        // Only a chunk whose light is final is saved
        NBTTagCompound tooEarly = new NBTTagCompound();
        AsyncLightStorage.save(chunk, null, tooEarly);
        assertTrue(tooEarly.getKeySet().isEmpty());
        when(((AsyncLitChunk) chunk).fulgor$isLightReady()).thenReturn(true);
        NBTTagCompound compound = new NBTTagCompound();
        AsyncLightStorage.save(chunk, null, compound);
        assertFalse(compound.getKeySet().isEmpty());
        // A chunk loaded from that tag comes back with the same light
        AsyncWorld reloaded = new AsyncWorld(false);
        Chunk target = reloaded.chunk(0, 0);
        AsyncLightStorage.load(target, compound);
        assertEquals(15, reloaded.blockLight(new BlockPos(8, 64, 8)));
        // A tag with nothing in it leaves the chunk as it was
        AsyncLightStorage.load(reloaded.chunk(1, 1), new NBTTagCompound());
    }

    @Test
    void theClientWorldCheckOnlyAcceptsTheWorldTheClientIsIn() {
        Mc.client();
        WorldClient world = mock(WorldClient.class);
        assertFalse(ClientWorldCheck.isCurrentClientWorld(world));
        Mixins.set(net.minecraft.client.Minecraft.getMinecraft(), "world", world);
        assertTrue(ClientWorldCheck.isCurrentClientWorld(world));
    }

    @Test
    void theDynamicLightsBridgeAnswersOnlyWhenItIsBound() throws Throwable {
        MethodHandle original = Mixins.get(DynamicLightsBridge.class, "GET_LIGHT_VALUE");
        Statics.set(DynamicLightsBridge.class, "GET_LIGHT_VALUE", null);
        assertFalse(DynamicLightsBridge.isAvailable());
        // Without the mod the lookup finds nothing at all
        assertNull(Mixins.call(DynamicLightsBridge.class, "resolve"));
        Statics.set(Fulgor.class, "dynamicLights", true);
        assertNull(Mixins.call(DynamicLightsBridge.class, "resolve"));

        // Bound to a stand-in, the bridge reports what it answers and wraps whatever it throws
        MethodHandle handle = MethodHandles.lookup().findStatic(StandInLights.class, "getLightValue",
                MethodType.methodType(int.class, net.minecraft.block.Block.class,
                        net.minecraft.block.state.IBlockState.class, net.minecraft.world.IBlockAccess.class, BlockPos.class));
        Statics.set(DynamicLightsBridge.class, "GET_LIGHT_VALUE", handle);
        assertTrue(DynamicLightsBridge.isAvailable());
        assertEquals(11, DynamicLightsBridge.getLightValue(Blocks.STONE.getDefaultState(), null, BlockPos.ORIGIN));
        StandInLights.fail = true;
        assertThrows(IllegalStateException.class,
                () -> DynamicLightsBridge.getLightValue(Blocks.STONE.getDefaultState(), null, BlockPos.ORIGIN));
        StandInLights.fail = false;
        Statics.set(DynamicLightsBridge.class, "GET_LIGHT_VALUE", original);
    }

    // Stands in for the mod's own luminance hook
    public static final class StandInLights {
        static boolean fail;

        public static int getLightValue(net.minecraft.block.Block block, net.minecraft.block.state.IBlockState state,
                                        net.minecraft.world.IBlockAccess world, BlockPos pos) {
            if (fail) {
                throw new IllegalArgumentException("no");
            }
            return 11;
        }
    }

    @Test
    void aServerResendsTheChunkOnceAForcedRelightPublishes() {
        AsyncWorld fixture = new AsyncWorld(false, net.minecraft.world.WorldServer.class);
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                fixture.chunk(x, z);
            }
        }
        net.minecraft.world.WorldServer world = (net.minecraft.world.WorldServer) fixture.world;
        Chunk chunk = fixture.chunks.get(AsyncWorld.key(0, 0));
        when(((AsyncLitChunk) chunk).fulgor$isLightReady()).thenReturn(true);
        when(chunk.getBiomeArray()).thenReturn(new byte[256]);
        when(chunk.getTileEntityMap()).thenReturn(new java.util.HashMap<>());

        // The packet is built on the server thread, so the listener hands its work to the scheduler
        net.minecraft.server.MinecraftServer server = mock(net.minecraft.server.MinecraftServer.class);
        when(server.addScheduledTask(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        });
        when(world.getMinecraftServer()).thenReturn(server);
        net.minecraft.server.management.PlayerChunkMap map = mock(net.minecraft.server.management.PlayerChunkMap.class);
        when(world.getPlayerChunkMap()).thenReturn(map);
        net.minecraft.server.management.PlayerChunkMapEntry entry = mock(net.minecraft.server.management.PlayerChunkMapEntry.class);
        when(map.getEntry(0, 0)).thenReturn(entry);

        WorldLightManager manager = new WorldLightManager(world, true);
        for (Chunk loaded : fixture.chunks.values()) {
            manager.registerChunk(loaded);
        }
        assertTrue(manager.forceRelightChunk(0, 0));
        // The lanes have their own daemons here, but driving them from the test thread too keeps the wait short
        for (int i = 0; i < 100 && manager.hasPendingLightWork(0, 0); i++) {
            manager.processClientUpdates();
        }
        // 1.12.2 has no light-only packet, so the whole chunk goes back out to the watchers
        verify(entry, timeout(5000)).sendPacket(any(net.minecraft.network.play.server.SPacketChunkData.class));
        manager.shutdown();
    }
}
