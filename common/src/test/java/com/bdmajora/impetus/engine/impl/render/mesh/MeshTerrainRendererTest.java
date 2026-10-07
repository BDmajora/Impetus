package com.bdmajora.impetus.engine.impl.render.mesh;

import com.bdmajora.impetus.engine.impl.common.util.NativeBuffer;
import com.bdmajora.impetus.engine.impl.compat.environment.OsKind;
import com.bdmajora.impetus.engine.impl.gl.shader.ShaderConstants;
import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions;
import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions.MeshStatistics;
import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions.MeshTranslucencySorting;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;
import com.bdmajora.impetus.engine.impl.render.chunk.ChunkRenderMatrices;
import com.bdmajora.impetus.engine.impl.render.chunk.shader.ChunkFogMode;
import com.bdmajora.impetus.engine.impl.render.mesh.region.MeshStoresTest;
import com.bdmajora.impetus.engine.impl.render.mesh.region.SectionGeometry;
import com.bdmajora.impetus.lwjgl.GLExtension;
import com.bdmajora.testing.Sections;
import com.bdmajora.testing.Statics;
import com.bdmajora.testing.TestFogService;
import com.bdmajora.testing.TestGl;
import org.joml.Matrix4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.lwjgl.system.MemoryUtil;

import static org.junit.jupiter.api.Assertions.*;

class MeshTerrainRendererTest {
    private static final ChunkRenderMatrices MATRICES = new ChunkRenderMatrices(new Matrix4f().perspective(1f, 1f, 0.1f, 1000f), new Matrix4f());

    @BeforeEach
    void capable() {
        TestGl.meshCapable();
        Mockito.when(TestGl.gl().isExtensionSupported(Mockito.any())).thenReturn(true);
        Mockito.doAnswer(inv -> {
            inv.<IntBuffer>getArgument(2).put(0, 1);
            return 0x9119;
        }).when(TestGl.gl()).glGetSynci(Mockito.anyLong(), Mockito.anyInt(), Mockito.any());
        // Eight gigabytes free, in kilobytes
        Mockito.when(TestGl.gl().glGetInteger(MeshTerrainRenderer.GPU_MEMORY_AVAILABLE_NVX)).thenReturn(8 * 1024 * 1024);
        Statics.set(MeshShaderSupport.class, "supported", null);
    }

    @AfterEach
    void forget() {
        Statics.set(MeshShaderSupport.class, "supported", null);
        Statics.set(OsKind.class, "CURRENT", OsKind.LINUX);
        Statics.set(MeshTerrainRenderer.class, "activeConfig", null);
    }

    private static MeshTerrainConfig manual(int memoryMB, int keep, int renderDistance) {
        return new MeshTerrainConfig(false, MeshTranslucencySorting.NONE, MeshStatistics.NONE, false, memoryMB, keep, renderDistance);
    }

    private static SectionGeometry geometry() {
        return SectionGeometry.from(MeshStoresTest.mesh(2, ModelQuadFacing.POS_Y));
    }

    @Test
    void buildsFollowTheBackendAndFramesCloseEvenWithoutTranslucency() {
        assertFalse(MeshTerrainRenderer.isActive());
        MeshTerrainRenderer renderer = new MeshTerrainRenderer(manual(4096, 0, 8), 4);
        assertTrue(MeshTerrainRenderer.isActive());
        assertEquals(4096L << 20, renderer.getBudgetBytes());

        renderer.upload(0, 0, 0, geometry());
        renderer.upload(1, 0, 0, null);
        assertEquals(1, renderer.getPipeline().getSections().getRegions().getRegionCount());

        renderer.renderOpaque(Sections.viewport(8, 8, 8), MATRICES, MeshFog.NONE, 8, 8, 8, 640, 480);
        // A second opaque frame without a translucent pass in between closes the first itself
        renderer.renderOpaque(Sections.viewport(8, 8, 8), MATRICES, MeshFog.NONE, 8, 8, 8, 640, 480);
        renderer.renderTranslucent();

        List<String> lines = new ArrayList<>();
        renderer.addDebugStrings(lines);
        assertTrue(lines.get(0).startsWith("Mesh terrain: 0/4096 MB dense"));
        assertEquals("Mesh regions: 1/256", lines.get(1));

        // Without a keep distance a removed CPU section leaves the GPU too
        renderer.remove(0, 0, 0);
        assertEquals(0, renderer.getPipeline().getSections().getRegions().getRegionCount());
        renderer.delete();
        assertFalse(MeshTerrainRenderer.isActive());
    }

