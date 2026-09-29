package com.bdmajora.impetus.engine.impl.render.mesh.renderers;

import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class RasterizersTest {
    @BeforeEach
    void capable() {
        TestGl.meshCapable();
    }

    @Test
    void eachRasterizerBindsItsProgramAndDraws() {
        RegionRasterizer regions = new RegionRasterizer();
        regions.raster(3);
        regions.delete();
        SectionRasterizer sections = new SectionRasterizer();
        sections.raster(3);
        sections.delete();
        TerrainRasterizer terrain = new TerrainRasterizer();
        terrain.raster(3, 0x4000L);
        terrain.delete();
        Mockito.verify(TestGl.gl(), Mockito.times(2)).glDrawMeshTasksNV(0, 3);
        Mockito.verify(TestGl.gl()).glMultiDrawMeshTasksIndirectNV(0L, 3, 0);
        Mockito.verify(TestGl.gl(), Mockito.times(3)).glDeleteProgram(Mockito.anyInt());
    }
}
