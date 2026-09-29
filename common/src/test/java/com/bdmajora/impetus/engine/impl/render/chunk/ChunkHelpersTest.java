package com.bdmajora.impetus.engine.impl.render.chunk;

import com.bdmajora.impetus.engine.api.util.ColorABGR;
import com.bdmajora.impetus.engine.impl.gl.device.CommandList;
import com.bdmajora.impetus.engine.impl.gl.device.GLRenderDevice;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.sorting.QuadPrimitiveType;
import com.bdmajora.testing.Devices;
import org.joml.Matrix4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import static org.junit.jupiter.api.Assertions.*;

class ChunkHelpersTest {
    private GLRenderDevice device;

    @BeforeEach
    void activate() {
        device = Devices.active();
    }

    @AfterEach
    void deactivate() {
        device.makeInactive();
        ChunkColorWriter.SeparateAoState.set(false);
    }

    @Test
    void sharedIndexBufferGrowsOnDemand() {
        CommandList commands = device.createCommandList();
        SharedQuadIndexBuffer buffer = new SharedQuadIndexBuffer(commands, QuadPrimitiveType.TRIANGULATED);
        assertNotNull(buffer.getBufferObject());
        buffer.ensureCapacity(commands, 6 * 100);
        long size = buffer.getBufferObject() instanceof com.bdmajora.impetus.engine.impl.gl.buffer.GlMutableBuffer m ? m.getSize() : 0;
        assertTrue(size >= 100 * 24);
        buffer.ensureCapacity(commands, 6 * 100);
        buffer.ensureCapacity(commands, 6 * 100000);
        buffer.delete(commands);
        assertThrows(IllegalStateException.class, () -> buffer.getBufferObject().handle());
    }

    @Test
    void colourWritersFoldAoDifferently() {
        int white = ColorABGR.pack(255, 255, 255, 255);
        assertEquals(ChunkColorWriter.IMPETUS, ChunkColorWriter.active());
        // 0.5 becomes the byte 127, and 255 * 127 >> 8 truncates to 126
        assertEquals(ColorABGR.pack(126, 126, 126, 255), ChunkColorWriter.IMPETUS.writeColor(white, 0.5f));
        ChunkColorWriter.SeparateAoState.set(true);
        assertTrue(ChunkColorWriter.SeparateAoState.isEnabled());
        assertEquals(ChunkColorWriter.SEPARATE_AO, ChunkColorWriter.active());
        assertEquals(ColorABGR.pack(255, 255, 255, 127), ChunkColorWriter.SEPARATE_AO.writeColor(white, 0.5f));
    }

    @Test
    void matricesCopyFromBuffers() {
        Matrix4f projection = new Matrix4f().perspective(1f, 1f, 0.1f, 10f);
        Matrix4f modelView = new Matrix4f().translate(1, 2, 3);
        // JOML reads NIO buffers through Unsafe, so a heap buffer here takes the JVM down
        FloatBuffer p = ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder()).asFloatBuffer();
        FloatBuffer m = ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder()).asFloatBuffer();
        projection.get(p);
        modelView.get(m);
        ChunkRenderMatrices matrices = new ChunkRenderMatrices(p, m);
        assertEquals(projection, matrices.projection());
        assertEquals(modelView, matrices.modelView());
        assertEquals(matrices, new ChunkRenderMatrices(projection, modelView));
    }
}
