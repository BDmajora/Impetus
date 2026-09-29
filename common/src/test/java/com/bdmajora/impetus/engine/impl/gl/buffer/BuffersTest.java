package com.bdmajora.impetus.engine.impl.gl.buffer;

import com.bdmajora.impetus.engine.impl.common.util.NativeBuffer;
import com.bdmajora.impetus.engine.impl.gl.attribute.GlVertexAttributeFormat;
import com.bdmajora.impetus.engine.impl.gl.attribute.GlVertexFormat;
import com.bdmajora.impetus.engine.impl.gl.util.EnumBitField;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;

class BuffersTest {
    @Test
    void mutableAndImmutableBuffersTrackTheirState() {
        GlMutableBuffer mutable = new GlMutableBuffer();
        assertTrue(mutable.handle() > 0);
        assertEquals(0, mutable.getSize());
        mutable.setSize(64);
        assertEquals(64, mutable.getSize());
        assertNull(mutable.getActiveMapping());
        int handle = mutable.handle();
        mutable.delete();
        Mockito.verify(TestGl.gl()).glDeleteBuffers(handle);
        mutable.delete();
        assertThrows(IllegalStateException.class, mutable::handle);
        EnumBitField<GlBufferStorageFlags> flags = EnumBitField.of(GlBufferStorageFlags.MAP_WRITE);
        GlImmutableBuffer immutable = new GlImmutableBuffer(flags);
        assertSame(flags, immutable.getFlags());
        immutable.destroy();
        assertThrows(IllegalStateException.class, immutable::handle);
    }

    @Test
    void mappingsCopyIntoMappedMemory() {
        GlMutableBuffer buffer = new GlMutableBuffer();
        ByteBuffer map = TestGl.gl().memCalloc(16);
        GlBufferMapping mapping = new GlBufferMapping(buffer, map);
        buffer.setActiveMapping(mapping);
        assertSame(mapping, buffer.getActiveMapping());
        assertSame(buffer, mapping.getBufferObject());
        assertSame(map, mapping.getMemoryBuffer());
        ByteBuffer data = TestGl.gl().memAlloc(4);
        data.order(ByteOrder.nativeOrder()).putInt(0, 0x0A0B0C0D);
        mapping.write(data, 8);
        assertEquals(0x0A0B0C0D, map.order(ByteOrder.nativeOrder()).getInt(8));
        assertFalse(mapping.isDisposed());
        mapping.dispose();
        assertTrue(mapping.isDisposed());
        TestGl.gl().memFree(map);
        TestGl.gl().memFree(data);
    }

    @Test
    void enumsExposeTheirGlConstants() {
        assertEquals(0x8892, GlBufferTarget.ARRAY_BUFFER.getTargetParameter());
        assertEquals(0x8894, GlBufferTarget.ARRAY_BUFFER.getBindingParameter());
        assertEquals(7, GlBufferTarget.COUNT);
        assertEquals(0x88E4, GlBufferUsage.STATIC_DRAW.getId());
        assertEquals(1, GlBufferMapFlags.READ.getBits());
        assertEquals(0x40, GlBufferStorageFlags.PERSISTENT.getBits());
    }

    @Test
    void indexedVertexDataFreesBothBuffers() {
        GlVertexFormat format = GlVertexFormat.builder(4).addElement("a", 0, GlVertexAttributeFormat.FLOAT, 1, false, false).build();
        NativeBuffer vertices = new NativeBuffer(8);
        NativeBuffer indices = new NativeBuffer(8);
        IndexedVertexData data = new IndexedVertexData(format, vertices, indices);
        assertSame(format, data.vertexFormat());
        assertSame(vertices, data.vertexBuffer());
        assertSame(indices, data.indexBuffer());
        data.delete();
        assertThrows(IllegalStateException.class, vertices::getDirectBuffer);
        assertThrows(IllegalStateException.class, indices::getDirectBuffer);
    }
}
