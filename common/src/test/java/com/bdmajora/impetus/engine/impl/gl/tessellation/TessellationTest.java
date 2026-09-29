package com.bdmajora.impetus.engine.impl.gl.tessellation;

import com.bdmajora.impetus.engine.impl.gl.array.GlVertexArray;
import com.bdmajora.impetus.engine.impl.gl.attribute.GlVertexAttribute;
import com.bdmajora.impetus.engine.impl.gl.attribute.GlVertexAttributeBinding;
import com.bdmajora.impetus.engine.impl.gl.attribute.GlVertexAttributeFormat;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferTarget;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlMutableBuffer;
import com.bdmajora.impetus.engine.impl.gl.device.CommandList;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

class TessellationTest {
    @Test
    void vertexArrayTessellationRecordsAttributesOnce() {
        CommandList list = Mockito.mock(CommandList.class);
        GlVertexArray array = new GlVertexArray();
        GlMutableBuffer vertices = new GlMutableBuffer();
        GlMutableBuffer indices = new GlMutableBuffer();
        GlVertexAttributeBinding floats = new GlVertexAttributeBinding(0, new GlVertexAttribute(GlVertexAttributeFormat.FLOAT, "pos", 3, false, 0, 16, false));
        GlVertexAttributeBinding ints = new GlVertexAttributeBinding(1, new GlVertexAttribute(GlVertexAttributeFormat.UNSIGNED_INT, "light", 1, false, 12, 16, true));
        TessellationBinding vertexBinding = TessellationBinding.forVertexBuffer(vertices, new GlVertexAttributeBinding[]{floats, ints});
        TessellationBinding indexBinding = TessellationBinding.forElementBuffer(indices);
        assertEquals(GlBufferTarget.ARRAY_BUFFER, vertexBinding.target());
        assertEquals(GlBufferTarget.ELEMENT_BUFFER, indexBinding.target());
        assertEquals(0, indexBinding.attributeBindings().length);
        assertThrows(NullPointerException.class, () -> TessellationBinding.forVertexBuffer(vertices, null));

        GlVertexArrayTessellation tessellation = new GlVertexArrayTessellation(array, new TessellationBinding[]{vertexBinding, indexBinding});
        tessellation.init(list);
        Mockito.verify(list, Mockito.times(1)).bindVertexArray(array);
        Mockito.verify(list).bindBuffer(GlBufferTarget.ARRAY_BUFFER, vertices);
        Mockito.verify(list).bindBuffer(GlBufferTarget.ELEMENT_BUFFER, indices);
        Mockito.verify(TestGl.gl()).glVertexAttribPointer(0, 3, GlVertexAttributeFormat.FLOAT.typeId(), false, 16, 0);
        Mockito.verify(TestGl.gl()).glVertexAttribIPointer(1, 1, GlVertexAttributeFormat.UNSIGNED_INT.typeId(), 16, 12);
        Mockito.verify(TestGl.gl(), Mockito.times(2)).glEnableVertexAttribArray(Mockito.anyInt());
        Mockito.verify(list).unbindVertexArray();
        tessellation.delete(list);
        Mockito.verify(list).deleteVertexArray(array);
    }

    @Test
    void enumsExposeGlConstants() {
        assertEquals(4, GlPrimitiveType.TRIANGLES.getId());
        assertEquals(7, GlPrimitiveType.QUADS.getId());
        assertEquals(0x1405, GlIndexType.UNSIGNED_INT.getFormatId());
        assertEquals(2, GlIndexType.UNSIGNED_SHORT.getStride());
        assertEquals(0, GlVertexArray.NULL_ARRAY_ID);
        GlVertexArray array = new GlVertexArray();
        int handle = array.handle();
        array.delete();
        Mockito.verify(TestGl.gl()).glDeleteVertexArrays(handle);
    }
}
