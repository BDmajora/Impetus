package com.bdmajora.impetus.engine.impl.render.chunk;

import com.bdmajora.impetus.engine.impl.gl.device.CommandList;
import com.bdmajora.impetus.engine.impl.gl.device.GLRenderDevice;
import com.bdmajora.impetus.engine.impl.render.chunk.occlusion.AsyncOcclusionMode;
import com.bdmajora.impetus.engine.impl.render.viewport.CameraTransform;
import com.bdmajora.impetus.engine.impl.render.viewport.Viewport;
import com.bdmajora.impetus.engine.impl.util.PositionUtil;
import com.bdmajora.testing.Devices;
import com.bdmajora.testing.Passes;
import com.bdmajora.testing.Sections;
import com.bdmajora.testing.TestFogService;
import com.bdmajora.testing.TestGl;
import com.bdmajora.testing.TestSectionManager;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.Mockito;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

// A hang here would otherwise stall the whole run, so each test is cut off with its stack
@Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class RenderSectionManagerTest {
    private static final ChunkRenderMatrices MATRICES = new ChunkRenderMatrices(new Matrix4f(), new Matrix4f());
    private GLRenderDevice device;
    private CommandList commands;
    private TestSectionManager manager;

    @BeforeEach
    void activate() {
        device = Devices.active();
        commands = device.createCommandList();
        Mockito.when(TestGl.gl().glGetUniformLocation(Mockito.anyInt(), Mockito.any())).thenReturn(1);
        TestFogService.cutoff = 1_000_000f;
        // These tests assert on client-array multi-draws; another test applying the default config would otherwise switch them to indirect
        com.bdmajora.impetus.engine.impl.ImpetusRuntimeOptions.multiDrawIndirect = false;
    }

    @AfterEach
    void deactivate() {
        if (manager != null) {
            manager.destroy();
        }
        TestFogService.cutoff = 1f;
        device.makeInactive();
    }

    // One full frame the way SimpleWorldRenderer drives it: the walk runs last, so builds it queues dispatch on the following frame
    private void frame(Viewport viewport, int number, boolean immediately) {
        manager.runAsyncTasks();
        manager.updateChunks(immediately);
        manager.uploadChunks();
        if (manager.needsUpdate()) {
            manager.update(viewport, number, false);
        }
        manager.tickVisibleRenders();
    }

    // A walk frame followed by the frame that dispatches and uploads what it queued
    private void frames(Viewport viewport, int number, boolean immediately) {
        frame(viewport, number, immediately);
        frame(viewport, number + 1, immediately);
    }

    @Test
    void buildsUploadsAndDrawsLoadedChunks() {
        manager = new TestSectionManager(commands, 4, 0, 2, false, AsyncOcclusionMode.NONE, -1);
        manager.empty.add(PositionUtil.packSection(0, 1, 0));
        manager.globalEntities.add(PositionUtil.packSection(1, 0, 0));
        manager.onChunkAdded(0, 0);
        manager.onChunkAdded(1, 0);
        manager.onChunkAdded(1, 0);
        assertEquals(4, manager.getTotalSections());
        assertTrue(manager.isSectionBuilt(0, 1, 0));
        assertFalse(manager.isSectionBuilt(0, 0, 0));
        assertFalse(manager.isSectionBuilt(9, 9, 9));
        assertTrue(manager.needsUpdate());
        Viewport viewport = Sections.viewport(8, 8, 8);
        frames(viewport, 1, true);
        assertEquals(3, manager.builds.get());
        assertTrue(manager.isSectionBuilt(0, 0, 0));
        assertTrue(manager.isSectionVisible(0, 0, 0));
        assertEquals(1, manager.getSectionsWithGlobalEntities().size());
        assertEquals(3, manager.getVisibleChunkCount());
        assertNotNull(manager.getRenderLists());
        assertSame(Passes.CONFIG, manager.getRenderPassConfiguration());
        // Every section shares the one sprite, and the ticker counts distinct sprites
        assertEquals("A: 1", manager.getTickerDebugString());

        CameraTransform camera = viewport.getTransform();
        manager.renderLayer(MATRICES, Passes.SOLID, camera, camera);
        Mockito.verify(TestGl.gl(), Mockito.atLeastOnce()).glMultiDrawElementsBaseVertex(Mockito.anyInt(), Mockito.anyLong(), Mockito.anyInt(), Mockito.anyLong(), Mockito.anyInt(), Mockito.anyLong());
        manager.toggleRenderingForTerrainPass(Passes.SOLID);
        Mockito.clearInvocations(TestGl.gl());
        manager.renderLayer(MATRICES, Passes.SOLID, camera, camera);
        Mockito.verify(TestGl.gl(), Mockito.never()).glMultiDrawElementsBaseVertex(Mockito.anyInt(), Mockito.anyLong(), Mockito.anyInt(), Mockito.anyLong(), Mockito.anyInt(), Mockito.anyLong());
        manager.toggleRenderingForTerrainPass(Passes.SOLID);
        manager.debugInfo = true;
        manager.renderLayer(MATRICES, Passes.SOLID, camera, camera);
        manager.runAsyncTasks();
        Collection<String> debug = manager.getDebugStrings();
        assertTrue(debug.stream().anyMatch(s -> s.startsWith("G: ")));
        assertTrue(debug.stream().anyMatch(s -> s.startsWith("Chunk Queues")));
        assertTrue(debug.stream().anyMatch(s -> s.startsWith("solid - 3 sections")));

        // A block change schedules a rebuild; from another thread it is marshalled through the async queue
        manager.scheduleRebuild(0, 0, 0, false);
        Thread other = new Thread(() -> manager.scheduleRebuild(1, 0, 0, true));
        other.start();
        try {
            other.join();
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
        frames(viewport, 3, false);
        assertEquals(5, manager.builds.get());
        manager.scheduleRebuildAll();
        frames(viewport, 5, true);
        assertEquals(8, manager.builds.get());
        manager.refused.add(PositionUtil.packSection(0, 0, 0));
        manager.scheduleRebuild(0, 0, 0, false);
        frames(viewport, 7, true);
        assertFalse(manager.section(0, 0, 0).hasAnythingToRender());

        manager.onChunkRemoved(1, 0);
        manager.onChunkRemoved(1, 0);
        assertEquals(2, manager.getTotalSections());
        assertEquals(0, manager.getSectionsWithGlobalEntities().size());
        frames(viewport, 9, true);
        assertEquals(2, manager.getAllRenderSections().size());
        manager.getBuilder().tick();
        assertTrue(manager.getJobMetricsTracker().getMetrics().size() >= 1);
        assertFalse(manager.getSectionMetricsTracker().getSlowestSections().isEmpty());
    }

    // A camera move from the previous viewport to this one, with a fresh search so the move is seen
    private void moveTo(double x, double y, double z, int number) {
        manager.markGraphDirty();
        frames(Sections.viewport(x, y, z), number, true);
    }

    @Test
    void cameraMovementResortsTranslucentSections() {
        manager = new TestSectionManager(commands, 4, 0, 1, false, AsyncOcclusionMode.NONE, -1);
        manager.translucent.add(PositionUtil.packSection(0, 0, 0));
        manager.distanceSorted.add(PositionUtil.packSection(1, 0, 0));
        manager.onChunkAdded(0, 0);
        manager.onChunkAdded(1, 0);
        frames(Sections.viewport(8, 8, 2), 1, true);
        RenderSection tree = manager.section(0, 0, 0);
        RenderSection distance = manager.section(1, 0, 0);
        assertTrue(tree.isTreeSorted());
        assertEquals(RenderSection.SortMode.DYNAMIC, distance.getSortMode());
        assertEquals(3, distance.getHighestSortingIndex());
        int built = manager.builds.get();
        assertEquals(0, manager.sorts.get());

        // Crossing the tree's cut plane at z=5 re-sorts it; the distance-sorted section re-sorts on any whole block of movement
        moveTo(8, 8, 6, 3);
        assertEquals(2, manager.sorts.get());
        // Less than a block, and no plane crossed: nothing to do
        moveTo(8, 8, 6.5, 5);
        assertEquals(2, manager.sorts.get());
        // Back across the plane re-sorts both again
        moveTo(8, 8, 4, 7);
        assertEquals(4, manager.sorts.get());
        // Into another section: both again
        moveTo(8, 24, 24, 9);
        assertEquals(6, manager.sorts.get());
        // Moving within a section that shares no grid line with the distance-sorted one, and crossing no plane, sorts nothing
        moveTo(10, 26, 26, 11);
        assertEquals(6, manager.sorts.get());
        assertEquals(built, manager.builds.get());

        // Near sections sort before the frame continues when important rebuilds are allowed
        manager.importantRebuilds = true;
        moveTo(8, 8, 4, 13);
        assertEquals(8, manager.sorts.get());
        // A pending rebuild takes over from a sort, and a rebuilt section re-indexes its planes
        manager.scheduleRebuild(0, 0, 0, false);
        frames(Sections.viewport(8, 8, 4), 15, true);
        assertEquals(built + 1, manager.builds.get());
        assertTrue(tree.isTreeSorted());
        moveTo(8, 8, 6, 17);
        assertEquals(10, manager.sorts.get());
        Collection<String> debug = manager.getDebugStrings();
        assertTrue(debug.stream().anyMatch(s -> s.startsWith("Sorting:")));
        assertTrue(debug.stream().anyMatch(s -> s.startsWith("Tree Sort: 1 planes")));

        // Fog that hides everything past it shortens the search; translucent fog does not
        manager.fogOcclusion = true;
        manager.respectQueueLimit = false;
        moveTo(8, 8, 4, 19);
        TestFogService.color = new float[]{0, 0, 0, 0.5f};
        moveTo(8, 8, 4, 21);
        TestFogService.color = new float[]{0.5f, 0.6f, 0.7f, 1f};
        TestFogService.shape = com.bdmajora.impetus.engine.impl.render.chunk.fog.FogService.FOG_SHAPE_PLANAR;
        moveTo(8, 8, 4, 23);
        TestFogService.shape = 0;
        assertTrue(manager.getVisibleChunkCount() > 0);
        assertNull(manager.createSortTask(new RenderSection(null, 9, 9, 9), 1));

        // Removing a tree-sorted section drops its planes from the index
        manager.onChunkRemoved(0, 0);
        manager.runAsyncTasks();
        assertTrue(manager.getDebugStrings().stream().anyMatch(s -> s.startsWith("Tree Sort: 0 planes")));
    }

    @Test
    void theShadowPassSearchesForBothPassesAndSharesTheBuilder() {
        manager = new TestSectionManager(commands, 4, 0, 1, true, AsyncOcclusionMode.EVERYTHING, -1);
        manager.onChunkAdded(0, 0);
        Viewport viewport = Sections.viewport(8, 8, 8);
        assertTrue(manager.hasShadowPass());
        frames(viewport, 1, true);
        manager.finishAllGraphUpdates();
        frames(viewport, 3, true);
        manager.finishAllGraphUpdates();
        // The shadow pass runs first in a frame and submits the player search too
        manager.shadowPass = true;
        assertTrue(manager.needsUpdate());
        manager.updateForShadowPass(viewport, viewport, 4, false);
        manager.finishAllGraphUpdates();
        manager.updateChunks(false);
        manager.renderLayer(MATRICES, Passes.SOLID, viewport.getTransform(), viewport.getTransform());
        assertTrue(manager.isSectionVisible(0, 0, 0));
        manager.shadowPass = false;
        assertTrue(manager.needsUpdate());
        manager.update(viewport, 4, false);
        manager.finishAllGraphUpdates();
        assertTrue(manager.isSectionVisible(0, 0, 0));
        manager.onChunkRemoved(0, 0);
        manager.markGraphDirty();
        frames(viewport, 5, true);
        manager.finishAllGraphUpdates();
        assertFalse(manager.isSectionVisible(0, 0, 0));
        manager.managedBlock(() -> true);
        manager.scheduleAsyncTask(() -> {});

        TestSectionManager plain = new TestSectionManager(commands, 4, 0, 1, false, AsyncOcclusionMode.NONE, -1);
        assertFalse(plain.hasShadowPass());
        assertThrows(IllegalStateException.class, () -> plain.updateForShadowPass(viewport, viewport, 1, false));
        plain.destroy();
    }

    // The terrain pass hands in vanilla's frameCount and Umbra's shadow pass its own counter from zero; the walks used to stamp those, so one under the smaller number reached only the camera section
    @Test
    void walksStampTheirOwnFramesWhateverTheCallerCounts() {
        manager = new TestSectionManager(commands, 4, 0, 1, true, AsyncOcclusionMode.NONE, -1);
        manager.onChunkAdded(0, 0);
        manager.onChunkAdded(1, 0);
        manager.onChunkAdded(2, 0);
        Viewport viewport = Sections.viewport(8, 8, 8);
        frames(viewport, 5000, true);
        assertEquals(3, manager.getVisibleChunkCount());

        // A player walk driven from the shadow pass under a far smaller number still reaches every section
        manager.shadowPass = true;
        manager.markGraphDirty();
        manager.update(viewport, 1, false);
        manager.shadowPass = false;
        assertEquals(3, manager.getVisibleChunkCount());
        assertTrue(manager.isSectionVisible(2, 0, 0));

        // The shadow pass's own entry walks both lists, and the terrain pass after it reuses the player walk
        manager.shadowPass = true;
        manager.markGraphDirty();
        manager.updateForShadowPass(viewport, viewport, 2, false);
        assertTrue(manager.didShadowPassRunThisFrame());
        assertEquals(3, manager.getVisibleChunkCount());
        manager.shadowPass = false;
        assertTrue(manager.needsUpdate());
        manager.update(viewport, 6000, false);
        assertFalse(manager.didShadowPassRunThisFrame());
        assertEquals(3, manager.getVisibleChunkCount());

        // A rebuild submitted after the mixed numbers is applied, not dropped as older than the last build
        RenderSection far = manager.section(2, 0, 0);
        int lastBuilt = far.getLastBuiltFrame();
        int built = manager.builds.get();
        manager.scheduleRebuild(2, 0, 0, false);
        frames(viewport, 3, true);
        assertEquals(built + 1, manager.builds.get());
        assertTrue(far.getLastBuiltFrame() > lastBuilt);
    }

    // Only the abstract hooks, so every base default runs
    private static final class Bare extends RenderSectionManager {
        Bare(CommandList commands) {
            super(Passes.CONFIG, () -> new com.bdmajora.impetus.engine.impl.render.chunk.compile.ChunkBuildContext(Passes.CONFIG),
                    (device, config) -> new DefaultChunkRenderer(device, config, new TestFogService()) {
                        @Override
                        protected void configureShaderInterface(com.bdmajora.impetus.engine.impl.render.chunk.shader.ChunkShaderInterface shader) {}
                    }, 2, commands, 0, 1, -1, false);
        }

        @Override
        protected AsyncOcclusionMode getAsyncOcclusionMode() {
            return AsyncOcclusionMode.NONE;
        }

        @Override
        protected boolean useFogOcclusion() {
            return false;
        }

        @Override
        protected boolean useRasterOcclusionCulling() {
            return false;
        }

        @Override
        public com.bdmajora.impetus.engine.impl.render.chunk.fog.FogService getFogService() {
            return new TestFogService();
        }

        @Override
        protected boolean shouldUseOcclusionCulling(Viewport viewport, boolean spectator) {
            return true;
        }

        @Override
        protected boolean isSectionVisuallyEmpty(int x, int y, int z) {
            return false;
        }

        @Override
        protected com.bdmajora.impetus.engine.impl.render.chunk.compile.tasks.ChunkBuilderTask<com.bdmajora.impetus.engine.impl.render.chunk.compile.ChunkBuildOutput> createRebuildTask(RenderSection render, int frame) {
            return null;
        }
    }

    @Test
    void baseDefaultsAreQuietAndBuildNothing() {
        Bare bare = new Bare(commands);
        bare.onChunkAdded(0, 0);
        Viewport viewport = Sections.viewport(8, 8, 8);
        for (int i = 0; i < 3; i++) {
            bare.runAsyncTasks();
            bare.updateChunks(false);
            bare.uploadChunks();
            if (bare.needsUpdate()) {
                bare.update(viewport, i, false);
            }
        }
        assertFalse(bare.isInShadowPass());
        assertTrue(bare.isSectionBuilt(0, 0, 0));
        assertEquals(0, bare.getVisibleChunkCount());
        bare.scheduleRebuild(0, 0, 0, true);
        bare.renderLayer(MATRICES, Passes.SOLID, viewport.getTransform(), viewport.getTransform());
        assertEquals("", bare.getTickerDebugString());
        assertFalse(bare.getDebugStrings().isEmpty());
        bare.destroy();
    }

    @Test
    void workerThreadsRunBuildsOffTheRenderThread() {
        manager = new TestSectionManager(commands, 4, 0, 1, false, AsyncOcclusionMode.NONE, 1);
        // Neighbours, since the walk only reaches sections through loaded ones
        manager.onChunkAdded(0, 0);
        manager.onChunkAdded(1, 0);
        Viewport viewport = Sections.viewport(8, 8, 8);
        frames(viewport, 1, false);
        manager.getBuilder().managedBlock(() -> manager.builds.get() >= 2);
        frames(viewport, 3, false);
        assertEquals(2, manager.builds.get());
        assertEquals(2, manager.getVisibleChunkCount());
    }
}
