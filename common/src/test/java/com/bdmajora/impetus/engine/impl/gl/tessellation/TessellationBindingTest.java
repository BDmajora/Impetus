package com.bdmajora.impetus.engine.impl.gl.tessellation;

import com.bdmajora.impetus.engine.impl.gl.array.GlVertexArray;
import com.bdmajora.impetus.engine.impl.gl.attribute.GlVertexAttribute;
import com.bdmajora.impetus.engine.impl.gl.attribute.GlVertexAttributeBinding;
import com.bdmajora.impetus.engine.impl.gl.attribute.GlVertexAttributeFormat;
import com.bdmajora.impetus.engine.impl.gl.attribute.GlVertexFormat;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferTarget;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlMutableBuffer;
import com.bdmajora.impetus.engine.impl.gl.device.CommandList;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

// Tests binding construction and tessellation setup refusals.
class TessellationBindingTest {
    // Returns an attribute binding at an index with a divisor.
    private static GlVertexAttributeBinding binding(int index, int divisor) {
        return new GlVertexAttributeBinding(index, new GlVertexAttribute(GlVertexAttributeFormat.FLOAT, "a" + index, 1, false, 0, 4, false), divisor);
    }

    // Rejects null parts of a binding.
    @Test
    void recordRejectsNulls() {
        GlMutableBuffer buffer = new GlMutableBuffer();
        GlVertexAttributeBinding[] none = new GlVertexAttributeBinding[0];
        assertThrows(NullPointerException.class, () -> new TessellationBinding(null, buffer, none));
        assertThrows(NullPointerException.class, () -> new TessellationBinding(GlBufferTarget.ARRAY_BUFFER, buffer, null));
        assertThrows(NullPointerException.class, () -> new TessellationBinding(GlBufferTarget.ARRAY_BUFFER, buffer, new GlVertexAttributeBinding[]{null}));
        assertThrows(NullPointerException.class, () -> TessellationBinding.forVertexBuffer(buffer, (GlVertexAttributeBinding[]) null));
        assertThrows(NullPointerException.class, () -> TessellationBinding.forVertexBuffer(buffer, (GlVertexFormat) null, 0, 0));
    }

    // Numbers format attributes from the first index and applies the divisor.
    @Test
    void formatBindingNumbersAttributes() {
        GlMutableBuffer buffer = new GlMutableBuffer();
        GlVertexFormat format = GlVertexFormat.builder(8)
                .addElement("p", 0, GlVertexAttributeFormat.FLOAT, 2, false, false)
                .build();
        TessellationBinding plain = TessellationBinding.forVertexBuffer(buffer, format);
        assertEquals(1, plain.attributeBindings().length);
        assertEquals(0, plain.attributeBindings()[0].getIndex());
        assertEquals(0, plain.attributeBindings()[0].getDivisor());

        TessellationBinding shifted = TessellationBinding.forVertexBuffer(buffer, format, 5, 2);
        assertEquals(5, shifted.attributeBindings()[0].getIndex());
        assertEquals(2, shifted.attributeBindings()[0].getDivisor());
        assertSame(buffer, shifted.buffer());
        assertEquals(GlBufferTarget.ARRAY_BUFFER, shifted.target());
    }

    // Rejects a null array or null binding and copies the array.
    @Test
    void tessellationValidatesAndCopies() {
        GlVertexArray array = new GlVertexArray();
        TessellationBinding element = TessellationBinding.forElementBuffer(new GlMutableBuffer());
        assertThrows(NullPointerException.class, () -> new GlVertexArrayTessellation(array, null));
        assertThrows(NullPointerException.class, () -> new GlVertexArrayTessellation(array, new TessellationBinding[]{element, null}));
        assertThrows(NullPointerException.class, () -> new GlVertexArrayTessellation(null, new TessellationBinding[0]));

        TessellationBinding[] source = {element};
        GlVertexArrayTessellation tessellation = new GlVertexArrayTessellation(array, source);
        source[0] = null;
        assertSame(element, tessellation.bindings[0]);
    }

