package com.bdmajora.dynamiclights.client;

import com.bdmajora.dynamiclights.DynamicLights;
import com.bdmajora.dynamiclights.DynamicLightsConfig;
import com.bdmajora.dynamiclights.mixin.RenderGlobalRebuildAccessor;
import com.bdmajora.impetus.engine.impl.render.chunk.shader.TerrainDynamicLights;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.world.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LightSnapshotTest {
    private Minecraft client;

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
        Mc.forge();
    }

    @BeforeEach
    void freshEngine() {
        client = Mc.client();
        Mixins.set(client, "renderGlobal", rebuildRenderer());
        Mixins.set(DynamicLights.class, "config", new DynamicLightsConfig());
        // Uses the meshed fallback path.
        TerrainDynamicLights.setShaderApplied(false);
        DynamicLights.engine().clearLightSources();
    }

    @AfterEach
    void restoreShaderLights() {
        TerrainDynamicLights.setShaderApplied(true);
        TerrainDynamicLights.setLights(new double[0], 0);
    }

    private static RenderGlobal rebuildRenderer() {
        return mock(RenderGlobal.class, Mockito.withSettings().extraInterfaces(RenderGlobalRebuildAccessor.class));
    }

    private static World clientWorld() {
        WorldClient world = mock(WorldClient.class);
        Mixins.set(world, "isRemote", true);
        return world;
    }

    private static DynamicLightSource source(World world, double x, double y, double z, int luminance) {
        DynamicLightSource source = mock(DynamicLightSource.class, Mockito.CALLS_REAL_METHODS);
        when(source.impetus$getDynamicLightWorld()).thenReturn(world);
        when(source.impetus$getDynamicLightX()).thenReturn(x);
        when(source.impetus$getDynamicLightY()).thenReturn(y);
        when(source.impetus$getDynamicLightZ()).thenReturn(z);
        when(source.impetus$getLuminance()).thenReturn(luminance);
        return source;
    }

    @Test
    void indexedLookupsMatchTheUnindexedScanBitForBit() {
        Random random = new Random(7);
        int count = 48;
        double[] xs = new double[count];
        double[] ys = new double[count];
        double[] zs = new double[count];
        int[] luminances = new int[count];
        List<DynamicLightSource> sources = new ArrayList<>();
        // Builds sources across section edges and luminances.
        for (int i = 0; i < count; i++) {
            xs[i] = random.nextDouble() * 48.0D - 24.0D;
            ys[i] = random.nextDouble() * 48.0D + 40.0D;
            zs[i] = random.nextDouble() * 48.0D - 24.0D;
            luminances[i] = random.nextInt(17);
            sources.add(source(null, xs[i], ys[i], zs[i], luminances[i]));
        }
        LightSnapshot snapshot = LightSnapshot.capture(sources);
        assertEquals(count, snapshot.sources.length);
        // Compares one spot check against the mocks.
        assertEquals(DynamicLightsEngine.maxDynamicLightLevel(1, 60, 1, sources.get(0), 0.0D),
                DynamicLightsEngine.lightLevel(1, 60, 1, xs[0], ys[0], zs[0], luminances[0], 0.0D));

        for (int x = -34; x < 34; x++) {
            for (int y = 30; y < 98; y++) {
                for (int z = -34; z < 34; z++) {
                    double expected = 0.0D;
                    for (int i = 0; i < count; i++) {
                        expected = DynamicLightsEngine.lightLevel(x, y, z, xs[i], ys[i], zs[i], luminances[i], expected);
                    }
                    assertEquals(Math.min(expected, 15.0D), snapshot.levelAt(x, y, z), "block " + x + " " + y + " " + z);
                }
            }
        }
    }

    @Test
    void anEmptyOrDarkCaptureAnswersZero() {
        assertSame(LightSnapshot.EMPTY, LightSnapshot.capture(List.of()));
        assertEquals(0.0D, LightSnapshot.EMPTY.levelAt(0, 0, 0));
        LightSnapshot dark = LightSnapshot.capture(List.of(source(null, 0.5D, 0.5D, 0.5D, 0)));
        assertEquals(0, dark.litCount());
        assertEquals(1, dark.sources.length);
        assertEquals(0.0D, dark.levelAt(0, 0, 0));
        LightSnapshot lit = LightSnapshot.capture(List.of(source(null, 0.5D, 0.5D, 0.5D, 9)));
        assertEquals(1, lit.litCount());
        assertEquals(9.0D, lit.levelAt(0, 0, 0));
        // A section the light cannot reach has no index entry at all
        assertEquals(0.0D, lit.levelAt(100, 0, 0));
        // Two lights in one section share an index entry and the brighter wins
        LightSnapshot pair = LightSnapshot.capture(List.of(source(null, 0.5D, 0.5D, 0.5D, 4),
                source(null, 1.5D, 0.5D, 0.5D, 12)));
        assertEquals(12.0D, pair.levelAt(1, 0, 0));
    }

    @Test
    void meshReachCoversEveryWholeLevelAndNothingDimmer() {
        Random random = new Random(11);
        LongOpenHashSet reached = new LongOpenHashSet();
        for (int trial = 0; trial < 600; trial++) {
            double x = random.nextDouble() * 64.0D - 32.0D;
            double y = random.nextDouble() * 64.0D + 32.0D;
            double z = random.nextDouble() * 64.0D - 32.0D;
            int luminance = 1 + random.nextInt(15);
            reached.clear();
            DynamicLightsEngine.collectLitSections(x, y, z, luminance, reached);

            LongOpenHashSet needed = new LongOpenHashSet();
            int bx = (int) Math.floor(x);
            int by = (int) Math.floor(y);
            int bz = (int) Math.floor(z);
            for (int dx = -9; dx <= 9; dx++) {
                for (int dy = -9; dy <= 9; dy++) {
                    for (int dz = -9; dz <= 9; dz++) {
                        int px = bx + dx;
                        int py = by + dy;
                        int pz = bz + dz;
                        if ((int) DynamicLightsEngine.lightLevel(px, py, pz, x, y, z, luminance, 0.0D) < 1) {
                            continue;
                        }
                        // Walks every section whose mesh reads this block.
                        for (int sx = (px - 1) >> 4; sx <= (px + 1) >> 4; sx++) {
                            for (int sy = (py - 1) >> 4; sy <= (py + 1) >> 4; sy++) {
                                for (int sz = (pz - 1) >> 4; sz <= (pz + 1) >> 4; sz++) {
                                    needed.add(DynamicLightsEngine.packSection(sx, sy, sz));
                                }
                            }
                        }
                    }
                }
            }
            assertEquals(needed, reached, "light " + x + " " + y + " " + z + " luminance " + luminance);
        }
    }

    @Test
    void reachedSectionsNeverOverflowTheBuffer() {
        long[] out = new long[DynamicLightsEngine.MAX_REACHED_SECTIONS];
        int most = 0;
        for (double x = -0.5D; x <= 16.5D; x += 0.125D) {
            for (double y = -0.5D; y <= 16.5D; y += 0.125D) {
                most = Math.max(most, DynamicLightsEngine.reachedSections(x, y, 8.0D,
                        DynamicLightsEngine.LOOKUP_RADIUS, 1, out));
            }
        }
        // The widest radius with the mesh margin still stays within three sections per axis
        assertTrue(most <= DynamicLightsEngine.MAX_REACHED_SECTIONS);
        assertTrue(most >= 8);
        assertTrue(DynamicLightsEngine.meshReach(1) < 1.0E-3D);
        assertTrue(DynamicLightsEngine.meshReach(15) < DynamicLightsEngine.LOOKUP_RADIUS);
    }

    @Test
    void relightingQueuesTheUnionOfOldAndNewReach() {
        World world = clientWorld();
        Mixins.set(client, "world", world);
        RenderGlobal renderer = rebuildRenderer();
        RenderGlobalRebuildAccessor accessor = (RenderGlobalRebuildAccessor) renderer;

        LongOpenHashSet first = DynamicLightsEngine.relightSections(renderer, world, null, 8.0D, 8.0D, 8.0D, 14);
        assertEquals(Set.of(DynamicLightsEngine.packSection(0, 0, 0)), first);
        verify(accessor).impetus$markBlocksForUpdate(0, 0, 0, 15, 15, 15, false);

        // Crosses into the next section.
        LongOpenHashSet second = DynamicLightsEngine.relightSections(renderer, world, first, 24.0D, 8.0D, 8.0D, 14);
        assertEquals(Set.of(DynamicLightsEngine.packSection(1, 0, 0)), second);
        verify(accessor, times(2)).impetus$markBlocksForUpdate(0, 0, 0, 15, 15, 15, false);
        verify(accessor).impetus$markBlocksForUpdate(16, 0, 0, 31, 15, 15, false);
        LongOpenHashSet edge = DynamicLightsEngine.relightSections(renderer, world, second, 16.0D, 8.0D, 8.0D, 14);
        assertEquals(2, edge.size());
        verify(accessor, times(2)).impetus$markBlocksForUpdate(16, 0, 0, 31, 15, 15, false);

        // Goes dark.
        Mockito.clearInvocations(accessor);
        assertNull(DynamicLightsEngine.relightSections(renderer, world, edge, 16.0D, 8.0D, 8.0D, 0));
        verify(accessor, times(2)).impetus$markBlocksForUpdate(anyInt(), anyInt(), anyInt(), anyInt(), anyInt(),
                anyInt(), anyBoolean());

        // Checks a hidden world and a missing renderer.
        Mockito.clearInvocations(accessor);
        assertEquals(1, DynamicLightsEngine.relightSections(renderer, clientWorld(), edge, 8.0D, 8.0D, 8.0D, 14).size());
        assertEquals(1, DynamicLightsEngine.relightSections(null, world, edge, 8.0D, 8.0D, 8.0D, 14).size());
        verify(accessor, never()).impetus$markBlocksForUpdate(anyInt(), anyInt(), anyInt(), anyInt(), anyInt(),
                anyInt(), anyBoolean());
    }

    @Test
    void lookupsFollowAddsAndRemovesWithoutAnUpdate() {
        DynamicLightsEngine engine = DynamicLights.engine();
        World world = clientWorld();
        DynamicLightSource torch = source(world, 0.5D, 64.5D, 0.5D, 14);
        assertEquals(0.0D, engine.getDynamicLightLevel(0, 64, 0));
        // Adds a far light.
        engine.addLightSource(source(world, 200.5D, 64.5D, 0.5D, 14));
        engine.addLightSource(torch);
        assertEquals(14.0D, engine.getDynamicLightLevel(0, 64, 0));
        // The removal's own rebuild must not see the light it is clearing
        Mockito.doAnswer(call -> {
            assertEquals(0.0D, DynamicLights.engine().getDynamicLightLevel(0, 64, 0));
            return null;
        }).when(torch).impetus$scheduleTrackedChunksRebuild(any());
        engine.removeLightSource(torch);
        verify(torch).impetus$scheduleTrackedChunksRebuild(any());
        // Removing an untracked source does nothing
        engine.removeLightSource(torch);
        verify(torch, times(1)).impetus$scheduleTrackedChunksRebuild(any());
    }

    @Test
    void anUpdateRecapturesOnlyWhenASourceChanged() {
        DynamicLightsEngine engine = DynamicLights.engine();
        RenderGlobal renderer = rebuildRenderer();
        World world = clientWorld();
        DynamicLightSource torch = source(world, 0.5D, 64.5D, 0.5D, 14);
        engine.addLightSource(torch);
        assertEquals(14.0D, engine.getDynamicLightLevel(0, 64, 0));

        // Keeps the captured position for a declined move.
        when(torch.impetus$getDynamicLightX()).thenReturn(40.5D);
        when(torch.impetus$updateDynamicLight(renderer)).thenReturn(false);
        Mixins.set(engine, "lastUpdate", 0L);
        engine.updateAll(renderer);
        assertEquals(0, engine.getLastUpdateCount());
        assertEquals(14.0D, engine.getDynamicLightLevel(0, 64, 0));
        assertEquals(0.0D, engine.getDynamicLightLevel(40, 64, 0));

        when(torch.impetus$updateDynamicLight(renderer)).thenReturn(true);
        Mixins.set(engine, "lastUpdate", 0L);
        engine.updateAll(renderer);
        assertEquals(1, engine.getLastUpdateCount());
        assertEquals(0.0D, engine.getDynamicLightLevel(0, 64, 0));
        assertEquals(14.0D, engine.getDynamicLightLevel(40, 64, 0));
    }

    @Test
    void readersNeverWaitOnTheWriterMonitor() throws Exception {
        DynamicLightsEngine engine = DynamicLights.engine();
        World world = clientWorld();
        engine.addLightSource(source(world, 0.5D, 64.5D, 0.5D, 14));
        assertEquals(14.0D, engine.getDynamicLightLevel(0, 64, 0));

        Object monitor = Mixins.get(engine, "dynamicLightSources");
        AtomicReference<Double> seen = new AtomicReference<>();
        Thread reader = new Thread(() -> seen.set(engine.getDynamicLightLevel(0, 64, 0)));
        synchronized (monitor) {
            reader.start();
            reader.join(5_000L);
        }
        assertFalse(reader.isAlive());
        assertEquals(14.0D, seen.get());
    }

    @Test
    void concurrentReadersSurviveAddsAndRemoves() throws Exception {
        DynamicLightsEngine engine = DynamicLights.engine();
        World world = clientWorld();
        List<DynamicLightSource> sources = new ArrayList<>();
        for (int i = 0; i < 32; i++) {
            sources.add(source(world, i + 0.5D, 64.5D, 0.5D, 1 + i % 14));
        }

        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread[] readers = new Thread[4];
        for (int r = 0; r < readers.length; r++) {
            readers[r] = new Thread(() -> {
                try {
                    for (int i = 0; i < 20_000; i++) {
                        double level = engine.getDynamicLightLevel(i % 40, 64, 0);
                        assertTrue(level >= 0.0D && level <= 15.0D);
                    }
                } catch (Throwable t) {
                    failure.set(t);
                }
            });
            readers[r].start();
        }
        for (int round = 0; round < 200; round++) {
            DynamicLightSource source = sources.get(round % sources.size());
            engine.addLightSource(source);
            if (round % 3 == 0) {
                engine.removeLightSource(source);
            }
        }
        for (Thread reader : readers) {
            reader.join();
        }
        assertNull(failure.get());
        engine.clearLightSources();
        assertEquals(0.0D, engine.getDynamicLightLevel(5, 64, 0));
    }

    @Test
    void lightmapsTakeOnlyWholeLevelsAndCapAtFifteen() {
        DynamicLightsEngine engine = DynamicLights.engine();
        // Bits outside the two nibbles survive a fraction that raises nothing
        int lightmap = (3 << 20) | 0x5;
        assertEquals(lightmap, engine.getLightmapWithDynamicLight(0.9D, lightmap));
        assertEquals((3 << 20) | (15 << 4), engine.getLightmapWithDynamicLight(40.0D, lightmap));
        assertEquals((3 << 20) | (1 << 4), engine.getLightmapWithDynamicLight(1.0D, lightmap));
    }

    @Test
    void withShaderLightsMeshesTrackAndRebuildNothing() {
        TerrainDynamicLights.setShaderApplied(true);
        World world = clientWorld();
        Mixins.set(client, "world", world);
        RenderGlobal renderer = rebuildRenderer();
        LongOpenHashSet previous = new LongOpenHashSet(new long[] {DynamicLightsEngine.packSection(0, 0, 0)});
        assertNull(DynamicLightsEngine.relightSections(renderer, world, previous, 8.0D, 8.0D, 8.0D, 14));
        verify((RenderGlobalRebuildAccessor) renderer, never()).impetus$markBlocksForUpdate(anyInt(), anyInt(), anyInt(), anyInt(),
                anyInt(), anyInt(), anyBoolean());
    }

    // Returns a viewer at the origin.
    private net.minecraft.entity.Entity viewer() {
        net.minecraft.entity.Entity viewer = mock(net.minecraft.entity.Entity.class);
        viewer.lastTickPosX = -2.0D;
        viewer.posX = 2.0D;
        when(client.getRenderViewEntity()).thenReturn(viewer);
        return viewer;
    }

    // Reads the published lights back.
    private static float[] published() {
        java.nio.ByteBuffer block = org.lwjgl.system.MemoryUtil.memCalloc(TerrainDynamicLights.BYTES);
        try {
            TerrainDynamicLights.write(org.lwjgl.system.MemoryUtil.memAddress(block), 0, 0, 0);
            float[] lights = new float[TerrainDynamicLights.lightCount() * 4];
            for (int i = 0; i < lights.length; i++) {
                lights[i] = block.getFloat(i * 4);
            }
            return lights;
        } finally {
            org.lwjgl.system.MemoryUtil.memFree(block);
        }
    }

    @Test
    void theNearestLitSourcesArePublishedWhereTheyAreDrawn() {
        TerrainDynamicLights.setShaderApplied(true);
        DynamicLightsEngine engine = DynamicLights.engine();
        World world = clientWorld();
        viewer();
        // Publishes ten more lights than the block holds.
        int total = TerrainDynamicLights.MAX_LIGHTS + 10;
        for (int i = 0; i < total; i++) {
            engine.addLightSource(source(world, total - i, 64.0D, 0.0D, 1 + i % 14));
        }
        DynamicLightSource dark = source(world, 0.0D, 64.0D, 0.0D, 0);
        engine.addLightSource(dark);
        when(dark.impetus$getLuminance()).thenReturn(0);
        DynamicLightSource broken = source(world, Double.NaN, 64.0D, 0.0D, 14);
        engine.addLightSource(broken);
        engine.publishTerrainLights(0.5F);
        assertEquals(TerrainDynamicLights.MAX_LIGHTS, TerrainDynamicLights.lightCount());
        float[] lights = published();
        java.util.Set<Float> xs = new java.util.HashSet<>();
        for (int i = 0; i < lights.length; i += 4) {
            assertTrue(lights[i + 3] > 0.0F);
            assertFalse(Float.isNaN(lights[i]));
            xs.add(lights[i]);
        }
        // Checks the nearest lights were kept.
        for (int x = 1; x <= TerrainDynamicLights.MAX_LIGHTS; x++) {
            assertTrue(xs.contains((float) x), "x " + x);
        }

        // A source with no motion of its own answers its tick position for any partial tick
        DynamicLightSource plain = source(world, 3.0D, 4.0D, 5.0D, 7);
        assertEquals(3.0D, plain.impetus$getDynamicLightX(0.25F));
        assertEquals(4.0D, plain.impetus$getDynamicLightY(0.25F));
        assertEquals(5.0D, plain.impetus$getDynamicLightZ(0.25F));
    }

    @Test
    void nothingIsPublishedWhenTheShadersOrTheModeOrTheViewerAreMissing() {
        DynamicLightsEngine engine = DynamicLights.engine();
        World world = clientWorld();
        viewer();
        engine.addLightSource(source(world, 1.0D, 64.0D, 0.0D, 14));
        TerrainDynamicLights.setShaderApplied(true);
        engine.publishTerrainLights(0.0F);
        assertEquals(1, TerrainDynamicLights.lightCount());
        // The meshed fallback bakes the lights in itself
        TerrainDynamicLights.setShaderApplied(false);
        engine.publishTerrainLights(0.0F);
        assertEquals(0, TerrainDynamicLights.lightCount());
        TerrainDynamicLights.setShaderApplied(true);
        engine.publishTerrainLights(0.0F);
        DynamicLights.options().mode = com.bdmajora.dynamiclights.DynamicLightsMode.OFF;
        engine.publishTerrainLights(0.0F);
        assertEquals(0, TerrainDynamicLights.lightCount());
        DynamicLights.options().mode = com.bdmajora.dynamiclights.DynamicLightsMode.REALTIME;
        when(client.getRenderViewEntity()).thenReturn(null);
        engine.publishTerrainLights(0.0F);
        assertEquals(0, TerrainDynamicLights.lightCount());
        // Publishes an empty frame.
        engine.clearLightSources();
        viewer();
        engine.publishTerrainLights(0.0F);
        assertEquals(0, TerrainDynamicLights.lightCount());
    }
}
