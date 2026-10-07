package com.bdmajora.impetus.engine.impl.render.mesh.renderers;

import com.bdmajora.impetus.engine.impl.gl.shader.ShaderConstants;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class RasterizersTest {
    private static final ShaderConstants CONSTANTS = ShaderConstants.builder().add("TEXTURE_MAX_SCALE", "32768").build();

    @BeforeEach
    void capable() {
        TestGl.meshCapable();
    }

    @Test
    void eachRasterizerBindsItsProgramAndDraws() {
        RegionRasterizer regions = new RegionRasterizer(CONSTANTS);
        regions.raster(3);
        regions.delete();
        SectionRasterizer sections = new SectionRasterizer(CONSTANTS);
        sections.raster(3);
        sections.delete();
        TerrainRasterizer terrain = new TerrainRasterizer("mesh/terrain", CONSTANTS);
        terrain.raster(3, 0x4000L);
        terrain.delete();
        TranslucentRasterizer translucent = new TranslucentRasterizer(CONSTANTS);
        translucent.raster(2, 0x8000L);
        translucent.delete();
        SectionSorter sorter = new SectionSorter(CONSTANTS);
        sorter.dispatch(5);
        sorter.delete();
        Mockito.verify(TestGl.gl(), Mockito.times(2)).glDrawMeshTasksNV(0, 3);
        Mockito.verify(TestGl.gl()).glMultiDrawMeshTasksIndirectNV(0L, 3, 0);
        Mockito.verify(TestGl.gl()).glMultiDrawMeshTasksIndirectNV(0L, 2, 0);
        // Eight bytes per command, bound by address before each multi-draw
        Mockito.verify(TestGl.gl()).glBufferAddressRangeNV(Mockito.anyInt(), Mockito.eq(0), Mockito.eq(0x8000L), Mockito.eq(16L));
        Mockito.verify(TestGl.gl()).glDispatchCompute(5, 1, 1);
        Mockito.verify(TestGl.gl(), Mockito.times(5)).glDeleteProgram(Mockito.anyInt());
    }
}