    @Test
    void keptRegionsOutliveTheirSectionsAndPushTheFarPlaneOut() {
        assertEquals(128f, MeshTerrainRenderer.extendFarPlane(128f));
        MeshTerrainRenderer renderer = new MeshTerrainRenderer(manual(4096, 32, 8), 4);
        assertEquals(512f, MeshTerrainRenderer.extendFarPlane(128f));
        assertEquals(1024f, MeshTerrainRenderer.extendFarPlane(1024f));
        renderer.upload(0, 0, 0, geometry());
        renderer.remove(0, 0, 0);
        assertEquals(1, renderer.getPipeline().getSections().getRegions().getRegionCount());
        renderer.delete();

        MeshTerrainRenderer everything = new MeshTerrainRenderer(manual(4096, MeshTerrainConfig.MAX_KEEP_DISTANCE, 8), 4);
        assertEquals(4096f, MeshTerrainRenderer.extendFarPlane(128f));
        everything.delete();

        // A keep distance inside the render distance changes nothing
        MeshTerrainRenderer inside = new MeshTerrainRenderer(manual(4096, 4, 8), 4);
        assertEquals(128f, MeshTerrainRenderer.extendFarPlane(128f));
        inside.delete();
    }

    @Test
    void aFullRegionTableAndAnOverBudgetArenaEvict() {
        // 64 MB budget sits inside the 100 MB headroom, so every frame evicts until nothing is left
        MeshTerrainRenderer renderer = new MeshTerrainRenderer(manual(64, 0, 8), 4);
        int max = renderer.getPipeline().getMaxRegions();
        for (int region = 0; region < max; region++) {
            renderer.upload(region * 8, 0, 0, geometry());
        }
        // The table is full, so a section in a new region pushes the farthest one out first
        assertEquals(max - 1, renderer.getPipeline().getSections().getRegions().getRegionCount());
        renderer.upload(-8, 0, 0, geometry());
        assertEquals(max - 1, renderer.getPipeline().getSections().getRegions().getRegionCount());
        // A section joining an existing region needs no room
        renderer.upload(1, 0, 0, geometry());

        renderer.renderOpaque(Sections.viewport(8, 8, 8), MATRICES, MeshFog.NONE, 8, 8, 8, 640, 480);
        renderer.renderTranslucent();
        assertEquals(0, renderer.getPipeline().getSections().getRegions().getRegionCount());
        renderer.delete();
    }

    @Test
    void evictionPrefersRegionsSeenLeastAndPastTheRenderDistance() {
        MeshTerrainRenderer renderer = new MeshTerrainRenderer(manual(4096, 0, 8), 4);
        var regions = renderer.getPipeline().getSections().getRegions();
        int max = renderer.getPipeline().getMaxRegions();

        // Two regions inside the render distance of a camera at the origin, one past it
        renderer.upload(0, 0, 0, geometry());
        renderer.upload(0, 4, 0, geometry());
        renderer.upload(80, 0, 0, geometry());
        int near = 0, within = 1, far = 2;
        // Then fill the table with regions nobody has sampled
        for (int region = 3; region < max - 1; region++) {
            renderer.upload(region * 8 + 1000, 0, 0, geometry());
        }
        assertEquals(max - 1, regions.getRegionCount());

        var tracker = renderer.getPipeline().getVisibilityTracker();
        NativeBuffer bytes = new NativeBuffer(3);
        long address = MemoryUtil.memAddress(bytes.getDirectBuffer());
        MemoryUtil.memPutByte(address, (byte) 1);
        MemoryUtil.memPutByte(address + 1, (byte) 0);
        MemoryUtil.memPutByte(address + 2, (byte) 0);
        for (int frame = 0; frame < 250; frame++) {
            tracker.record(new short[]{(short) near, (short) within, (short) far}, 3, address);
        }

        // The never-visible region past the render distance goes first
        // Checked by position, since the newcomer reuses the evicted region's id
        renderer.upload(-100, 0, 0, geometry());
        assertFalse(regions.hasRegionFor(80, 0, 0));
        // Then the never-visible one inside it, while the visible one stays
        renderer.upload(-200, 0, 0, geometry());
        assertFalse(regions.hasRegionFor(0, 4, 0));
        assertTrue(regions.hasRegionFor(0, 0, 0));
        bytes.free();
        renderer.delete();
    }

    @Test
    void automaticBudgetsFollowFreeVideoMemory() {
        var automatic = new MeshTerrainConfig(false, MeshTranslucencySorting.NONE, MeshStatistics.NONE, true, 2048, 0, 8);
        // Dense arenas cap at three gigabytes, sparse ones take free memory less the reserve
        assertEquals(MeshTerrainRenderer.DENSE_AUTOMATIC_CAP_MB, MeshTerrainRenderer.computeBudgetMB(automatic, false, 0L));
        assertEquals(8192 - 1024 + 100, MeshTerrainRenderer.computeBudgetMB(automatic, true, 100L << 20));
        Mockito.when(TestGl.gl().glGetInteger(MeshTerrainRenderer.GPU_MEMORY_AVAILABLE_NVX)).thenReturn(512 * 1024);
        assertEquals(MeshTerrainRenderer.AUTOMATIC_MINIMUM_MB, MeshTerrainRenderer.computeBudgetMB(automatic, true, 0L));
        Mockito.when(TestGl.gl().isExtensionSupported(GLExtension.NVX_gpu_memory_info)).thenReturn(false);
        assertEquals(2048, MeshTerrainRenderer.computeBudgetMB(automatic, true, 0L));
    }

