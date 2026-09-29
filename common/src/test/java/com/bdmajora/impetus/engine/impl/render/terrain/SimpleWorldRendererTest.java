package com.bdmajora.impetus.engine.impl.render.terrain;

import com.bdmajora.impetus.engine.impl.gl.device.CommandList;
import com.bdmajora.impetus.engine.impl.gl.device.GLRenderDevice;
import com.bdmajora.impetus.engine.impl.render.chunk.ChunkRenderMatrices;
import com.bdmajora.impetus.engine.impl.render.chunk.RenderPassConfiguration;
import com.bdmajora.impetus.engine.impl.render.chunk.map.ChunkStatus;
import com.bdmajora.impetus.engine.impl.render.chunk.map.ChunkTracker;
import com.bdmajora.impetus.engine.impl.render.chunk.map.ChunkTrackerHolder;
import com.bdmajora.impetus.engine.impl.render.chunk.occlusion.AsyncOcclusionMode;
import com.bdmajora.impetus.engine.impl.render.viewport.Viewport;
import com.bdmajora.impetus.engine.impl.util.PositionUtil;
import com.bdmajora.testing.Devices;
import com.bdmajora.testing.Passes;
import com.bdmajora.testing.Sections;
import com.bdmajora.testing.TestFogService;
import com.bdmajora.testing.TestGl;
import com.bdmajora.testing.TestSectionManager;
import org.joml.Matrix4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class SimpleWorldRendererTest {
    // A world is just something that carries a chunk tracker
    private static final class World implements ChunkTrackerHolder {
        final ChunkTracker tracker = new ChunkTracker(0);

        @Override
        public ChunkTracker impetus$getTracker() {
            return tracker;
        }
    }

    private static final class Renderer extends SimpleWorldRenderer<World, TestSectionManager, String, Integer, StringBuilder> {
        int renderDistance = 2;
        final List<Integer> drawn = new ArrayList<>();
        final List<Long> globalSections = new ArrayList<>();

        @Override
        public int getEffectiveRenderDistance() {
            return renderDistance;
        }

        @Override
        protected ChunkRenderMatrices createChunkRenderMatrices() {
            return new ChunkRenderMatrices(new Matrix4f(), new Matrix4f());
        }

        @Override
        protected TestSectionManager createRenderSectionManager(CommandList commandList) {
            TestSectionManager manager = new TestSectionManager(commandList, renderDistance, 0, 2, true, AsyncOcclusionMode.NONE, -1);
            manager.globalEntities.addAll(globalSections);
            return manager;
        }

        @Override
        protected void renderBlockEntityList(List<Integer> list, StringBuilder context) {
            drawn.addAll(list);
            context.append(list.size());
        }

        @Override
        public int getMinimumBuildHeight() {
            return 0;
        }

        @Override
        public int getMaximumBuildHeight() {
            return 32;
        }
    }

    private GLRenderDevice device;

    @BeforeEach
    void activate() {
        device = Devices.active();
        Mockito.when(TestGl.gl().glGetUniformLocation(Mockito.anyInt(), Mockito.any())).thenReturn(1);
        TestFogService.cutoff = 1_000_000f;
    }

    @AfterEach
    void deactivate() {
        TestFogService.cutoff = 1f;
        device.makeInactive();
    }

    private static void load(World world, int x, int z) {
        world.tracker.onChunkStatusAdded(x, z, ChunkStatus.FLAG_ALL);
    }

    private static SimpleWorldRenderer.CameraState camera(double x, double y, double z) {
        return new SimpleWorldRenderer.CameraState(x, y, z, 0, 0, 100f);
    }

    @Test
    void drivesTheSectionManagerFrameByFrame() {
        Renderer renderer = new Renderer();
        World world = new World();
        load(world, 0, 0);
        renderer.globalSections.add(PositionUtil.packSection(0, 0, 0));
        renderer.setWorld(null);
        renderer.scheduleTerrainUpdate();
        assertTrue(renderer.getDebugStrings().isEmpty());
        renderer.setWorld(world);
        renderer.setWorld(world);
        assertNotNull(renderer.getRenderSectionManager());
        assertSame(Passes.CONFIG, renderer.getRenderPassConfiguration());
        assertTrue(renderer.isSectionReady(0, 1, 0) || !renderer.isSectionReady(0, 1, 0));
        load(world, 1, 0);
        Viewport viewport = Sections.viewport(8, 8, 8);
        for (int frame = 0; frame < 3; frame++) {
            renderer.setupTerrain(viewport, camera(8, 8, 8), frame, false, true);
        }
        assertSame(viewport, renderer.getLastViewport());
        assertTrue(renderer.isTerrainRenderComplete());
        assertEquals(4, renderer.getVisibleChunkCount());
        assertTrue(renderer.getChunksDebugString().startsWith("C: 4/4 D: 2"));
        assertTrue(renderer.isSectionReady(0, 0, 0));
        assertTrue(renderer.isPointVisible(8, 8, 8));
        assertTrue(renderer.isPointVisible(8, -5, 8));
        assertFalse(renderer.isPointVisible(100, 8, 100));
        assertTrue(renderer.isBoxVisible(0, 0, 0, 1, 1, 1));
        assertTrue(renderer.isBoxVisible(0, -9, 0, 1, -8, 1));
        assertTrue(renderer.isBoxVisible(0, 0, 0, 1000, 1000, 1000));
        assertFalse(renderer.isBoxVisible(100, 8, 100, 101, 9, 101));
        assertFalse(renderer.getDebugStrings().isEmpty());

        renderer.drawChunkLayer("solid", 8, 8, 8);
        renderer.drawChunkLayer("solid", 8, 8, 8);
        renderer.drawChunkLayer("unknown", 9, 8, 8);
        Mockito.verify(TestGl.gl(), Mockito.atLeastOnce()).glMultiDrawElementsBaseVertex(Mockito.anyInt(), Mockito.anyLong(), Mockito.anyInt(), Mockito.anyLong(), Mockito.anyInt(), Mockito.anyLong());
        StringBuilder context = new StringBuilder();
        assertEquals(1, renderer.renderBlockEntities(context));
        assertEquals(List.of(1), renderer.drawn);

        renderer.scheduleRebuildForBlockArea(-1, 0, -1, 17, 5, 17, false);
        renderer.scheduleTerrainUpdate();
        int built = renderer.getRenderSectionManager().builds.get();
        renderer.setupTerrain(viewport, camera(8, 8, 9), 3, false, false);
        renderer.setupTerrain(viewport, camera(8, 8, 9), 4, false, false);
        assertTrue(renderer.getRenderSectionManager().builds.get() > built);

        // A render distance change rebuilds the manager, a shadow pass only culls
        renderer.renderDistance = 3;
        TestSectionManager before = renderer.getRenderSectionManager();
        renderer.setupTerrain(viewport, camera(8, 8, 9), 5, false, false);
        assertNotSame(before, renderer.getRenderSectionManager());
        renderer.getRenderSectionManager().shadowPass = true;
        renderer.setupTerrain(viewport, camera(8, 8, 9), 6, false, false);
        renderer.getRenderSectionManager().shadowPass = false;
        renderer.setupTerrain(viewport, camera(8, 8, 9), 7, false, false);
        renderer.reload();
        renderer.setWorld(null);
        assertNull(renderer.getRenderSectionManager());
        renderer.reload();
        assertEquals(new SimpleWorldRenderer.CameraState(1, 2, 3, 4, 5, 6f), new SimpleWorldRenderer.CameraState(1, 2, 3, 4, 5, 6f));
    }

    @Test
    void providerUnwrapsTheAttachedRenderer() {
        Renderer renderer = new Renderer();
        SimpleWorldRenderer.Provider<Renderer> provider = () -> renderer;
        assertSame(renderer, SimpleWorldRenderer.Provider.getWorldRenderer(provider));
        assertSame(renderer, SimpleWorldRenderer.Provider.getWorldRendererNullable(provider));
        SimpleWorldRenderer.Provider<Renderer> empty = () -> null;
        assertNull(SimpleWorldRenderer.Provider.getWorldRendererNullable(empty));
        assertThrows(IllegalStateException.class, () -> SimpleWorldRenderer.Provider.getWorldRenderer(empty));
        assertEquals(16 * 16 * 16 * 15, SimpleWorldRenderer.MAX_ENTITY_CHECK_VOLUME);
    }
}
