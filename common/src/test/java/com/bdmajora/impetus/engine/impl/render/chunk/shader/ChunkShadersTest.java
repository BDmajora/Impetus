package com.bdmajora.impetus.engine.impl.render.chunk.shader;

import com.bdmajora.impetus.engine.impl.gl.shader.GlProgram;
import com.bdmajora.impetus.engine.impl.gl.shader.ShaderBindingContext;
import com.bdmajora.impetus.engine.impl.gl.tessellation.GlPrimitiveType;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.sorting.QuadPrimitiveType;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.ChunkMeshFormats;
import com.bdmajora.testing.Passes;
import com.bdmajora.testing.TestFogService;
import com.bdmajora.testing.TestGl;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.FloatBuffer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChunkShadersTest {
    // A binding context over a linked program where every uniform resolves
    private static ShaderBindingContext context() {
        Mockito.when(TestGl.gl().glGetUniformLocation(Mockito.anyInt(), Mockito.any())).thenReturn(1);
        return GlProgram.builder("test").link(ctx -> ctx);
    }

    @Test
    void fogModesMapFromGlAndUploadTheirUniforms() {
        assertEquals(ChunkFogMode.NONE, ChunkFogMode.fromGLMode(0));
        assertEquals(ChunkFogMode.EXP2, ChunkFogMode.fromGLMode(0x800));
        assertEquals(ChunkFogMode.EXP2, ChunkFogMode.fromGLMode(0x801));
        assertEquals(ChunkFogMode.SMOOTH, ChunkFogMode.fromGLMode(0x2601));
        assertThrows(UnsupportedOperationException.class, () -> ChunkFogMode.fromGLMode(99));
        assertEquals(List.of("USE_FOG", "USE_FOG_EXP2"), ChunkFogMode.EXP2.getDefines());
        ShaderBindingContext context = context();
        ChunkFogMode.NONE.create(context).setup();
        ChunkFogMode.EXP2.create(context).setup();
        Mockito.verify(TestGl.gl()).glUniform1f(1, TestFogService.density);
        ChunkFogMode.SMOOTH.create(context).setup();
        Mockito.verify(TestGl.gl()).glUniform1i(1, TestFogService.shape);
        Mockito.verify(TestGl.gl()).glUniform1f(1, TestFogService.end);
        assertSame(TestFogService.mode, ChunkShaderFogComponent.FOG_SERVICE.getFogMode());
        assertEquals(1f, ChunkShaderFogComponent.FOG_SERVICE.getFogCutoff());
        ChunkShaderComponent.Factory<ChunkShaderFogComponent> bare = ctx -> new ChunkShaderFogComponent.None(ctx);
        assertTrue(bare.getDefines().isEmpty());
    }

    @Test
    void optionsCollectDefinesFromEveryPart() {
        TerrainRenderPass pass = TerrainRenderPass.builder().name("p").pipelineState(TerrainRenderPass.PipelineState.DEFAULT)
                .fragmentDiscard(true).hasNoLightmap(true).vertexType(ChunkMeshFormats.COMPACT).primitiveType(QuadPrimitiveType.TRIANGULATED)
                .extraDefine("EXTRA", "1").build();
        ChunkShaderOptions options = new ChunkShaderOptions(List.of(ChunkFogMode.SMOOTH), pass);
        List<String> defines = options.constants().getDefineStrings();
        assertTrue(defines.contains("#define USE_FOG_SMOOTH"));
        assertTrue(defines.contains("#define USE_FRAGMENT_DISCARD"));
        assertTrue(defines.contains("#define IMPETUS_NO_LIGHTMAP"));
        assertTrue(defines.contains("#define EXTRA 1"));
        assertTrue(defines.contains("#define USE_VERTEX_COMPRESSION"));
        assertFalse(new ChunkShaderOptions(List.of(), Passes.SOLID).constants().getDefineStrings().contains("#define USE_FRAGMENT_DISCARD"));
        assertEquals(0, ChunkShaderBindingPoints.FRAG_COLOR);
        new ChunkShaderBindingPoints();
        assertEquals(2, ChunkShaderTextureSlot.VALUES.length);
    }

    @Test
    void defaultInterfaceUploadsMatricesOffsetsTexturesAndAges() {
        ShaderBindingContext context = context();
        DefaultChunkShaderInterface shader = new DefaultChunkShaderInterface(context, new ChunkShaderOptions(List.of(ChunkFogMode.EXP2), Passes.CUTOUT));
        shader.setupState(Passes.CUTOUT);
        assertEquals(GlPrimitiveType.TRIANGLES, shader.getPrimitiveType());
        shader.setProjectionMatrix(new Matrix4f());
        shader.setModelViewMatrix(new Matrix4f());
        Mockito.verify(TestGl.gl(), Mockito.times(2)).glUniformMatrix4fv(Mockito.eq(1), Mockito.eq(false), Mockito.any(FloatBuffer.class));
        shader.setRegionOffset(1, 2, 3);
        Mockito.verify(TestGl.gl()).glUniform3f(1, 1, 2, 3);
        shader.setTextureSlot(ChunkShaderTextureSlot.BLOCK, 0);
        shader.setTextureSlot(ChunkShaderTextureSlot.LIGHT, 2);
        Mockito.verify(TestGl.gl()).glUniform1i(1, 2);
        shader.setSectionAges(50_000_000_000L, new long[]{0L, 49_999_000_000L});
        Mockito.verify(TestGl.gl()).glUniform1fv(Mockito.eq(1), Mockito.any(FloatBuffer.class));
        shader.restoreState();

        TerrainRenderPass unlit = TerrainRenderPass.builder().name("unlit").pipelineState(TerrainRenderPass.PipelineState.DEFAULT)
                .hasNoLightmap(true).vertexType(ChunkMeshFormats.COMPACT).primitiveType(QuadPrimitiveType.DIRECT).build();
        Mockito.when(TestGl.gl().glGetUniformLocation(Mockito.anyInt(), Mockito.eq("impetus_ChunkAges"))).thenReturn(-1);
        DefaultChunkShaderInterface plain = new DefaultChunkShaderInterface(context, new ChunkShaderOptions(List.of(), unlit));
        plain.setTextureSlot(ChunkShaderTextureSlot.LIGHT, 5);
        plain.setSectionAges(1, new long[]{0});
        Mockito.verify(TestGl.gl(), Mockito.never()).glUniform1i(1, 5);
        ChunkShaderInterface bare = Mockito.mock(ChunkShaderInterface.class, Mockito.CALLS_REAL_METHODS);
        bare.restoreState();
        bare.setSectionAges(0, new long[0]);
    }
}
