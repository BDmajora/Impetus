package com.bdmajora.fulgor.async;

import com.bdmajora.fulgor.Fulgor;
import com.bdmajora.fulgor.FulgorConfig;
import com.bdmajora.fulgor.async.engine.AsyncWorld;
import com.bdmajora.fulgor.async.engine.BfsLightEngine;
import com.bdmajora.fulgor.lighting.LightingEngine;
import com.bdmajora.fulgor.mixin.async.world.ChunkLightingMixin;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import net.minecraft.client.Minecraft;
import net.minecraft.profiler.Profiler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.chunk.Chunk;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecoveryPathsTest {
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

    @Test
    void anOverflowingChunkIsRequeuedUntilItGivesUp() {
        AsyncWorld fixture = new AsyncWorld(false);
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                fixture.chunk(x, z);
            }
        }
        Chunk chunk = fixture.chunks.get(AsyncWorld.key(0, 0));
        WorldLightManager manager = new WorldLightManager(fixture.world, true);
        manager.registerChunk(chunk);
        LightQueue queue = Mixins.get(manager, "blockQueue");

        // A batch that overflowed is put back with one more attempt against it
        ChunkTasks task = new ChunkTasks(ChunkPos.asLong(0, 0));
        task.initialLightChunk = chunk;
        task.initialLightGeneration = 1L;
        assertTrue((boolean) Mixins.call(manager, "requeueAfterOverflow", queue, task, 0, 0, "Block"));
        // An edge pass that overflowed without an initial generation of its own restarts the whole chunk instead
        ChunkTasks edges = new ChunkTasks(ChunkPos.asLong(0, 0));
        edges.initialLightEdgeGeneration = 1L;
        assertTrue((boolean) Mixins.call(manager, "requeueAfterOverflow", queue, edges, 0, 0, "Block"));
        // Past the retry limit it is given up on instead
        task.relightAttempts = 99;
        assertFalse((boolean) Mixins.call(manager, "requeueAfterOverflow", queue, task, 0, 0, "Block"));
        // A chunk that has gone away is not requeued at all
        manager.unregisterChunk(0, 0);
        ChunkTasks gone = new ChunkTasks(ChunkPos.asLong(0, 0));
        gone.initialLightChunk = chunk;
        assertFalse((boolean) Mixins.call(manager, "requeueAfterOverflow", queue, gone, 0, 0, "Block"));
        manager.shutdown();
    }

    @Test
    void edgeReconciliationIsRestartedAfterAnOverflow() {
        AsyncWorld fixture = new AsyncWorld(false);
        Chunk chunk = fixture.chunk(0, 0);
        LoadedChunkMap chunks = new LoadedChunkMap();
        chunks.put(ChunkPos.asLong(0, 0), chunk);
        LightQueue sky = new LightQueue();
        LightQueue block = new LightQueue();
        InitialLightCoordinator coordinator = new InitialLightCoordinator(chunks, sky, block);

        ChunkTasks task = new ChunkTasks(ChunkPos.asLong(0, 0));
        // Without an edge generation there is nothing to restart
        assertFalse(coordinator.restartAfterEdgeOverflow(task, 0, 0, "Sky"));
        task.initialLightEdgeGeneration = 1L;
        // Nothing is on record for the chunk, so a newer generation already replaced this pass and the overflow is dropped
        assertTrue(coordinator.restartAfterEdgeOverflow(task, 0, 0, "Sky"));
        // With the matching generation queued, the pass is put back with another attempt against it
        coordinator.queue(0, 0, chunk, BfsLightEngine.getEmptySectionsForChunk(chunk));
        assertTrue(coordinator.hasPending(ChunkPos.asLong(0, 0)));
        assertTrue(coordinator.restartAfterEdgeOverflow(task, 0, 0, "Sky"));
        // The recovery above became generation two; the chunk it was made for is no longer the loaded one, so it is dropped
        task.initialLightEdgeGeneration = 2L;
        chunks.put(ChunkPos.asLong(0, 0), fixture.chunk(0, 0));
        assertFalse(coordinator.restartAfterEdgeOverflow(task, 0, 0, "Sky"));
        // Past the limit it is given up on
        task.edgeCheckAttempts = 99;
        assertFalse(coordinator.restartAfterEdgeOverflow(task, 0, 0, "Sky"));
        assertThrows(IllegalArgumentException.class,
                () -> coordinator.queueRecovery(0, 0, chunk, new Boolean[16], -1));
        assertNotNull(coordinator.getPendingFuture(ChunkPos.asLong(0, 0)));
        coordinator.removeChunk(0, 0);
        assertFalse(coordinator.hasPending(ChunkPos.asLong(0, 0)));
    }

    @Test
    void theClientRefusesLightWorkFromTheWrongThread() throws Exception {
        Minecraft client = Mc.client();
        World world = mock(World.class);
        Mixins.set(world, "isRemote", true);
        Mixins.set(world, "profiler", new Profiler());
        WorldProvider dimension = mock(WorldProvider.class);
        when(dimension.hasSkyLight()).thenReturn(true);
        Mixins.set(world, "provider", dimension);
        when(world.getChunkProvider()).thenReturn(mock(net.minecraft.world.chunk.IChunkProvider.class));
        LightingEngine engine = new LightingEngine(world);
        engine.scheduleLightUpdate(EnumSkyBlock.BLOCK, BlockPos.ORIGIN);
        // Off the client thread the batch is left for the tick to pick up
        when(client.isCallingFromMinecraftThread()).thenReturn(false);
        engine.processLightUpdates();
        // On it the batch is worked off
        when(client.isCallingFromMinecraftThread()).thenReturn(true);
        engine.processLightUpdates();
        engine.processLightUpdates();
    }

    @Test
    void vanillasOwnLightingCallsAreTurnedOff() {
        ChunkLightingMixin chunk = Mixins.instanceWith(ChunkLightingMixin.class, AsyncLitChunk.class);
        var gaps = Mixins.ci();
        Mixins.call(chunk, "fulgor$skipRecheckGaps", true, gaps);
        assertTrue(gaps.isCancelled());
        var occlusion = Mixins.ci();
        Mixins.call(chunk, "fulgor$skipSkylightOcclusion", 0, 0, occlusion);
        assertTrue(occlusion.isCancelled());
        var relight = Mixins.ci();
        Mixins.call(chunk, "fulgor$skipRelightChecks", relight);
        assertTrue(relight.isCancelled());
        var check = Mixins.ci();
        Mixins.call(chunk, "fulgor$skipCheckLight", check);
        assertTrue(check.isCancelled());
    }
}
