package com.bdmajora.impetus.engine.impl.render.chunk.terrain.material;

import com.bdmajora.impetus.engine.impl.render.chunk.compile.sorting.QuadPrimitiveType;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.material.parameters.AlphaCutoffParameter;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.material.parameters.MaterialParameters;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.ChunkMeshFormats;
import com.bdmajora.testing.Passes;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class MaterialTest {
    @Test
    void materialsPackTheirParameters() {
        Material cutout = new Material(Passes.CUTOUT, AlphaCutoffParameter.HALF, true);
        assertEquals(MaterialParameters.pack(AlphaCutoffParameter.HALF, true), cutout.bits());
        assertEquals(0b101, cutout.bits());
        assertEquals(cutout, new Material(Passes.CUTOUT, AlphaCutoffParameter.HALF, true));
        assertNotEquals(cutout, new Material(Passes.CUTOUT, AlphaCutoffParameter.ONE, true));
        assertNotEquals(cutout, null);
        assertNotEquals(cutout, "x");
        assertEquals(cutout.hashCode(), new Material(Passes.CUTOUT, AlphaCutoffParameter.HALF, true).hashCode());
        assertTrue(cutout.toString().contains("cutout"));
        assertThrows(IllegalArgumentException.class, () -> new Material(Passes.SOLID, AlphaCutoffParameter.HALF, true));
        assertEquals(AlphaCutoffParameter.ONE_TENTH, AlphaCutoffParameter.valueOf(0.1f));
        assertEquals(0.5f, AlphaCutoffParameter.HALF.cutoff());
        assertThrows(IllegalArgumentException.class, () -> AlphaCutoffParameter.valueOf(0.3f));
        new MaterialParameters();
    }

    @Test
    void passesCarryTheirConfiguration() {
        AtomicInteger setups = new AtomicInteger();
        TerrainRenderPass pass = TerrainRenderPass.builder()
                .name("custom")
                .pipelineState(new TerrainRenderPass.PipelineState() {
                    @Override
                    public void setup() {
                        setups.incrementAndGet();
                    }

                    @Override
                    public void clear() {
                        setups.decrementAndGet();
                    }
                })
                .useReverseOrder(true)
                .fragmentDiscard(true)
                .useTranslucencySorting(true)
                .hasNoLightmap(true)
                .vertexType(ChunkMeshFormats.VANILLA_LIKE)
                .primitiveType(QuadPrimitiveType.DIRECT)
                .extraDefine("FOO", "1")
                .build();
        assertEquals("custom", pass.name());
        assertTrue(pass.isReverseOrder());
        assertTrue(pass.isSorted());
        assertTrue(pass.hasNoLightmap());
        assertTrue(pass.supportsFragmentDiscard());
        assertSame(QuadPrimitiveType.DIRECT, pass.primitiveType());
        assertSame(ChunkMeshFormats.VANILLA_LIKE, pass.vertexType());
        assertEquals(Map.of("FOO", "1"), pass.extraDefines());
        pass.startDrawing();
        assertEquals(1, setups.get());
        pass.endDrawing();
        assertEquals(0, setups.get());
        assertEquals("TerrainRenderPass[name=custom]", pass.toString());
        assertNotEquals(pass, Passes.SOLID);
        assertEquals(Passes.SOLID, Passes.pass("solid2", false, false, ChunkMeshFormats.COMPACT));
        assertEquals(Passes.SOLID.hashCode(), Passes.pass("solid2", false, false, ChunkMeshFormats.COMPACT).hashCode());
        TerrainRenderPass.PipelineState.DEFAULT.setup();
        TerrainRenderPass.PipelineState.DEFAULT.clear();
        assertThrows(IllegalArgumentException.class, () -> TerrainRenderPass.builder().name("").vertexType(ChunkMeshFormats.COMPACT).primitiveType(QuadPrimitiveType.DIRECT).build());
        assertThrows(NullPointerException.class, () -> TerrainRenderPass.builder().name("x").primitiveType(QuadPrimitiveType.DIRECT).build());
        assertThrows(NullPointerException.class, () -> TerrainRenderPass.builder().name("x").vertexType(ChunkMeshFormats.COMPACT).build());
        assertNotNull(TerrainRenderPass.builder().toString());
    }
}