    // Binds only the buffers when no attributes exist and sets divisors when nonzero.
    @Test
    void divisorsAreSetOnlyWhenNonzero() {
        CommandList list = Mockito.mock(CommandList.class);
        GlMutableBuffer buffer = new GlMutableBuffer();
        TessellationBinding vertex = TessellationBinding.forVertexBuffer(buffer, new GlVertexAttributeBinding[]{binding(0, 0), binding(1, 3)});
        GlVertexArrayTessellation tessellation = new GlVertexArrayTessellation(new GlVertexArray(), new TessellationBinding[]{vertex});
        tessellation.init(list);
        Mockito.verify(TestGl.gl(), Mockito.never()).glVertexAttribDivisor(0, 0);
        Mockito.verify(TestGl.gl()).glVertexAttribDivisor(1, 3);
        Mockito.verify(TestGl.gl()).glEnableVertexAttribArray(0);
        Mockito.verify(TestGl.gl()).glEnableVertexAttribArray(1);
    }

    // Accepts an empty binding list.
    @Test
    void emptyTessellationDoesNothing() {
        CommandList list = Mockito.mock(CommandList.class);
        GlVertexArray array = new GlVertexArray();
        new GlVertexArrayTessellation(array, new TessellationBinding[0]).init(list);
        Mockito.verify(list).bindVertexArray(array);
        Mockito.verify(list, Mockito.never()).bindBuffer(Mockito.any(), Mockito.any());
        Mockito.verify(list).unbindVertexArray();
    }

    // Reports every index and primitive constant.
    @Test
    void enumConstants() {
        assertEquals(0x1401, GlIndexType.UNSIGNED_BYTE.getFormatId());
        assertEquals(0x1403, GlIndexType.UNSIGNED_SHORT.getFormatId());
        assertEquals(0x1405, GlIndexType.UNSIGNED_INT.getFormatId());
        assertEquals(1, GlIndexType.UNSIGNED_BYTE.getStride());
        assertEquals(2, GlIndexType.UNSIGNED_SHORT.getStride());
        assertEquals(4, GlIndexType.UNSIGNED_INT.getStride());
        int[] expected = {0, 1, 3, 2, 4, 5, 6, 0xA, 0xB, 0xC, 0xD, 0xE, 7};
        GlPrimitiveType[] types = GlPrimitiveType.values();
        assertEquals(expected.length, types.length);
        for (int i = 0; i < types.length; i++) {
            assertEquals(expected[i], types[i].getId(), types[i].name());
        }
    }

    // Unbinds the vertex array when attribute setup fails and allows a retry.
    @Test
    void initUnbindsWhenAttributeSetupFails() {
        CommandList list = Mockito.mock(CommandList.class);
        GlVertexArray array = new GlVertexArray();
        TessellationBinding vertex = TessellationBinding.forVertexBuffer(new GlMutableBuffer(), new GlVertexAttributeBinding[]{binding(0, 0)});
        GlVertexArrayTessellation tessellation = new GlVertexArrayTessellation(array, new TessellationBinding[]{vertex});
        Mockito.doThrow(new IllegalStateException("gl")).doNothing().when(TestGl.gl()).glEnableVertexAttribArray(0);
        assertThrows(IllegalStateException.class, () -> tessellation.init(list));
        Mockito.verify(list).unbindVertexArray();
        tessellation.init(list);
        Mockito.verify(list, Mockito.times(2)).bindVertexArray(array);
        Mockito.verify(list, Mockito.times(2)).unbindVertexArray();
    }

    // Unbinds the vertex array when binding a buffer fails.
    @Test
    void initUnbindsWhenBufferBindFails() {
        CommandList list = Mockito.mock(CommandList.class);
        Mockito.doThrow(new IllegalArgumentException("bind")).when(list).bindBuffer(Mockito.any(), Mockito.any());
        GlVertexArrayTessellation tessellation = new GlVertexArrayTessellation(new GlVertexArray(),
                new TessellationBinding[]{TessellationBinding.forElementBuffer(new GlMutableBuffer())});
        assertThrows(IllegalArgumentException.class, () -> tessellation.init(list));
        Mockito.verify(list).unbindVertexArray();
    }

