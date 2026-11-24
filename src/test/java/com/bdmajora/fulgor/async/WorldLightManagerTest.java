package com.bdmajora.fulgor.async;

import com.bdmajora.fulgor.Fulgor;
import com.bdmajora.fulgor.FulgorConfig;
import com.bdmajora.fulgor.async.engine.AsyncWorld;
import com.bdmajora.fulgor.async.engine.BfsLightEngine;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class WorldLightManagerTest {
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

    // Returns a client side neighbourhood.
    private static AsyncWorld neighbourhood() {
        AsyncWorld fixture = new AsyncWorld(true);
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                fixture.chunk(x, z);
            }
        }
        return fixture;
    }

    // Runs the client tick until the lanes are idle.
    private static void drain(WorldLightManager manager) {
        for (int i = 0; i < 20 && manager.hasUpdates(); i++) {
            manager.processClientUpdates();
        }
    }

    private static WorldLightManager manager(AsyncWorld fixture, boolean hasSkyLight) {
        WorldLightManager manager = new WorldLightManager(fixture.world, hasSkyLight);
        for (Chunk chunk : fixture.chunks.values()) {
            manager.registerChunk(chunk);
        }
        return manager;
    }

    @Test
    void registeredChunksAreTheOnesTheLanesMayTouch() {
        AsyncWorld fixture = neighbourhood();
        WorldLightManager manager = manager(fixture, true);
        assertSame(fixture.chunks.get(AsyncWorld.key(0, 0)), manager.getLoadedChunk(0, 0));
        assertNull(manager.getLoadedChunk(9, 9));
        manager.unregisterChunk(0, 0);
        assertNull(manager.getLoadedChunk(0, 0));

        // Neighbour readiness asks each of the four sides
        WorldLightManager ready = manager(neighbourhood(), true);
        assertFalse(ready.areNeighboursLightReady(0, 0));
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                Chunk chunk = ready.getLoadedChunk(i, j);
                when(((AsyncLitChunk) chunk).fulgor$isLightReady()).thenReturn(true);
            }
        }
        assertTrue(ready.areNeighboursLightReady(0, 0));
        assertFalse(ready.areNeighboursLightReady(5, 5));
    }

    @Test
    void aBlockChangeGoesToBothLanesAndIsWorkedOffByTheTick() {
        AsyncWorld fixture = neighbourhood();
        WorldLightManager manager = manager(fixture, true);
        Chunk chunk = fixture.chunks.get(AsyncWorld.key(0, 0));
        manager.queueChunkLight(0, 0, chunk, BfsLightEngine.getEmptySectionsForChunk(chunk));
        assertTrue(manager.hasUpdates());
        assertTrue(manager.hasChunkPendingLight(0, 0));
        drain(manager);
        assertFalse(manager.hasUpdates());

        BlockPos source = new BlockPos(8, 64, 8);
        fixture.setBlock(source, Blocks.GLOWSTONE.getDefaultState());
        manager.queueBlockChange(source.getX(), source.getY(), source.getZ());
        assertTrue(manager.hasUpdates());
        drain(manager);
        assertEquals(15, fixture.blockLight(source));
        assertFalse(manager.hasChunkPendingLight(0, 0));

        // A section that emptied is queued to both lanes as well
        manager.queueSectionChange(0, 4, 0, true);
        drain(manager);
        // Queues a chunk restored with saved light.
        manager.queueChunkLoadInit(0, 0, chunk, BfsLightEngine.getEmptySectionsForChunk(chunk));
        drain(manager);
        assertFalse(manager.hasUpdates());
    }

    @Test
    void aBulkSkyChangeIsPromotedToAWholeChunkRelight() {
        AsyncWorld fixture = neighbourhood();
        WorldLightManager manager = manager(fixture, true);
        Chunk chunk = fixture.chunks.get(AsyncWorld.key(0, 0));
        manager.queueChunkLight(0, 0, chunk, BfsLightEngine.getEmptySectionsForChunk(chunk));
        drain(manager);
        // Sixteen or more changed positions in one chunk relight the whole thing on the sky lane
        for (int i = 0; i < 20; i++) {
            fixture.setBlock(new BlockPos(i % 16, 64, i / 16), Blocks.STONE.getDefaultState());
            manager.queueBlockChange(i % 16, 64, i / 16);
        }
        drain(manager);
        assertEquals(0, fixture.skyLight(new BlockPos(0, 64, 0)));
        assertFalse(manager.hasUpdates());
    }

    @Test
    void aWorldWithoutSkylightOnlyRunsTheBlockLane() {
        AsyncWorld fixture = neighbourhood();
        WorldLightManager manager = manager(fixture, false);
        Chunk chunk = fixture.chunks.get(AsyncWorld.key(0, 0));
        BlockPos source = new BlockPos(4, 64, 4);
        fixture.setBlock(source, Blocks.GLOWSTONE.getDefaultState());
        manager.queueChunkLight(0, 0, chunk, BfsLightEngine.getEmptySectionsForChunk(chunk));
        manager.queueBlockChange(source.getX(), source.getY(), source.getZ());
        manager.queueSectionChange(0, 4, 0, false);
        manager.queueChunkLoadInit(0, 0, chunk, BfsLightEngine.getEmptySectionsForChunk(chunk));
        drain(manager);
        assertEquals(15, fixture.blockLight(source));
        // Checks no sky light was written.
        assertEquals(15, fixture.skyLight(new BlockPos(4, 200, 4)));
    }

    @Test
    void workForAnUnloadedChunkIsDroppedRatherThanRun() {
        AsyncWorld fixture = neighbourhood();
        WorldLightManager manager = manager(fixture, true);
        Chunk chunk = fixture.chunks.get(AsyncWorld.key(0, 0));
        manager.queueChunkLight(0, 0, chunk, BfsLightEngine.getEmptySectionsForChunk(chunk));
        manager.unregisterChunk(0, 0);
        drain(manager);
        assertFalse(manager.hasUpdates());
        // Removing a chunk from the queues completes whatever it had pending
        manager.registerChunk(chunk);
        manager.queueChunkLight(0, 0, chunk, BfsLightEngine.getEmptySectionsForChunk(chunk));
        manager.removeChunkFromQueues(0, 0);
        drain(manager);
    }

    @Test
    void theServerSideManagerRunsItsLanesOnTheirOwnThreads() throws Exception {
        AsyncWorld fixture = new AsyncWorld(false);
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                fixture.chunk(x, z);
            }
        }
        WorldLightManager manager = manager(fixture, true);
        Chunk chunk = fixture.chunks.get(AsyncWorld.key(0, 0));
        BlockPos source = new BlockPos(8, 64, 8);
        fixture.setBlock(source, Blocks.GLOWSTONE.getDefaultState());
        manager.queueChunkLight(0, 0, chunk, BfsLightEngine.getEmptySectionsForChunk(chunk));
        // The workers pick the batch up on their own
        long deadline = System.currentTimeMillis() + 5000L;
        while (manager.hasUpdates() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10L);
        }
        assertFalse(manager.hasUpdates());
        assertEquals(15, fixture.blockLight(source));
        manager.shutdown();
    }

    // Lowering the render distance unloads up to 100 chunks in one server tick while their light still waits in a long queue.
    @Test
    void unloadingChunksDoesNotWaitForTheLightBacklog() {
        AsyncWorld fixture = new AsyncWorld(true);
        for (int x = 0; x < 10; x++) {
            for (int z = 0; z < 10; z++) {
                fixture.chunk(x, z);
            }
        }
        WorldLightManager manager = manager(fixture, true);
        for (int x = 0; x < 10; x++) {
            for (int z = 0; z < 10; z++) {
                assertTrue(manager.forceRelightChunk(x, z));
            }
        }

        // No worker runs, so any wait for queued work lasts until its time limit.
        long start = System.nanoTime();
        for (int x = 0; x < 10; x++) {
            for (int z = 0; z < 10; z++) {
                assertFalse(manager.finishForUnload(x, z));
                manager.removeChunkFromQueues(x, z);
                manager.unregisterChunk(x, z);
            }
        }
        long millis = (System.nanoTime() - start) / 1_000_000L;
        assertTrue(millis < 1000L, "unloading 100 chunks blocked the server tick for " + millis + " ms");
        assertFalse(manager.hasUpdates());
        assertNull(manager.getLoadedChunk(0, 0));
    }

    // A task a worker is already running may still write the chunk, so only that one is waited for.
    @Test
    void unloadingWaitsForTheRunningTaskOnly() throws Exception {
        for (boolean sky : new boolean[] {true, false}) {
            AsyncWorld fixture = neighbourhood();
            WorldLightManager manager = manager(fixture, true);
            assertTrue(manager.finishForUnload(0, 0));
            manager.queueBlockChange(1, 64, 1);
            assertFalse(manager.finishForUnload(0, 0));

            LightQueue lane = Mixins.get(manager, sky ? "skyQueue" : "blockQueue");
            LightQueue other = Mixins.get(manager, sky ? "blockQueue" : "skyQueue");
            other.removeChunk(0, 0);
            ChunkTasks running = lane.removeFirstTask();
            // Nobody finishes the task, so the wait ends at its limit and the light does not count as complete
            assertFalse(manager.finishForUnload(0, 0));

            Thread worker = new Thread(() -> lane.completeTask(running));
            worker.start();
            worker.join();
            assertTrue(manager.finishForUnload(0, 0));

            // An interrupted wait gives up and keeps the interrupt
            manager.queueBlockChange(1, 64, 1);
            lane.removeFirstTask();
            Thread.currentThread().interrupt();
            assertFalse(manager.finishForUnload(0, 0));
            assertTrue(Thread.interrupted());
        }
    }

    // A world without sky light has no sky lane to ask.
    @Test
    void unloadingWithoutASkyLaneAsksTheBlockLane() {
        WorldLightManager manager = manager(neighbourhood(), false);
        assertTrue(manager.finishForUnload(0, 0));
        manager.queueChunkLoadInit(0, 0, manager.getLoadedChunk(0, 0), new Boolean[16]);
        assertTrue(manager.finishForUnload(0, 0));
        manager.queueBlockChange(1, 64, 1);
        assertFalse(manager.finishForUnload(0, 0));
    }
}
