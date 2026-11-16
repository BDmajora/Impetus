package com.bdmajora.impetus.engine.impl.render.chunk.shader;

import com.bdmajora.impetus.engine.impl.render.shader.ShaderLoader;
import com.bdmajora.testing.Statics;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL31;
import org.lwjgl.system.MemoryUtil;
import org.mockito.Mockito;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;

class TerrainDynamicLightsTest {
    @BeforeEach
    @AfterEach
    void reset() {
        TerrainDynamicLights.delete();
        TerrainDynamicLights.setLights(new double[0], 0);
        TerrainDynamicLights.setShaderApplied(true);
    }

    @Test
    void publishedLightsAreCheckedCappedAndCounted() {
        assertThrows(NullPointerException.class, () -> TerrainDynamicLights.setLights(null, 0));
        assertThrows(IllegalArgumentException.class, () -> TerrainDynamicLights.setLights(new double[4], -1));
        assertThrows(IllegalArgumentException.class, () -> TerrainDynamicLights.setLights(new double[7], 2));
        assertThrows(IllegalArgumentException.class, () -> TerrainDynamicLights.setLights(new double[4], Integer.MAX_VALUE));
        // More lights than the block holds keep the first MAX_LIGHTS, which the caller sorted nearest first
        double[] many = new double[(TerrainDynamicLights.MAX_LIGHTS + 3) * TerrainDynamicLights.STRIDE];
        TerrainDynamicLights.setLights(many, TerrainDynamicLights.MAX_LIGHTS + 3);
        assertEquals(TerrainDynamicLights.MAX_LIGHTS, TerrainDynamicLights.lightCount());
        TerrainDynamicLights.setLights(new double[] {1, 2, 3, 14}, 1);
        assertEquals(1, TerrainDynamicLights.lightCount());
    }

    @Test
    void theBlockHoldsLightsRelativeToTheOriginThenTheCount() {
        TerrainDynamicLights.setLights(new double[] {30_000_000.25, 64.5, -30_000_000.75, 14, 1, 2, 3, 7}, 2);
        ByteBuffer block = MemoryUtil.memCalloc(TerrainDynamicLights.BYTES);
        try {
            long ptr = MemoryUtil.memAddress(block);
            // Garbage left from an earlier frame is overwritten, including the unused slots
            MemoryUtil.memSet(ptr, 0x7F, TerrainDynamicLights.BYTES);
            TerrainDynamicLights.write(ptr, 30_000_000.0, 60.0, -30_000_001.0);
            // Far from the world origin the subtraction happens in double, so the fraction survives
            assertEquals(0.25f, block.getFloat(0));
            assertEquals(4.5f, block.getFloat(4));
            assertEquals(0.25f, block.getFloat(8));
            assertEquals(14.0f, block.getFloat(12));
            assertEquals((float) (1 - 30_000_000.0), block.getFloat(16));
            assertEquals(7.0f, block.getFloat(28));
            for (int i = 32; i < TerrainDynamicLights.MAX_LIGHTS * 16; i += 4) {
                assertEquals(0, block.getInt(i), "byte " + i);
            }
            int tail = TerrainDynamicLights.MAX_LIGHTS * 16;
            assertEquals(2, block.getInt(tail));
            assertEquals(0, block.getInt(tail + 4));
            assertEquals(0L, block.getLong(tail + 8));
        } finally {
            MemoryUtil.memFree(block);
        }
    }

    @Test
    void theRasterBufferUploadsOnlyWhenTheLightsOrTheOriginMove() {
        Mockito.when(TestGl.gl().glGenBuffers()).thenReturn(77);
        TerrainDynamicLights.prepare(1, 2, 3);
        Mockito.verify(TestGl.gl()).glBufferData(eq(GL31.GL_UNIFORM_BUFFER), any(ByteBuffer.class), eq(GL15.GL_DYNAMIC_DRAW));
        Mockito.verify(TestGl.gl()).glBindBufferBase(GL31.GL_UNIFORM_BUFFER, TerrainDynamicLights.BINDING, 77);
        // The same frame again only rebinds, since a pack could have used the binding point in between
        TerrainDynamicLights.prepare(1, 2, 3);
        Mockito.verify(TestGl.gl(), Mockito.times(1)).glBufferData(anyInt(), any(ByteBuffer.class), anyInt());
        Mockito.verify(TestGl.gl(), Mockito.times(2)).glBindBufferBase(GL31.GL_UNIFORM_BUFFER, TerrainDynamicLights.BINDING, 77);
        // A shadow pass from another origin, or freshly published lights, upload again
        TerrainDynamicLights.prepare(1, 2, 4);
        TerrainDynamicLights.prepare(1, 3, 4);
        TerrainDynamicLights.prepare(2, 3, 4);
        TerrainDynamicLights.setLights(new double[] {0, 0, 0, 9}, 1);
        TerrainDynamicLights.prepare(2, 3, 4);
        Mockito.verify(TestGl.gl(), Mockito.times(5)).glBufferData(anyInt(), any(ByteBuffer.class), anyInt());
        Mockito.verify(TestGl.gl(), Mockito.times(1)).glGenBuffers();

        // Released, the next draw makes a fresh buffer and uploads into it; a second release is harmless
        TerrainDynamicLights.delete();
        Mockito.verify(TestGl.gl()).glDeleteBuffers(77);
        TerrainDynamicLights.delete();
        Mockito.verify(TestGl.gl(), Mockito.times(1)).glDeleteBuffers(anyInt());
        Mockito.when(TestGl.gl().glGenBuffers()).thenReturn(78);
        TerrainDynamicLights.prepare(2, 3, 4);
        Mockito.verify(TestGl.gl()).glBindBufferBase(GL31.GL_UNIFORM_BUFFER, TerrainDynamicLights.BINDING, 78);
        Mockito.verify(TestGl.gl(), Mockito.times(6)).glBufferData(anyInt(), any(ByteBuffer.class), anyInt());
    }

    @Test
    void theShaderSwitchAndTheSharedGlslAgree() {
        assertTrue(TerrainDynamicLights.isShaderApplied());
        TerrainDynamicLights.setShaderApplied(false);
        assertFalse(TerrainDynamicLights.isShaderApplied());
        // The engine include and the pack prologue declare the same members and the same function
        String include = ShaderLoader.getShaderSource("impetus:include/dynamic_light.glsl");
        assertTrue(include.contains(TerrainDynamicLights.BLOCK_MEMBERS), include);
        assertTrue(include.contains(TerrainDynamicLights.LEVEL_FUNCTION), include);
        assertTrue(include.contains("binding = " + TerrainDynamicLights.BINDING));
        assertTrue(include.contains("uniform " + TerrainDynamicLights.BLOCK_NAME + " {"));
        assertTrue(TerrainDynamicLights.UNBOUND_DECLARATIONS.startsWith("layout(std140) uniform " + TerrainDynamicLights.BLOCK_NAME));
        assertTrue(TerrainDynamicLights.UNBOUND_DECLARATIONS.endsWith(TerrainDynamicLights.LEVEL_FUNCTION));
        // 7.75 squared, the engine's fade radius
        assertTrue(TerrainDynamicLights.LEVEL_FUNCTION.contains(String.valueOf(7.75 * 7.75)));
        assertNotNull(Statics.get(TerrainDynamicLights.class, "LIGHTS"));
    }
}