    // Records attributes again on repeated init calls.
    @Test
    void repeatedInitRecordsEachTime() {
        CommandList list = Mockito.mock(CommandList.class);
        TessellationBinding vertex = TessellationBinding.forVertexBuffer(new GlMutableBuffer(), new GlVertexAttributeBinding[]{binding(2, 1)});
        GlVertexArrayTessellation tessellation = new GlVertexArrayTessellation(new GlVertexArray(), new TessellationBinding[]{vertex});
        for (int i = 0; i < 3; i++) {
            tessellation.init(list);
        }
        Mockito.verify(TestGl.gl(), Mockito.times(3)).glEnableVertexAttribArray(2);
        Mockito.verify(TestGl.gl(), Mockito.times(3)).glVertexAttribDivisor(2, 1);
    }

    // Shares one empty attribute array between element bindings and accepts a null buffer.
    @Test
    void elementBindingsShareEmptyAttributes() {
        TessellationBinding first = TessellationBinding.forElementBuffer(new GlMutableBuffer());
        TessellationBinding second = TessellationBinding.forElementBuffer(null);
        assertSame(first.attributeBindings(), second.attributeBindings());
        assertEquals(0, second.attributeBindings().length);
        assertNull(second.buffer());
        assertEquals(GlBufferTarget.ELEMENT_BUFFER, second.target());
    }

    // Names the format in the null refusal and rejects it before reading other arguments.
    @Test
    void nullFormatIsNamed() {
        NullPointerException error = assertThrows(NullPointerException.class,
                () -> TessellationBinding.forVertexBuffer(null, (GlVertexFormat) null, -1, -1));
        assertEquals("format", error.getMessage());
    }

    // Rejects negative indexes and divisors and an index that overflows the int range.
    @Test
    void formatIndexAndDivisorLimits() {
        GlMutableBuffer buffer = new GlMutableBuffer();
        GlVertexFormat one = GlVertexFormat.builder(8).addElement("p", 0, GlVertexAttributeFormat.FLOAT, 2, false, false).build();
        GlVertexFormat two = GlVertexFormat.builder(8)
                .addElement("p", 0, GlVertexAttributeFormat.FLOAT, 1, false, false)
                .addElement("q", 4, GlVertexAttributeFormat.FLOAT, 1, false, false)
                .build();
        assertThrows(IllegalArgumentException.class, () -> TessellationBinding.forVertexBuffer(buffer, one, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> TessellationBinding.forVertexBuffer(buffer, one, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> TessellationBinding.forVertexBuffer(buffer, one, Integer.MIN_VALUE, 0));
        assertThrows(IllegalArgumentException.class, () -> TessellationBinding.forVertexBuffer(buffer, two, Integer.MAX_VALUE, 0));
        TessellationBinding top = TessellationBinding.forVertexBuffer(buffer, one, Integer.MAX_VALUE, Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, top.attributeBindings()[0].getIndex());
        assertEquals(Integer.MAX_VALUE, top.attributeBindings()[0].getDivisor());
        TessellationBinding pair = TessellationBinding.forVertexBuffer(buffer, two, Integer.MAX_VALUE - 1, 0);
        assertEquals(Integer.MAX_VALUE - 1, pair.attributeBindings()[0].getIndex());
        assertEquals(Integer.MAX_VALUE, pair.attributeBindings()[1].getIndex());
    }

    // Delegates bind and unbind and delete to the command list each call.
    @Test
    void bindUnbindDeleteDelegate() {
        CommandList list = Mockito.mock(CommandList.class);
        GlVertexArray array = new GlVertexArray();
        GlVertexArrayTessellation tessellation = new GlVertexArrayTessellation(array, new TessellationBinding[0]);
        tessellation.bind(list);
        tessellation.bind(list);
        tessellation.unbind(list);
        tessellation.delete(list);
        Mockito.verify(list, Mockito.times(2)).bindVertexArray(array);
        Mockito.verify(list).unbindVertexArray();
        Mockito.verify(list).deleteVertexArray(array);
    }
}