    @Test
    void sparseAutomaticBudgetsRefreshEveryMinute() {
        Statics.set(OsKind.class, "CURRENT", OsKind.WINDOWS);
        var automatic = new MeshTerrainConfig(false, MeshTranslucencySorting.NONE, MeshStatistics.NONE, true, 2048, 0, 8);
        AtomicLong clock = new AtomicLong();
        MeshTerrainRenderer renderer = new MeshTerrainRenderer(automatic, 4, clock::get);
        assertEquals(7168L << 20, renderer.getBudgetBytes());

        Mockito.when(TestGl.gl().glGetInteger(MeshTerrainRenderer.GPU_MEMORY_AVAILABLE_NVX)).thenReturn(4 * 1024 * 1024);
        renderer.renderOpaque(Sections.viewport(8, 8, 8), MATRICES, MeshFog.NONE, 8, 8, 8, 640, 480);
        assertEquals(7168L << 20, renderer.getBudgetBytes());
        clock.set(MeshTerrainRenderer.BUDGET_REFRESH_NANOS);
        renderer.renderOpaque(Sections.viewport(8, 8, 8), MATRICES, MeshFog.NONE, 8, 8, 8, 640, 480);
        // Free memory plus the one sparse page the arena has committed, less the reserve
        assertEquals(3073L << 20, renderer.getBudgetBytes());
        List<String> lines = new ArrayList<>();
        renderer.addDebugStrings(lines);
        assertTrue(lines.get(0).contains("sparse"));
        renderer.delete();
    }

    @Test
    void regionTablesAreSizedForTheFarthestKeptDistance() {
        assertEquals(256, MeshTerrainRenderer.computeMaxRegions(manual(2048, 0, 8), 4));
        // 32 chunks: 10 regions a side, four tall, a quarter spare
        assertEquals(500, MeshTerrainRenderer.computeMaxRegions(manual(2048, 0, 32), 4));
        assertEquals(500, MeshTerrainRenderer.computeMaxRegions(manual(2048, 32, 8), 4));
        assertEquals(66 * 66 * 4 * 5 / 4, MeshTerrainRenderer.computeMaxRegions(manual(2048, 256, 8), 4));
        assertEquals(256, MeshTerrainRenderer.computeMaxRegions(manual(2048, 0, 2), 0));
        assertEquals(65535, MeshTerrainRenderer.computeMaxRegions(manual(2048, 256, 8), 64));
    }

    @Test
    void configsSnapshotOptionsAndCompileTheirDefines() {
        var settings = new ImpetusGameOptions.MeshTerrainSettings();
        var config = MeshTerrainConfig.from(settings, 12);
        assertTrue(config.temporalCoherence());
        assertEquals(12, config.renderDistance());
        assertFalse(config.keepsBeyondRenderDistance());
        assertFalse(config.keepsEverything());

        ShaderConstants quads = new MeshTerrainConfig(true, MeshTranslucencySorting.QUADS, MeshStatistics.QUADS, true, 1, 0, 8).shaderConstants().build();
        assertEquals(List.of("#define TEXTURE_MAX_SCALE 32768", "#define STATISTICS_REGIONS", "#define STATISTICS_SECTIONS",
                "#define STATISTICS_QUADS", "#define SORT_SECTIONS", "#define SORT_QUADS"), quads.getDefineStrings());
        ShaderConstants sections = new MeshTerrainConfig(true, MeshTranslucencySorting.SECTIONS, MeshStatistics.FRUSTUM, true, 1, 0, 8).shaderConstants().build();
        assertEquals(List.of("#define TEXTURE_MAX_SCALE 32768", "#define SORT_SECTIONS"), sections.getDefineStrings());
    }

    @Test
    void fogIsReadFromTheLiveFogService() {
        TestFogService.mode = ChunkFogMode.EXP2;
        assertEquals(2, MeshFog.from(new TestFogService()).mode());
        TestFogService.mode = ChunkFogMode.NONE;
        assertEquals(0, MeshFog.from(new TestFogService()).mode());
        TestFogService.mode = ChunkFogMode.SMOOTH;
        MeshFog fog = MeshFog.from(new TestFogService());
        assertEquals(1, fog.mode());
        assertEquals(TestFogService.end, fog.end());
        assertEquals(0, MeshFog.NONE.mode());
    }

    @Test
    void aBrokenBackendStaysOffForTheSession() {
        assertTrue(MeshShaderSupport.isSupported());
        MeshShaderSupport.markBroken("program link failed");
        assertFalse(MeshShaderSupport.isSupported());
        assertEquals("program link failed", MeshShaderSupport.getUnsupportedReason());
    }
}
