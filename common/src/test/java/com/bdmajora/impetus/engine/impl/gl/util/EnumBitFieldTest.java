package com.bdmajora.impetus.engine.impl.gl.util;

import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferMapFlags;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EnumBitFieldTest {
    @Test
    void combinesBitsAndAnswersMembership() {
        EnumBitField<GlBufferMapFlags> field = EnumBitField.of(GlBufferMapFlags.READ, GlBufferMapFlags.WRITE);
        assertEquals(GlBufferMapFlags.READ.getBits() | GlBufferMapFlags.WRITE.getBits(), field.getBitField());
        assertTrue(field.contains(GlBufferMapFlags.READ));
        assertFalse(field.contains(GlBufferMapFlags.PERSISTENT));
        VertexRange range = new VertexRange(3, 4);
        assertEquals(3, range.vertexStart());
        assertEquals(4, range.vertexCount());
    }
}
